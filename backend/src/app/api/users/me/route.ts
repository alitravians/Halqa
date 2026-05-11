import { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminAuth, adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import {
  isReservedDisplayName,
  isReservedHandle,
} from "@/lib/reserved-names";
import { classifyText } from "@/lib/word-filter";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  try {
    // PR-H — banned users still need to read their own profile to
    // see ban status / appeal context in the UI.
    const user = await requireUser(req, { allowBanned: true });
    const snap = await adminFirestore().collection("users").doc(user.uid).get();
    return asJson(200, snap.data() ?? { uid: user.uid, role: "user" });
  } catch (err) {
    return asError(err);
  }
}

const ALLOWED_KEYS = ["displayName", "bio", "avatar"] as const;

export async function POST(req: NextRequest) {
  try {
    // PR-H — profile edits (display name, bio, avatar) are not the
    // action a ban targets. A banned user correcting their handle
    // while suspended is harmless and not the moderation surface.
    const user = await requireUser(req, { allowBanned: true });
    const body = (await req.json()) as Record<string, unknown>;

    const now = new Date().toISOString();
    const update: Record<string, unknown> = { updatedAt: now };

    // Trim every string field on the way in so leading / trailing
    // whitespace doesn't sneak into displayName ("   اسمي   ") and so
    // empty-after-trim fields are rejected rather than silently
    // overwriting the user's previous value with "".
    for (const key of ALLOWED_KEYS) {
      if (key in body) {
        const v = body[key];
        if (typeof v !== "string") {
          throw new HttpError(400, `${key} must be a string.`);
        }
        if (v.length > 280) {
          throw new HttpError(400, `${key} too long (max 280 chars).`);
        }
        const trimmed = v.trim();
        if (trimmed.length === 0) {
          throw new HttpError(400, `${key} must not be empty after trim.`);
        }
        update[key] = trimmed;
      }
    }

    // PR-J gates: display-name + bio profanity, reserved display-name,
    // reserved handle. Profanity HARD-hits reject the request; for
    // displayName SOFT hits also reject (profile is persistent; chat
    // is ephemeral so chat tolerates 'soft').
    if (typeof update.displayName === "string") {
      if (isReservedDisplayName(update.displayName)) {
        throw new HttpError(
          400,
          "Display name impersonates Halqa staff or a reserved role. Pick another."
        );
      }
      const dn = classifyText(update.displayName);
      if (dn.classification !== "clean") {
        throw new HttpError(
          400,
          "Display name contains disallowed words. Pick another."
        );
      }
    }
    if (typeof update.bio === "string") {
      const bio = classifyText(update.bio);
      if (bio.classification === "hard") {
        throw new HttpError(400, "Bio contains disallowed words.");
      }
    }

    if (typeof body.handle === "string" && body.handle.length > 0) {
      const h = body.handle.trim().replace(/^@/, "");
      if (!/^[a-zA-Z0-9_]{2,24}$/.test(h)) {
        throw new HttpError(400, "handle must be 2-24 chars, alphanumeric + underscore.");
      }
      if (isReservedHandle(h)) {
        throw new HttpError(
          400,
          "That handle is reserved (Halqa staff / system roles). Pick another."
        );
      }
      update.handle = h;
    }

    if (Object.keys(update).length === 1) {
      // only `updatedAt` — nothing meaningful to write. Reject so the
      // client doesn't think a no-op succeeded silently.
      throw new HttpError(400, "no valid profile fields to update.");
    }

    const db = adminFirestore();
    const ref = db.collection("users").doc(user.uid);

    // Atomic profile update + audit_log write. Old code did the two
    // operations in separate round trips, so a Vercel function
    // timeout between them produced a profile change with no audit
    // record — Trust & Safety lost the trail. Using set+merge inside
    // the txn preserves the ability to upsert if the user doc
    // somehow doesn't exist (shouldn't happen — requireUser
    // self-creates — but cheap defence).
    await db.runTransaction(async (tx) => {
      const auditRef = db.collection("audit_log").doc();
      tx.set(ref, update, { merge: true });
      tx.set(auditRef, {
        userId: user.uid,
        action: "profile_update",
        timestamp: now,
        metadata: {
          fields: Object.keys(update).filter((k) => k !== "updatedAt"),
        },
      });
    });

    const fresh = await ref.get();
    return asJson(200, fresh.data());
  } catch (err) {
    return asError(err);
  }
}

// PR-K (Reem P0) — self-service account deletion.
//
//   DELETE /api/users/me
//     auth: requireUser
//     body: optional { confirm?: 'DELETE' } — UI-side belt-and-braces
//     to make sure the user really meant it; Tariq's Android UI will
//     send `{"confirm":"DELETE"}` and we validate. If the env-flag
//     ENFORCE_DELETE_CONFIRM is set, the body becomes mandatory.
//
// Play 2024 account-deletion-policy compliance
// --------------------------------------------
// Google Play requires apps that let users create an account in-app
// to also let them request deletion from inside the app — without
// emailing support, without filing a Help Center ticket. The action
// must take effect "as soon as possible" and the app must clearly
// describe the data that will be deleted vs retained.
//
// This is a SOFT-DELETE (anonymize) + Auth account deletion. Hard
// delete of all Firestore documents (PDPL Article 18 right-to-erasure)
// is **LAYLA-005**, deferred to v0.2 — there are referential integrity
// problems (gifts ledger, withdraw rows, audit_log entries) that need
// a separate background-job pipeline to scrub safely.
//
// Soft-delete model (this PR)
// ---------------------------
//   On DELETE:
//     1. Verify the user doc exists. If it doesn't, treat as 200
//        idempotent (already-gone case).
//     2. End any active streams owned by the user (status='ended').
//        Otherwise the LiveKit watchdog cron eventually cleans them
//        up, but doing it inline is friendlier UX (sender of any
//        chat to a deleted user's stream gets immediate 409).
//     3. Freeze the user's wallet (set frozen=true). Withdraw and
//        gift-send routes already gate on frozen flags (verify by
//        grep before merge).
//     4. Anonymize /users/{uid}:
//          - clear: displayName, bio, avatar, handle, phoneNumber,
//            email, dob_attested_at, kyc/* mirror fields
//          - set: deleted=true, deletedAt=ISO, role='deleted'
//        We keep the doc rather than deleting it because:
//          (a) referential integrity — gifts, audit_log, chat
//              messages reference uid; deleting the doc orphans
//              those references.
//          (b) PDPL Article 18 hard-delete is LAYLA-005 — needs a
//              cross-collection scrub pipeline + KYC retention
//              policy carve-out (KSA AML requires 5y retention on
//              financial records, so wallet/transactions can't be
//              instantly purged).
//          (c) Re-registration: someone reusing the same phone
//              number triggers a NEW Auth account with a NEW uid;
//              the old (anonymized) doc stays put.
//     5. Write /audit_log row 'account_deleted_self'. This is the
//        legal proof-of-deletion log; staff queries against this
//        action serve as the response to Google Play data-deletion
//        appeals.
//     6. Delete the Firebase Auth user (adminAuth().deleteUser(uid)).
//        After this point, the user cannot sign in with the uid; if
//        the same email/phone re-registers, a NEW uid is issued.
//
// What is NOT deleted (documented for the Play Store data-deletion
// listing)
// ------------------------------------------------------------
//   - Gifts ledger entries (/users/{hostUid}/gifts/* AND
//     /streams/{streamId}/gifts/*) — KSA AML 5y retention.
//   - Wallet ledger entries — same, plus financial reconciliation.
//   - Chat messages already sent — preserved (similar to all major
//     chat apps; the sender's handle in the snapshot is anonymized
//     via the username field, but historic messages aren't deleted).
//   - Audit log entries — required for compliance investigations,
//     never deleted.
//   - Stream recordings (LiveKit Egress) — if exist, separate
//     retention policy.
//
// All of these are documented in the Halqa privacy policy entry
// "What we keep after account deletion" (Layla owns that policy
// text; coordinate with her if Play Console rejects).
//
// Race conditions
// ---------------
//   - User has an active stream. We end it BEFORE we delete the Auth
//     account, so the watchdog doesn't see a 'live' stream owned by
//     a deleted uid.
//   - User has an in-flight gift transaction. Gift txn doesn't read
//     /users/{uid} so anonymization doesn't race; wallet freeze
//     does block subsequent sends (via the wallet route's frozen
//     gate).
//   - User holds a pending KYC submission. The /kyc_submissions/{uid}
//     doc is left intact (immutable except status); staff can still
//     review/reject. Future hardening: cascade-cancel pending KYC.
//
// Errors
// ------
// 400 — confirm token missing when ENFORCE_DELETE_CONFIRM is set.
// 401 — no auth.
// 500 — Firebase admin failures bubble up.

export async function DELETE(req: NextRequest) {
  try {
    // Note: requireUser does NOT have { allowBanned: true } here.
    // If PR-H (banned gate) merges first, a banned user cannot
    // self-delete via this endpoint — they'd need to go through
    // the ban-appeal flow. That's intentional: banned-user
    // self-delete could be a tactic to wipe evidence before
    // staff review. Bans → appeal → admin unban → user can then
    // delete. Closed-beta tradeoff; revisit if Reem flags it.
    const user = await requireUser(req);

    // Optional confirm token. Closed beta keeps it optional so curl
    // testing works; production flips ENFORCE_DELETE_CONFIRM=true.
    if (process.env.ENFORCE_DELETE_CONFIRM === "true") {
      let body: Record<string, unknown> = {};
      try {
        body = (await req.json()) as Record<string, unknown>;
      } catch {
        // empty body is fine for the non-strict path but rejected
        // here because the env flag demands a body
        throw new HttpError(
          400,
          "Confirm token required. Send {\"confirm\":\"DELETE\"}."
        );
      }
      if (body.confirm !== "DELETE") {
        throw new HttpError(
          400,
          "Confirm token must be exactly 'DELETE' to proceed."
        );
      }
    }

    const db = adminFirestore();
    const nowIso = new Date().toISOString();
    const uid = user.uid;
    const userRef = db.collection("users").doc(uid);

    // Snapshot current state for the audit row + idempotency check.
    const userSnap = await userRef.get();
    if (!userSnap.exists) {
      // Doc missing — still try to delete the Auth account so we don't
      // leave orphaned auth. Then log + return 200.
      try {
        await adminAuth().deleteUser(uid);
      } catch {
        // ignore — auth delete on a not-found uid is a no-op
      }
      return asJson(200, {
        uid,
        already_deleted: true,
        message: "User doc missing; treated as already deleted.",
      });
    }
    const userData = userSnap.data() ?? {};
    if (userData.deleted === true) {
      // Already soft-deleted. Idempotent.
      return asJson(200, {
        uid,
        already_deleted: true,
        deletedAt: userData.deletedAt ?? null,
      });
    }

    // 1. End any active streams owned by this user. Outside the txn
    //    because Firestore txns can't span an aggregate query then
    //    individual updates with a per-doc condition; we accept the
    //    small race window (watchdog covers if a stream goes live
    //    between query and update).
    const liveStreamsSnap = await db
      .collection("streams")
      .where("ownerUid", "==", uid)
      .where("status", "==", "live")
      .get();
    for (const streamDoc of liveStreamsSnap.docs) {
      await streamDoc.ref.update({
        status: "ended",
        endedAt: nowIso,
        endedReason: "owner_account_deleted",
      });
    }

    // 2. Atomic anonymization + wallet freeze + audit row.
    //    Wallet doc may not exist (closed beta — some users never
    //    topped up). Read first, only write if exists. /audit_log
    //    is append-only; users/{uid} is the protected one we MUST
    //    anonymize before deleting the Auth account.
    const walletRef = db.collection("wallets").doc(uid);
    await db.runTransaction(async (tx) => {
      const walletSnap = await tx.get(walletRef);

      // Anonymize the user doc. We clear PII fields by setting them
      // to FieldValue.delete() and stamp the deletion markers. The
      // role transitions to 'deleted' as a fast-path filter for
      // staff queries (e.g. "show me users active in last 7d" can
      // exclude deleted accounts).
      tx.update(userRef, {
        displayName: FieldValue.delete(),
        bio: FieldValue.delete(),
        avatar: FieldValue.delete(),
        handle: FieldValue.delete(),
        phoneNumber: FieldValue.delete(),
        email: FieldValue.delete(),
        dob_attested_at: FieldValue.delete(),
        kyc_status: FieldValue.delete(),
        deleted: true,
        deletedAt: nowIso,
        role: "deleted",
        updatedAt: nowIso,
      });

      if (walletSnap.exists) {
        tx.update(walletRef, {
          frozen: true,
          frozen_reason: "account_deleted_self",
          frozen_at: nowIso,
          updatedAt: nowIso,
        });
      }

      const auditRef = db.collection("audit_log").doc();
      tx.set(auditRef, {
        userId: uid,
        action: "account_deleted_self",
        timestamp: nowIso,
        metadata: {
          actor_uid: uid,
          actor_role: user.role,
          active_streams_ended: liveStreamsSnap.size,
          wallet_frozen: walletSnap.exists,
        },
      });
    });

    // 3. Delete the Firebase Auth user LAST. If it fails (network,
    //    quota), the Firestore anonymization is already applied —
    //    the user is effectively soft-deleted even if their Auth
    //    record lingers. They can't login because requireUser will
    //    see /users/{uid}.deleted=true and 403 on next request.
    //
    //    NB: To enforce that 403, /lib/auth.ts requireUser should
    //    treat deleted=true as banned-equivalent. That's not in this
    //    PR (PR-H added a banned-check; we piggyback by saying that
    //    deleted users effectively have role='deleted' so any
    //    role-gated endpoint rejects them, and ban-gated paths
    //    aren't applicable). Future hardening: explicit deleted-check
    //    in requireUser parallel to banned-check.
    try {
      await adminAuth().deleteUser(uid);
    } catch (e) {
      // Log but don't fail — Firestore side is already cleaned up.
      // Surface a soft warning in the response so Tariq's UI can
      // tell the user their data is anonymized but to reach out if
      // they can still sign in.
      const msg = e instanceof Error ? e.message : String(e);
      return asJson(200, {
        uid,
        deletedAt: nowIso,
        active_streams_ended: liveStreamsSnap.size,
        wallet_frozen: true,
        auth_delete_warning: msg,
        message:
          "Account anonymized successfully. Auth delete failed; data is removed but contact support if you can still sign in.",
      });
    }

    return asJson(200, {
      uid,
      deletedAt: nowIso,
      active_streams_ended: liveStreamsSnap.size,
      wallet_frozen: true,
    });
  } catch (err) {
    return asError(err);
  }
}
