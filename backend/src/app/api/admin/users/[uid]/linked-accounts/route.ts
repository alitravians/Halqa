import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import {
  asError,
  asJson,
  HttpError,
  isModerator,
  requireUser,
} from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// PR-M (Yasser Y-H3) — moderator/admin endpoint to link two Auth
// accounts that belong to the same human (sister phone, second
// Google, post-KYC merge). The linkage is stored as a string-array
// `linked_accounts` on `/users/{uid}` and the relation is symmetric
// — every write touches BOTH sides atomically.
//
// Why this exists:
//   The trivial self-gift guard in `/api/gifts/send` only blocks
//   `ownerUid === sender.uid`. A user with a secondary Auth account
//   can top the secondary up via the closed-beta wallet grant and
//   send arbitrary gifts to the primary's stream to inflate the
//   `diamondTotal` leaderboard rank. The gift route consults the
//   sender's `linked_accounts` array inside its txn snapshot; if
//   the stream owner appears there, the gift is rejected with
//   `LINKED_ACCOUNT_SELF_GIFT`.
//
// Routes:
//
//   POST   /api/admin/users/{uid}/linked-accounts
//     body: { linkedUid: string }
//     Links `uid` <-> `linkedUid`. Idempotent — re-linking returns
//     200 with `alreadyLinked: true`.
//
//   DELETE /api/admin/users/{uid}/linked-accounts
//     body: { linkedUid: string }
//     Unlinks `uid` <-> `linkedUid`. Idempotent — already-unlinked
//     returns 200 with `notLinked: true`.
//
// Auth: requireUser + isModerator (moderator | staff | admin). The
// firestore rule on /users/{uid} only allows isAdmin() direct-writes
// of `linked_accounts` — Admin SDK in this route bypasses the rule,
// so moderators can manage links via this API but not via the
// Firestore console. Same asymmetry as the ban endpoints (PR-H).
//
// Atomicity:
//   - POST does a Firestore transaction that reads both user docs,
//     verifies they exist, and writes the symmetric arrayUnion on
//     both. Plus an audit_log row. All in one commit.
//   - DELETE mirrors with arrayRemove. Audit row written either
//     way (even on a no-op removal — moderators may run it
//     defensively).
//
// Audit_log shape (action="linked_accounts_added" / "_removed"):
//   {
//     userId: <uid>,                 // primary side
//     action,
//     timestamp,
//     metadata: {
//       actor_uid, actor_role,
//       linked_uid: <linkedUid>,
//       alreadyLinked? / notLinked?,
//     }
//   }

interface RouteContext {
  params: Promise<{ uid: string }>;
}

const UID_RE = /^[a-zA-Z0-9_-]{1,128}$/;

interface ParsedBody {
  linkedUid: string;
}

async function parseBody(req: NextRequest): Promise<ParsedBody> {
  let body: Record<string, unknown>;
  try {
    body = (await req.json()) as Record<string, unknown>;
  } catch {
    throw new HttpError(400, "Body must be valid JSON.");
  }
  if (typeof body.linkedUid !== "string") {
    throw new HttpError(400, "linkedUid is required (string).");
  }
  const linkedUid = body.linkedUid.trim();
  if (!UID_RE.test(linkedUid)) {
    throw new HttpError(400, "Invalid linkedUid.");
  }
  return { linkedUid };
}

export async function POST(req: NextRequest, ctx: RouteContext) {
  try {
    const actor = await requireUser(req);
    if (!isModerator(actor)) {
      throw new HttpError(403, "Moderator access required.");
    }

    const { uid: primaryUid } = await ctx.params;
    if (!UID_RE.test(primaryUid)) {
      throw new HttpError(400, "Invalid uid in path.");
    }
    const { linkedUid } = await parseBody(req);
    if (primaryUid === linkedUid) {
      throw new HttpError(400, "Cannot link a uid to itself.");
    }

    const nowIso = new Date().toISOString();
    const db = adminFirestore();
    const primaryRef = db.collection("users").doc(primaryUid);
    const secondaryRef = db.collection("users").doc(linkedUid);
    const auditRef = db.collection("audit_log").doc();

    const result = await db.runTransaction(async (tx) => {
      const [primarySnap, secondarySnap] = await Promise.all([
        tx.get(primaryRef),
        tx.get(secondaryRef),
      ]);
      if (!primarySnap.exists) {
        throw new HttpError(404, "Primary user not found.");
      }
      if (!secondarySnap.exists) {
        throw new HttpError(404, "Secondary user not found.");
      }
      const primaryData = primarySnap.data() ?? {};
      const primaryLinks: string[] = Array.isArray(primaryData.linked_accounts)
        ? primaryData.linked_accounts.filter(
            (v): v is string => typeof v === "string"
          )
        : [];
      const alreadyLinked = primaryLinks.includes(linkedUid);

      tx.update(primaryRef, {
        linked_accounts: FieldValue.arrayUnion(linkedUid),
        updatedAt: nowIso,
      });
      tx.update(secondaryRef, {
        linked_accounts: FieldValue.arrayUnion(primaryUid),
        updatedAt: nowIso,
      });
      tx.set(auditRef, {
        userId: primaryUid,
        action: "linked_accounts_added",
        timestamp: nowIso,
        metadata: {
          actor_uid: actor.uid,
          actor_role: actor.role,
          linked_uid: linkedUid,
          alreadyLinked,
        },
      });

      return {
        alreadyLinked,
        uid: primaryUid,
        linkedUid,
      };
    });

    return asJson(result.alreadyLinked ? 200 : 201, result);
  } catch (err) {
    return asError(err);
  }
}

export async function DELETE(req: NextRequest, ctx: RouteContext) {
  try {
    const actor = await requireUser(req);
    if (!isModerator(actor)) {
      throw new HttpError(403, "Moderator access required.");
    }

    const { uid: primaryUid } = await ctx.params;
    if (!UID_RE.test(primaryUid)) {
      throw new HttpError(400, "Invalid uid in path.");
    }
    const { linkedUid } = await parseBody(req);
    if (primaryUid === linkedUid) {
      throw new HttpError(400, "Cannot unlink a uid from itself.");
    }

    const nowIso = new Date().toISOString();
    const db = adminFirestore();
    const primaryRef = db.collection("users").doc(primaryUid);
    const secondaryRef = db.collection("users").doc(linkedUid);
    const auditRef = db.collection("audit_log").doc();

    const result = await db.runTransaction(async (tx) => {
      const [primarySnap, secondarySnap] = await Promise.all([
        tx.get(primaryRef),
        tx.get(secondaryRef),
      ]);
      if (!primarySnap.exists) {
        throw new HttpError(404, "Primary user not found.");
      }
      // We don't fail on a missing secondary doc here — an unlink
      // should still scrub the primary's array entry. But if it
      // does exist we mirror-remove for symmetry. Refusing to
      // unlink because the other side is gone would leave the
      // primary's array forever stale.
      const primaryData = primarySnap.data() ?? {};
      const primaryLinks: string[] = Array.isArray(primaryData.linked_accounts)
        ? primaryData.linked_accounts.filter(
            (v): v is string => typeof v === "string"
          )
        : [];
      const notLinked = !primaryLinks.includes(linkedUid);

      tx.update(primaryRef, {
        linked_accounts: FieldValue.arrayRemove(linkedUid),
        updatedAt: nowIso,
      });
      if (secondarySnap.exists) {
        tx.update(secondaryRef, {
          linked_accounts: FieldValue.arrayRemove(primaryUid),
          updatedAt: nowIso,
        });
      }
      tx.set(auditRef, {
        userId: primaryUid,
        action: "linked_accounts_removed",
        timestamp: nowIso,
        metadata: {
          actor_uid: actor.uid,
          actor_role: actor.role,
          linked_uid: linkedUid,
          notLinked,
          secondary_missing: !secondarySnap.exists,
        },
      });

      return {
        notLinked,
        uid: primaryUid,
        linkedUid,
      };
    });

    return asJson(200, result);
  } catch (err) {
    return asError(err);
  }
}
