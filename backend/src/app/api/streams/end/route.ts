import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, isModerator, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface EndBody {
  streamId: string;
}

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const body = (await req.json()) as Partial<EndBody>;
    if (!body.streamId) throw new HttpError(400, "streamId is required.");

    const db = adminFirestore();
    const ref = db.collection("streams").doc(body.streamId);
    const snap = await ref.get();
    if (!snap.exists) throw new HttpError(404, "Stream not found.");
    const data = snap.data()!;
    if (data.ownerUid !== user.uid && !isModerator(user)) {
      throw new HttpError(403, "Only owner or moderator can end the stream.");
    }
    const now = new Date().toISOString();
    await ref.set({ status: "ended", endTime: now }, { merge: true });
    await db.collection("audit_log").add({
      userId: user.uid,
      action: "stream_end",
      timestamp: now,
      metadata: { streamId: body.streamId, endedBy: user.uid === data.ownerUid ? "owner" : "moderator" },
    });
    return asJson(200, { ok: true });
  } catch (err) {
    return asError(err);
  }
}
