import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface KycBody {
  identityType: "national_id" | "passport" | "iqama";
  /**
   * 0–3 image references (data URLs or upload IDs). Capped at 256 KB each.
   *
   * The Android client currently sends `[]` because image upload through
   * Firebase Storage hasn't shipped yet — the KYC screen explicitly tells
   * the user "the review team will request the images after receiving the
   * application" (see `KycScreen.kt:217`). Requiring at least one image
   * here previously made every Android submission fail with HTTP 400, which
   * silently broke the entire KYC funnel: no gift-spend gating,
   * broadcasting tier upgrades, or earnings withdrawals could complete
   * because none of them resolved past the gate.
   *
   * When the upload feature ships, tighten the lower bound (and add the
   * Firebase Storage rules to match) without breaking older clients still
   * sending `[]`.
   */
  images: string[];
  fullName: string;
  documentNumber: string;
}

const MAX_IMG_BYTES = 256 * 1024;
const MAX_IMG_COUNT = 3;

/**
 * Hard caps on the user-controlled string fields, so a curl/Postman
 * caller cannot push the submitted KYC doc toward Firestore's 1MB
 * hard limit. The Android UI already caps fullName at 80 chars and
 * documentNumber at 24 chars (KycScreen.kt:211/218), so legitimate
 * clients are well under these numbers; the server-side caps exist
 * purely to defeat a tampered/non-Halqa client. Same class as the
 * streamTitle cap in /api/livekit/token (PR #54). 120 / 64 are picked
 * to be twice the Android caps so future UI tweaks don't trip us
 * without a coordinated change.
 */
const MAX_FULL_NAME = 120;
const MAX_DOC_NUMBER = 64;

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const body = (await req.json()) as Partial<KycBody>;

    if (!body.identityType || !["national_id", "passport", "iqama"].includes(body.identityType)) {
      throw new HttpError(400, "identityType is required: national_id | passport | iqama.");
    }
    if (!body.fullName || typeof body.fullName !== "string") {
      throw new HttpError(400, "fullName is required.");
    }
    // Reject before trim. A 10MB-of-spaces payload would have its
    // length check pass after trim (length 0 < 3 → fails) but we'd
    // still pay the round-trip + the trim() over a giant string.
    if (body.fullName.length > MAX_FULL_NAME) {
      throw new HttpError(400, `fullName too long (max ${MAX_FULL_NAME} chars).`);
    }
    if (body.fullName.trim().length < 3) {
      throw new HttpError(400, "fullName must be at least 3 characters.");
    }
    if (!body.documentNumber || typeof body.documentNumber !== "string") {
      throw new HttpError(400, "documentNumber is required.");
    }
    if (body.documentNumber.length > MAX_DOC_NUMBER) {
      throw new HttpError(400, `documentNumber too long (max ${MAX_DOC_NUMBER} chars).`);
    }
    if (body.documentNumber.trim().length < 4) {
      throw new HttpError(400, "documentNumber required.");
    }
    const images: string[] = Array.isArray(body.images) ? body.images : [];
    if (images.length > MAX_IMG_COUNT) {
      throw new HttpError(400, `images must contain at most ${MAX_IMG_COUNT} entries.`);
    }
    for (const img of images) {
      if (typeof img !== "string") throw new HttpError(400, "image must be string.");
      if (img.length > MAX_IMG_BYTES) {
        throw new HttpError(400, `image too large (>${MAX_IMG_BYTES} bytes).`);
      }
    }

    const db = adminFirestore();
    const ref = db.collection("kyc_submissions").doc(user.uid);
    const now = new Date().toISOString();

    // Atomic check-and-write. The previous code did a non-txn read,
    // then a separate `set` + `audit_log.add`, opening THREE distinct
    // race windows:
    //   1. The "approved" guard could be bypassed by a second submit
    //      that landed between the first submit's read and write.
    //   2. The status flip + audit_log write were not atomic, so a
    //      timeout between them left an unaudited KYC submission.
    //   3. set({merge:false}) blew away every existing field without
    //      reading the current state first; an in-flight reviewer
    //      decision could be erased.
    // Wrap the read + the write + the audit row in a single txn so
    // the snapshot used for the "approved" check is the same one the
    // commit replaces.
    await db.runTransaction(async (tx) => {
      const existing = await tx.get(ref);
      if (existing.exists && existing.data()?.status === "approved") {
        throw new HttpError(409, "KYC already approved.");
      }

      tx.set(ref, {
        uid: user.uid,
        status: "pending",
        identityType: body.identityType,
        fullName: body.fullName!.trim(),
        documentNumber: body.documentNumber!.trim(),
        images,
        submittedAt: now,
        approvedAt: null,
        reason: null,
      });

      const auditRef = db.collection("audit_log").doc();
      tx.set(auditRef, {
        userId: user.uid,
        action: "kyc_submit",
        timestamp: now,
        metadata: { identityType: body.identityType },
      });
    });

    return asJson(200, { status: "pending", submittedAt: now });
  } catch (err) {
    return asError(err);
  }
}
