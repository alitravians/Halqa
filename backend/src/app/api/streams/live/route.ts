import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  try {
    await requireUser(req);
    const snap = await adminFirestore()
      .collection("streams")
      .where("status", "==", "live")
      .limit(50)
      .get();

    const out = snap.docs.map((doc) => {
      const d = doc.data();
      return {
        streamId: d.streamId,
        ownerUid: d.ownerUid,
        title: d.title,
        startTime: d.startTime,
        viewerCount: d.viewerCount ?? 0,
        roomName: d.roomName,
      };
    });
    return asJson(200, { streams: out });
  } catch (err) {
    return asError(err);
  }
}
