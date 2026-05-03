import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * GET /api/streams/live
 *
 * Returns the most recently started live streams (newest first), capped
 * at 50. The sort order MUST be explicit: without an `orderBy` clause
 * Firestore falls back to `__name__` (document id) ordering, which
 * sorts by `u_<uid>_<timestamp>` lexicographically and is not the
 * "newest first" feed clients expect — when there are >50 live streams
 * the cap silently drops newer ones in favour of older streams whose
 * room names happen to sort earlier.
 *
 * The composite index (status ASC, startTime DESC) is already
 * provisioned in `firebase/firestore.indexes.json` for this query.
 */
export async function GET(req: NextRequest) {
  try {
    await requireUser(req);
    const snap = await adminFirestore()
      .collection("streams")
      .where("status", "==", "live")
      .orderBy("startTime", "desc")
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
