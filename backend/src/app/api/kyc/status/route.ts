import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  try {
    // PR-H — banned user reading their own KYC status (sibling of
    // the appeal-path kyc/submit). Same allowlist.
    const user = await requireUser(req, { allowBanned: true });
    const snap = await adminFirestore().collection("kyc_submissions").doc(user.uid).get();
    if (!snap.exists) {
      return asJson(200, { status: "none" });
    }
    const data = snap.data() ?? {};
    return asJson(200, {
      status: data.status ?? "pending",
      submittedAt: data.submittedAt ?? null,
      approvedAt: data.approvedAt ?? null,
      reason: data.reason ?? null,
      identityType: data.identityType ?? null,
    });
  } catch (err) {
    return asError(err);
  }
}
