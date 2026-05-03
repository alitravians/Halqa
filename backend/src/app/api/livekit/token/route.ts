import { NextRequest } from "next/server";
import { AccessToken } from "livekit-server-sdk";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import { assertProdSafe, evaluateKycBeta } from "@/lib/kyc-allowlist";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface TokenBody {
  roomName: string;
  /** "publisher" lets the user broadcast; "viewer" subscribes only. */
  role: "publisher" | "viewer";
  /** Optional title for stream metadata when starting a publisher session. */
  streamTitle?: string;
}

const ROOM_NAME_RE = /^[A-Za-z0-9_-]{4,64}$/;

export async function POST(req: NextRequest) {
  try {
    // Refuse to issue any tokens if a deploy left the unsafe combo
    // (live monetization + global KYC bypass) on. Better to 500 the
    // whole route than to silently let strangers broadcast in
    // production.
    assertProdSafe();
    const user = await requireUser(req);
    const body = (await req.json()) as Partial<TokenBody>;

    if (!body.roomName || typeof body.roomName !== "string") {
      throw new HttpError(400, "roomName is required.");
    }
    if (!ROOM_NAME_RE.test(body.roomName)) {
      throw new HttpError(
        400,
        "roomName must be 4-64 chars, alphanumeric / underscore / dash only."
      );
    }
    const role = body.role === "publisher" ? "publisher" : "viewer";

    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    const wsUrl = process.env.LIVEKIT_URL;
    if (!apiKey || !apiSecret || !wsUrl) {
      throw new HttpError(
        500,
        "LiveKit env vars are not configured (LIVEKIT_API_KEY/SECRET/URL)."
      );
    }

    const db = adminFirestore();

    // P0 — gate every request, even viewers, against an active ban.
    const banSnap = await db
      .collection("bans")
      .where("userId", "==", user.uid)
      .where("active", "==", true)
      .limit(1)
      .get();
    if (!banSnap.empty) {
      throw new HttpError(403, "Account is banned.");
    }

    if (role === "publisher") {
      // P0 — only KYC-approved users may broadcast.
      // Beta gating is now allowlist-based (see lib/kyc-allowlist.ts):
      //   - STATIC_ALLOWLIST (founding team)
      //   - KYC_BETA_ALLOWLIST env CSV (operator-managed testers)
      //   - legacy global BYPASS_KYC_FOR_BETA flag (logs a warning on use)
      // Anyone outside the allowlist still needs an approved KYC submission.
      const beta = evaluateKycBeta(user.uid);
      if (!beta.allowed) {
        const kycSnap = await db
          .collection("kyc_submissions")
          .doc(user.uid)
          .get();
        const kycStatus = kycSnap.exists
          ? (kycSnap.data()?.status as string | undefined)
          : undefined;
        if (kycStatus !== "approved") {
          throw new HttpError(
            403,
            "Publisher access requires approved KYC. Submit KYC and wait for review."
          );
        }
      }

      // P0 — room name MUST belong to this user. Reject squatting/impersonation.
      // Convention: roomName starts with `u_<uid>_` for user-owned streams.
      if (!body.roomName.startsWith(`u_${user.uid}_`)) {
        throw new HttpError(
          403,
          `roomName must be prefixed with u_${user.uid}_ for publisher tokens.`
        );
      }

      // P0 — atomic create-if-absent so two devices can't open two `live` docs.
      //
      // Three cases for an existing doc with this roomName:
      //
      //   (a) ownerUid != caller.uid → room-name squatting / impersonation.
      //   (b) ownerUid == caller.uid && status == "live" → legitimate resume
      //       of a *still-live* stream. The Android client generates a new
      //       streamId per session (`u_{uid}_{ts}` in BroadcastSession.start),
      //       so this realistically only happens when LiveKit briefly
      //       disconnects and the broadcaster reconnects within the
      //       room-empty timeout (~5min) — the stream doc was never marked
      //       ended, just hand them a fresh AccessToken and move on.
      //   (c) ownerUid == caller.uid && status != "live" → the stream was
      //       deliberately ended (by the owner via /api/streams/end, by a
      //       moderator force-ending it, or by the LiveKit room_finished
      //       webhook). Reusing the same streamId here is dangerous on
      //       three counts:
      //         1. Moderation bypass — a moderator force-ends a banned
      //            stream, the broadcaster immediately POSTs /livekit/token
      //            with the same roomName, status flips back to "live",
      //            the moderator's action is silently undone with no
      //            audit trail.
      //         2. Audit hole — there's no `stream_start` audit_log entry
      //            for the resume, so Trust & Safety can't reconstruct
      //            "the user broadcasted again at time T after being
      //            ended at time T-30s".
      //         3. Analytics drift — the resume overwrites `startTime`,
      //            losing the original session length.
      //       The radical fix is to refuse the resume entirely. Clients
      //       that legitimately want to broadcast again must call
      //       /livekit/token with a fresh streamId (which the Android
      //       client already generates on every BroadcastSession.start
      //       via `System.currentTimeMillis()`).
      await db.runTransaction(async (tx) => {
        const ref = db.collection("streams").doc(body.roomName!);
        const existing = await tx.get(ref);
        if (existing.exists) {
          const data = existing.data()!;
          if (data.ownerUid !== user.uid) {
            throw new HttpError(409, "Room name already taken by another user.");
          }
          if (data.status !== "live") {
            throw new HttpError(
              409,
              "This stream has already ended. Start a new broadcast."
            );
          }
          return;
        }
        const now = new Date().toISOString();
        tx.set(ref, {
          streamId: body.roomName,
          ownerUid: user.uid,
          title: body.streamTitle?.trim() || "بث جديد",
          status: "live",
          startTime: now,
          endTime: null,
          viewerCount: 0,
          roomName: body.roomName,
          createdAt: now,
        });
        const auditRef = db.collection("audit_log").doc();
        tx.set(auditRef, {
          userId: user.uid,
          action: "stream_start",
          timestamp: now,
          metadata: {
            streamId: body.roomName,
            title: body.streamTitle ?? null,
          },
        });
      });
    } else {
      // Viewer — confirm the room exists and is live before issuing a token.
      const streamSnap = await db.collection("streams").doc(body.roomName).get();
      if (!streamSnap.exists) {
        throw new HttpError(404, "Stream not found.");
      }
      const data = streamSnap.data()!;
      if (data.status !== "live") {
        throw new HttpError(410, "Stream is not currently live.");
      }
    }

    // PDPL-critical: the LiveKit `name` field is broadcast to every
    // other participant in the room as part of the participant
    // metadata. We MUST NOT put `email` or `phoneNumber` here — those
    // are PII and Halqa's privacy policy promises they never leave the
    // backend. The previous fallback chain (`email || phoneNumber || uid`)
    // exposed every host's email/phone to every viewer of their stream
    // (and every viewer's email/phone to every other viewer).
    //
    // Use the public profile fields only:
    //   1. displayName — what the user typed in their profile
    //   2. handle      — public @-name fallback
    //   3. uid         — opaque pseudonymous string, last resort so
    //                    LiveKit always has a non-empty value
    const at = new AccessToken(apiKey, apiSecret, {
      identity: user.uid,
      name: user.displayName || user.handle || user.uid,
      ttl: 60 * 60, // 1 hour
    });
    at.addGrant({
      room: body.roomName,
      roomJoin: true,
      canPublish: role === "publisher",
      canSubscribe: true,
      canPublishData: true,
    });
    const token = await at.toJwt();

    return asJson(200, { token, url: wsUrl });
  } catch (err) {
    return asError(err);
  }
}
