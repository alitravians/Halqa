import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, isModerator, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface EndBody {
  streamId: string;
}

/**
 * Mirrors the room-name shape enforced in /api/livekit/token. Reapply
 * here so we never resolve `streams/<arbitrary>` and so a forged
 * payload like `streamId: "../../meta/health"` (Firestore segments
 * with `/` are rejected by the SDK, but defence-in-depth is cheap)
 * cannot reach Firestore in the first place.
 */
const STREAM_ID_RE = /^[A-Za-z0-9_-]{4,64}$/;

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const body = (await req.json()) as Partial<EndBody>;
    if (!body.streamId) throw new HttpError(400, "streamId is required.");
    if (typeof body.streamId !== "string" || !STREAM_ID_RE.test(body.streamId)) {
      throw new HttpError(400, "streamId is malformed.");
    }

    const db = adminFirestore();
    const ref = db.collection("streams").doc(body.streamId);

    // Single transaction:
    //   1. Authoritatively read the doc inside the txn snapshot.
    //   2. Authorize (owner OR moderator).
    //   3. If already ended, return idempotently — do NOT overwrite
    //      the existing endTime and do NOT append a duplicate
    //      audit_log entry. The previous code did both, so two
    //      simultaneous "end stream" presses (broadcaster taps end
    //      while a moderator force-ends) produced two audit events
    //      with subtly different endTime values, and Trust & Safety
    //      had no way to tell which one was the canonical close.
    //   4. Flip status + write audit_log atomically. Either both
    //      land or neither does; we cannot leave a stream marked
    //      `ended` with no audit trail (the previous code wrote the
    //      status with set+merge first and the audit log second, so
    //      a Vercel function timeout between the two would orphan
    //      the audit row forever).
    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) {
        throw new HttpError(404, "Stream not found.");
      }
      const data = snap.data() ?? {};
      const ownerUid = String(data.ownerUid ?? "");
      const isOwner = ownerUid === user.uid;
      if (!isOwner && !isModerator(user)) {
        throw new HttpError(403, "Only owner or moderator can end the stream.");
      }

      if (data.status === "ended") {
        // Idempotent — return the existing endTime so the caller
        // can render it without making another round trip.
        return {
          idempotent: true,
          endTime: typeof data.endTime === "string" ? data.endTime : null,
        };
      }

      const now = new Date().toISOString();
      tx.update(ref, { status: "ended", endTime: now });
      const auditRef = db.collection("audit_log").doc();
      tx.set(auditRef, {
        userId: user.uid,
        action: "stream_end",
        timestamp: now,
        metadata: {
          streamId: body.streamId,
          endedBy: isOwner ? "owner" : "moderator",
        },
      });

      return { idempotent: false, endTime: now };
    });

    return asJson(200, { ok: true, ...result });
  } catch (err) {
    return asError(err);
  }
}
