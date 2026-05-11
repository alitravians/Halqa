import type { NextRequest } from "next/server";
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

// PR-H — admin/moderator unban endpoint.
//
//   POST /api/admin/users/{uid}/unban
//     body: { reason: string (≤500 chars) }
//
// Side effects (all inside one Firestore txn):
//
//   1. /users/{uid}: banned=false. ban_reason / banned_by /
//      banned_at are PRESERVED on the user doc (so investigators
//      see the last ban event without consulting /bans/) but the
//      fast-path boolean flips false.
//
//   2. /bans/{banId}: ALL active rows for this user flipped to
//      active=false + unbanned_by + unbanned_at. We do not delete
//      the rows — the ban event is part of the user's permanent
//      moderation history.
//
//   3. /audit_log/{id}: action="user_unbanned" with metadata
//      referencing the previous /bans/ row(s) deactivated.
//
// Idempotency: if user is not currently banned, return 200 with the
// existing state. No /audit_log row is written for the no-op case.

interface RouteContext {
  params: Promise<{ uid: string }>;
}

export async function POST(req: NextRequest, ctx: RouteContext) {
  try {
    const actor = await requireUser(req);
    if (!isModerator(actor)) {
      throw new HttpError(403, "Moderator access required.");
    }
    const { uid: targetUid } = await ctx.params;
    if (!/^[a-zA-Z0-9_-]{1,128}$/.test(targetUid)) {
      throw new HttpError(400, "Invalid target uid.");
    }

    let body: Record<string, unknown>;
    try {
      body = (await req.json()) as Record<string, unknown>;
    } catch {
      throw new HttpError(400, "Body must be valid JSON.");
    }
    if (typeof body.reason !== "string") {
      throw new HttpError(400, "reason is required (string).");
    }
    const reason = body.reason.trim();
    if (reason.length === 0) {
      throw new HttpError(400, "reason must not be empty.");
    }
    if (reason.length > 500) {
      throw new HttpError(400, "reason too long (max 500 chars).");
    }

    const nowIso = new Date().toISOString();
    const db = adminFirestore();
    const userRef = db.collection("users").doc(targetUid);

    // Read the active bans OUTSIDE the txn first so we know which
    // doc IDs to lock inside the txn. Firestore txns require all
    // reads to use explicit doc refs (no queries inside txn). The
    // outer read picks up any new active bans that landed after
    // the outer read with optimistic-concurrency retries — the
    // inner read on each doc ref re-verifies the active flag.
    const activeBansSnap = await db
      .collection("bans")
      .where("userId", "==", targetUid)
      .where("active", "==", true)
      .get();

    const result = await db.runTransaction(async (tx) => {
      const userSnap = await tx.get(userRef);
      if (!userSnap.exists) {
        throw new HttpError(404, "Target user not found.");
      }
      const userData = userSnap.data() ?? {};
      if (userData.banned !== true && activeBansSnap.empty) {
        return {
          alreadyUnbanned: true,
          uid: targetUid,
          banned: false,
        };
      }

      // Re-read each /bans/ doc inside the txn so we lock them and
      // observe any concurrent ban events that landed between the
      // outer query and now. Filter out rows that have already
      // been deactivated since the outer read.
      const deactivatedBanIds: string[] = [];
      for (const banDoc of activeBansSnap.docs) {
        const fresh = await tx.get(banDoc.ref);
        if (!fresh.exists) continue;
        const bd = fresh.data() ?? {};
        if (bd.active !== true) continue;
        tx.update(banDoc.ref, {
          active: false,
          unbanned_by: actor.uid,
          unbanned_at: nowIso,
          unban_reason: reason,
        });
        deactivatedBanIds.push(banDoc.id);
      }

      tx.update(userRef, {
        banned: false,
        updatedAt: nowIso,
        // ban_reason / banned_by / banned_at preserved on purpose —
        // the user-doc retains the last-ban breadcrumb for staff.
      });

      const auditRef = db.collection("audit_log").doc();
      tx.set(auditRef, {
        userId: targetUid,
        action: "user_unbanned",
        timestamp: nowIso,
        metadata: {
          actor_uid: actor.uid,
          actor_role: actor.role,
          deactivated_ban_ids: deactivatedBanIds,
          reason_len: reason.length,
        },
      });

      return {
        alreadyUnbanned: false,
        uid: targetUid,
        banned: false,
        deactivated_ban_ids: deactivatedBanIds,
      };
    });

    return asJson(200, result);
  } catch (err) {
    return asError(err);
  }
}
