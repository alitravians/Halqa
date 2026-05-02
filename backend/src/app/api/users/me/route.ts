import { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";

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

const ALLOWED = ["displayName", "handle", "bio", "avatar"] as const;
type AllowedKey = (typeof ALLOWED)[number];

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const body = (await req.json()) as Record<string, unknown>;

    const update: Record<string, unknown> = { updatedAt: new Date().toISOString() };
    for (const key of ALLOWED) {
      if (key in body) {
        const v = body[key];
        if (typeof v !== "string") {
          throw new HttpError(400, `${key} must be a string.`);
        }
        if (v.length > 280) {
          throw new HttpError(400, `${key} too long (max 280 chars).`);
        }
        update[key] = v;
      }
    }

    if (typeof body.handle === "string" && body.handle.length > 0) {
      const h = body.handle.trim().replace(/^@/, "");
      if (!/^[a-zA-Z0-9_]{2,24}$/.test(h)) {
        throw new HttpError(400, "handle must be 2-24 chars, alphanumeric + underscore.");
      }
      update.handle = h;
    }

    const ref = adminFirestore().collection("users").doc(user.uid);
    await ref.set(update, { merge: true });

    await adminFirestore().collection("audit_log").add({
      userId: user.uid,
      action: "profile_update",
      timestamp: new Date().toISOString(),
      metadata: { fields: Object.keys(update).filter((k) => k !== "updatedAt") },
    });

    const fresh = await ref.get();
    return asJson(200, fresh.data());
  } catch (err) {
    return asError(err);
  }
}
