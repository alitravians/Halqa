import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, isStaff, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: NextRequest, ctx: { params: Promise<{ uid: string }> }) {
  try {
    const { uid: targetUid } = await ctx.params;
    const user = await requireUser(req);
    if (user.uid !== targetUid && !isStaff(user)) {
      throw new HttpError(403, "Not authorized to read this audit log.");
    }

    // Fetch user-scoped audit entries from /audit_log where userId == targetUid.
    const snap = await adminFirestore()
      .collection("audit_log")
      .where("userId", "==", targetUid)
      .orderBy("timestamp", "desc")
      .limit(100)
      .get();

    const entries = snap.docs.map((d) => ({
      id: d.id,
      action: d.data().action,
      timestamp: d.data().timestamp,
      metadata: d.data().metadata ?? {},
    }));

    return asJson(200, { entries });
  } catch (err) {
    return asError(err);
  }
}
