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

/**
 * Convert any thrown value into the response we send back to the client.
 *
 * The contract is:
 *   - `HttpError` is a value WE programmatically throw with a message
 *     intended for the client. Forward it verbatim with the chosen
 *     status code.
 *   - Anything else is an UNHANDLED failure — Firestore SDK errors,
 *     code bugs, `assertProdSafe()` config-state errors, etc. The
 *     message of those almost always contains internal information
 *     (file paths, env-var names, SDK internals, `RuntimeError: Cannot
 *     read property 'data' of undefined at /var/task/...`). We MUST
 *     NOT echo it to the client. Log the full detail server-side
 *     (Vercel function logs), generate a short request id so callers
 *     can quote it in support tickets, and return a generic 500 with
 *     just `{ error: "Internal server error", requestId }`.
 *
 * Before this change, `asError` was returning `{ error: err.message }`
 * for ALL non-HttpError throws. A real example of what that leaked:
 *   - `assertProdSafe()` failure → "[kyc] BYPASS_KYC_FOR_BETA cannot
 *     be true when MONETIZATION_MODE=live. …" — tells abusers that
 *     the closed-beta KYC bypass flag is on a live deploy.
 *   - Firestore SDK transient → "5 NOT_FOUND: No document to update:
 *     projects/halqa-prod/databases/(default)/documents/streams/u_…"
 *     — leaks the project id, document path layout, and the streamId
 *     namespace shape.
 *   - JS runtime → "Cannot read properties of undefined (reading
 *     'displayName') at /var/task/.next/server/app/api/.../route.js:74:18"
 *     — leaks the Next.js server bundle layout and the line where the
 *     bug lives, which is a foothold for further probing.
 */
export function asError(err: unknown): Response {
  if (err instanceof HttpError) {
    return asJson(err.status, { error: err.message });
  }
  // Short, low-collision request id. Clients can quote it when
  // reporting an issue and we can grep Vercel logs for it.
  const requestId = `req_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  // Full detail goes to the Vercel function log so we can debug.
  // Stringifying keeps the stack in JSON-friendly shape; the spread
  // is a no-op for non-objects but lets Error subclasses survive.
  console.error("Unhandled error:", { requestId, err });
  return asJson(500, {
    error: "Internal server error.",
    requestId,
  });
}
