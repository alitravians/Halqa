import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface KycBody {
  identityType: "national_id" | "passport" | "iqama";
  /** Up to 3 image references (data URLs or upload IDs). Capped at 256 KB each. */
  images: string[];
  fullName: string;
  documentNumber: string;
}

const MAX_IMG_BYTES = 256 * 1024;

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const body = (await req.json()) as Partial<KycBody>;

    if (!body.identityType || !["national_id", "passport", "iqama"].includes(body.identityType)) {
      throw new HttpError(400, "identityType is required: national_id | passport | iqama.");
    }
    if (!body.fullName || typeof body.fullName !== "string" || body.fullName.length < 3) {
      throw new HttpError(400, "fullName must be at least 3 characters.");
    }
    if (!body.documentNumber || typeof body.documentNumber !== "string" || body.documentNumber.length < 4) {
      throw new HttpError(400, "documentNumber required.");
    }
    if (!Array.isArray(body.images) || body.images.length < 1 || body.images.length > 3) {
      throw new HttpError(400, "images must contain 1-3 entries.");
    }
    for (const img of body.images) {
      if (typeof img !== "string") throw new HttpError(400, "image must be string.");
      if (img.length > MAX_IMG_BYTES) {
        throw new HttpError(400, `image too large (>${MAX_IMG_BYTES} bytes).`);
      }
    }

    const db = adminFirestore();
    const ref = db.collection("kyc_submissions").doc(user.uid);
    const existing = await ref.get();
    if (existing.exists && existing.data()?.status === "approved") {
      throw new HttpError(409, "KYC already approved.");
    }

    const now = new Date().toISOString();
    await ref.set(
      {
        uid: user.uid,
        status: "pending",
        identityType: body.identityType,
        fullName: body.fullName.trim(),
        documentNumber: body.documentNumber.trim(),
        images: body.images,
        submittedAt: now,
        approvedAt: null,
        reason: null,
      },
      { merge: false }
    );

    await db.collection("audit_log").add({
      userId: user.uid,
      action: "kyc_submit",
      timestamp: now,
      metadata: { identityType: body.identityType },
    });

    return asJson(200, { status: "pending", submittedAt: now });
  } catch (err) {
    return asError(err);
  }
}
