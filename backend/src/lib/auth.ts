import type { NextRequest } from "next/server";
import { adminAuth, adminFirestore } from "./firebase-admin";

export type UserRole = "user" | "scout" | "moderator" | "staff" | "admin";

export interface AuthedUser {
  uid: string;
  email: string | null;
  phoneNumber: string | null;
  /**
   * Display name from the user's Firestore profile. Safe to surface to
   * other participants in a stream (LiveKit room name, chat sender name,
   * etc.). Distinct from `email` / `phoneNumber`, which are PII and must
   * never leave the backend.
   */
  displayName: string | null;
  /** Public handle (e.g. "@aliali"). Safe to surface alongside displayName. */
  handle: string | null;
  role: UserRole;
}

const STAFF_ROLES: UserRole[] = ["staff", "admin"];
const MOD_ROLES: UserRole[] = ["moderator", "staff", "admin"];

function readBearerToken(req: NextRequest): string | null {
  const auth = req.headers.get("authorization") || req.headers.get("Authorization");
  if (!auth) return null;
  const m = auth.match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : null;
}

/** Verifies the Firebase ID token, loads role from Firestore. */
export async function requireUser(req: NextRequest): Promise<AuthedUser> {
  const token = readBearerToken(req);
  if (!token) {
    throw new HttpError(401, "Missing Authorization Bearer token.");
  }
  let decoded;
  try {
    decoded = await adminAuth().verifyIdToken(token);
  } catch (err) {
    throw new HttpError(401, `Invalid ID token: ${(err as Error).message}`);
  }

  const uid = decoded.uid;
  const userRef = adminFirestore().collection("users").doc(uid);
  const snap = await userRef.get();
  let role: UserRole = "user";
  let displayName: string | null = null;
  let handle: string | null = null;
  if (snap.exists) {
    const data = snap.data() ?? {};
    role = (data.role || "user") as UserRole;
    // Read PII-safe profile fields from the same snapshot we already
    // fetched for role. Coerced to non-empty string-or-null so callers
    // can do `displayName ?? handle ?? <fallback>` without worrying
    // about empty strings sneaking through as truthy.
    const dn = typeof data.displayName === "string" ? data.displayName.trim() : "";
    const hd = typeof data.handle === "string" ? data.handle.trim() : "";
    displayName = dn.length > 0 ? dn : null;
    handle = hd.length > 0 ? hd : null;
  } else {
    // Self-create on first call.
    const initialDisplayName = (decoded.name || "").trim();
    await userRef.set(
      {
        uid,
        email: decoded.email || null,
        phoneNumber: decoded.phone_number || null,
        displayName: initialDisplayName,
        handle: "",
        bio: "",
        avatar: "",
        role: "user",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
      { merge: true }
    );
    displayName = initialDisplayName.length > 0 ? initialDisplayName : null;
  }

  return {
    uid,
    email: decoded.email || null,
    phoneNumber: decoded.phone_number || null,
    displayName,
    handle,
    role,
  };
}

export function isStaff(user: AuthedUser): boolean {
  return STAFF_ROLES.includes(user.role);
}

export function isModerator(user: AuthedUser): boolean {
  return MOD_ROLES.includes(user.role);
}

export class HttpError extends Error {
  constructor(public status: number, msg: string) {
    super(msg);
  }
}

export function asJson(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

export function asError(err: unknown): Response {
  if (err instanceof HttpError) {
    return asJson(err.status, { error: err.message });
  }
  console.error("Unhandled error:", err);
  const msg = err instanceof Error ? err.message : "Internal server error";
  return asJson(500, { error: msg });
}
