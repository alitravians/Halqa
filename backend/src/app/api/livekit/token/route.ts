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
      await db.runTransaction(async (tx) => {
        const ref = db.collection("streams").doc(body.roomName!);
        const existing = await tx.get(ref);
        if (existing.exists) {
          const data = existing.data()!;
          if (data.ownerUid !== user.uid) {
            throw new HttpError(409, "Room name already taken by another user.");
          }
          if (data.status !== "live") {
            // Reuse for resume, mark live again.
            tx.set(
              ref,
              { status: "live", endTime: null, startTime: new Date().toISOString() },
              { merge: true }
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

    const at = new AccessToken(apiKey, apiSecret, {
      identity: user.uid,
      name: user.email || user.phoneNumber || user.uid,
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
