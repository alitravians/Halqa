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

// PR-H (Mohammed Critical) — admin/moderator ban endpoint.
//
//   POST /api/admin/users/{uid}/ban
//     body: { reason: string (≤500 chars), durationDays?: number,
//             severity?: 'warn' | 'temp' | 'permanent' }
//
// Auth: requireUser + isModerator (moderator | staff | admin). The
// firestore rule on /users/{uid} still restricts direct-writes of
// `banned` to isAdmin() — Admin SDK in this route bypasses that, so
// moderators can ban via this API but not via the Firestore console.
// That asymmetry is intentional: the API path produces an audit_log
// row and writes /bans/, the console path produces neither, so we
// only want the console path open to the most-trusted role.
//
// Side effects (all inside one Firestore txn):
//
//   1. /users/{uid}: banned=true + ban_reason + banned_by +
//      banned_at + updatedAt. This is the FAST PATH for
//      requireUser — checked on every authed request without a
//      second Firestore round-trip.
//
//   2. /bans/{banId}: new row { userId, active:true, reason,
//      banned_by, banned_at, severity, expires_at? }. Keeps the
//      existing assertNotBanned() helper working (it scans /bans/
//      where active==true) and serves as the immutable audit
//      trail — one row per ban event, never updated in place.
//
//   3. /audit_log/{id}: action="user_banned" with metadata. Trust
//      & Safety chain of custody.
//
// Idempotency: if user is already banned (users/{uid}.banned ===
// true), return 200 with the existing state rather than 409 — staff
// double-clicking the ban button should be a no-op, not a hard
// error. The /bans/ row is NOT duplicated in that case (we check
// the user-doc fast path and short-circuit before writing).
//
// Self-ban: cannot ban yourself. Prevents a compromised mod account
// from locking out the legit owner and prevents accidental
// console-paste self-bans.

interface RouteContext {
  params: Promise<{ uid: string }>;
}

const VALID_SEVERITIES = ["warn", "temp", "permanent"] as const;
type Severity = (typeof VALID_SEVERITIES)[number];

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
    if (targetUid === actor.uid) {
      throw new HttpError(400, "Cannot ban yourself.");
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

    let severity: Severity = "permanent";
    if (body.severity !== undefined) {
      if (
        typeof body.severity !== "string" ||
        !VALID_SEVERITIES.includes(body.severity as Severity)
      ) {
        throw new HttpError(
          400,
          `severity must be one of: ${VALID_SEVERITIES.join(", ")}.`
        );
      }
      severity = body.severity as Severity;
    }

    let expiresAt: string | null = null;
    if (severity === "temp") {
      if (
        typeof body.durationDays !== "number" ||
        !Number.isFinite(body.durationDays) ||
        body.durationDays < 1 ||
        body.durationDays > 365
      ) {
        throw new HttpError(
          400,
          "durationDays must be a number between 1 and 365 for temp bans."
        );
      }
      const ms = Date.now() + body.durationDays * 24 * 60 * 60 * 1000;
      expiresAt = new Date(ms).toISOString();
    }

    const nowIso = new Date().toISOString();
    const db = adminFirestore();
    const userRef = db.collection("users").doc(targetUid);
    const banRef = db.collection("bans").doc();
    const auditRef = db.collection("audit_log").doc();

    // Restrict moderator severities to keep abuse blast radius
    // small: a moderator can only `warn` or `temp` ban. Permanent
    // bans require staff or admin. This is enforced in the route
    // (not in rules) because the rules already only see Admin SDK
    // writes via this route.
    if (severity === "permanent" && actor.role === "moderator") {
      throw new HttpError(
        403,
        "Permanent bans require staff or admin authorisation."
      );
    }

    const result = await db.runTransaction(async (tx) => {
      const userSnap = await tx.get(userRef);
      if (!userSnap.exists) {
        throw new HttpError(404, "Target user not found.");
      }
      const userData = userSnap.data() ?? {};
      if (userData.banned === true) {
        // Idempotent: already banned. Return current state without
        // re-writing /bans/ or audit_log. Two double-clicks shouldn't
        // produce two ban rows.
        return {
          alreadyBanned: true,
          uid: targetUid,
          banned: true,
          ban_reason: userData.ban_reason ?? null,
          banned_by: userData.banned_by ?? null,
          banned_at: userData.banned_at ?? null,
        };
      }

      tx.update(userRef, {
        banned: true,
        ban_reason: reason,
        banned_by: actor.uid,
        banned_at: nowIso,
        updatedAt: nowIso,
      });

      tx.set(banRef, {
        banId: banRef.id,
        userId: targetUid,
        active: true,
        reason,
        severity,
        banned_by: actor.uid,
        banned_at: nowIso,
        expires_at: expiresAt,
      });

      tx.set(auditRef, {
        userId: targetUid,
        action: "user_banned",
        timestamp: nowIso,
        metadata: {
          actor_uid: actor.uid,
          actor_role: actor.role,
          ban_id: banRef.id,
          severity,
          expires_at: expiresAt,
          reason_len: reason.length,
        },
      });

      return {
        alreadyBanned: false,
        uid: targetUid,
        banId: banRef.id,
        banned: true,
        ban_reason: reason,
        banned_by: actor.uid,
        banned_at: nowIso,
        severity,
        expires_at: expiresAt,
      };
    });

    return asJson(result.alreadyBanned ? 200 : 201, result);
  } catch (err) {
    return asError(err);
  }
}
