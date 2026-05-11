import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import {
  isReservedDisplayName,
  isReservedHandle,
} from "@/lib/reserved-names";
import { classifyText } from "@/lib/word-filter";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const snap = await adminFirestore().collection("users").doc(user.uid).get();
    return asJson(200, snap.data() ?? { uid: user.uid, role: "user" });
  } catch (err) {
    return asError(err);
  }
}

const ALLOWED_KEYS = ["displayName", "bio", "avatar"] as const;

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const body = (await req.json()) as Record<string, unknown>;

    const now = new Date().toISOString();
    const update: Record<string, unknown> = { updatedAt: now };

    // Trim every string field on the way in so leading / trailing
    // whitespace doesn't sneak into displayName ("   اسمي   ") and so
    // empty-after-trim fields are rejected rather than silently
    // overwriting the user's previous value with "".
    for (const key of ALLOWED_KEYS) {
      if (key in body) {
        const v = body[key];
        if (typeof v !== "string") {
          throw new HttpError(400, `${key} must be a string.`);
        }
        if (v.length > 280) {
          throw new HttpError(400, `${key} too long (max 280 chars).`);
        }
        const trimmed = v.trim();
        if (trimmed.length === 0) {
          throw new HttpError(400, `${key} must not be empty after trim.`);
        }
        update[key] = trimmed;
      }
    }

    // PR-J gates: display-name + bio profanity, reserved display-name,
    // reserved handle. Profanity HARD-hits reject the request; for
    // displayName SOFT hits also reject (profile is persistent; chat
    // is ephemeral so chat tolerates 'soft').
    if (typeof update.displayName === "string") {
      if (isReservedDisplayName(update.displayName)) {
        throw new HttpError(
          400,
          "Display name impersonates Halqa staff or a reserved role. Pick another."
        );
      }
      const dn = classifyText(update.displayName);
      if (dn.classification !== "clean") {
        throw new HttpError(
          400,
          "Display name contains disallowed words. Pick another."
        );
      }
    }
    if (typeof update.bio === "string") {
      const bio = classifyText(update.bio);
      if (bio.classification === "hard") {
        throw new HttpError(400, "Bio contains disallowed words.");
      }
    }

    if (typeof body.handle === "string" && body.handle.length > 0) {
      const h = body.handle.trim().replace(/^@/, "");
      if (!/^[a-zA-Z0-9_]{2,24}$/.test(h)) {
        throw new HttpError(400, "handle must be 2-24 chars, alphanumeric + underscore.");
      }
      if (isReservedHandle(h)) {
        throw new HttpError(
          400,
          "That handle is reserved (Halqa staff / system roles). Pick another."
        );
      }
      update.handle = h;
    }

    if (Object.keys(update).length === 1) {
      // only `updatedAt` — nothing meaningful to write. Reject so the
      // client doesn't think a no-op succeeded silently.
      throw new HttpError(400, "no valid profile fields to update.");
    }

    const db = adminFirestore();
    const ref = db.collection("users").doc(user.uid);

    // Atomic profile update + audit_log write. Old code did the two
    // operations in separate round trips, so a Vercel function
    // timeout between them produced a profile change with no audit
    // record — Trust & Safety lost the trail. Using set+merge inside
    // the txn preserves the ability to upsert if the user doc
    // somehow doesn't exist (shouldn't happen — requireUser
    // self-creates — but cheap defence).
    await db.runTransaction(async (tx) => {
      const auditRef = db.collection("audit_log").doc();
      tx.set(ref, update, { merge: true });
      tx.set(auditRef, {
        userId: user.uid,
        action: "profile_update",
        timestamp: now,
        metadata: {
          fields: Object.keys(update).filter((k) => k !== "updatedAt"),
        },
      });
    });

    const fresh = await ref.get();
    return asJson(200, fresh.data());
  } catch (err) {
    return asError(err);
  }
}
