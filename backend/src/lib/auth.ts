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
    // Map Firebase Admin SDK error codes to stable, safe client
    // messages. Two reasons this is NOT just `err.message`:
    //
    //   1. Information disclosure. Firebase Admin SDK error
    //      messages contain implementation detail the client
    //      can't act on and a 401 attacker shouldn't see, e.g.:
    //
    //        "Firebase ID token has incorrect 'kid' claim. Maybe
    //         the public key for the project rotated; check the
    //         Firebase JWKS endpoint."
    //        "Decoding Firebase ID token failed. Make sure you
    //         passed the entire string JWT representing the ID
    //         token. See https://firebase.google.com/docs/...
    //         for details on how to retrieve an ID token."
    //
    //      Echoing those into `{error: <msg>}` (which `asError`
    //      does for HttpError verbatim — line ~120) and then to
    //      the Android client (which shows the body via
    //      `Throwable.humanize`, see ApiErrors.kt) leaks SDK
    //      internals + Firebase docs URLs to every 401-er.
    //
    //   2. UX. The Android humanize fallback for 401 is a clean
    //      Arabic string ("انتهت الجلسة. أعد تسجيل الدخول.") but
    //      it only fires when the body is empty/unparseable. If
    //      we send the Firebase English message, the user sees
    //      that English message instead of the localised one.
    //      Sending a stable English-but-deliberate message keeps
    //      it forward-compat with backend log search and lets
    //      the client decide whether to localise.
    const code = (err as { code?: string }).code;
    if (code === "auth/id-token-expired") {
      throw new HttpError(401, "Session expired. Please sign in again.");
    }
    if (code === "auth/id-token-revoked" || code === "auth/user-disabled") {
      throw new HttpError(401, "Session was revoked. Please sign in again.");
    }
    if (code === "auth/argument-error") {
      // Malformed JWT structure (missing segment, bad base64).
      // Treat as invalid token; do NOT echo the SDK's "Decoding
      // ... failed. Make sure ..." string.
      throw new HttpError(401, "Invalid session token.");
    }
    // Catch-all for unknown auth/* codes (signature failures,
    // wrong audience, key rotation issues, etc.). All of those
    // are the same thing from the user's perspective: the token
    // they're holding is unusable, sign in again.
    throw new HttpError(401, "Invalid or expired session token.");
  }

  const uid = decoded.uid;
  const userRef = adminFirestore().collection("users").doc(uid);
  const snap = await userRef.get();
  let role: UserRole = "user";
  let displayName: string | null = null;
  let handle: string | null = null;
  if (snap.exists) {
    const data = snap.data() ?? {};
    // PR-K: self-service account deletion is a soft-delete that
    // anonymizes the user doc and deletes the Firebase Auth user.
    // The Auth-delete invalidates new sign-ins, but JWTs issued
    // before deletion remain valid for ~1h (token expiry) unless
    // we explicitly check {checkRevoked: true} or block here.
    // Block here — fail-closed.
    if (data.deleted === true) {
      throw new HttpError(403, "Account has been deleted.");
    }
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
    //
    // Layla LAYLA-002 — the closed-beta KYC bypass audit trail (Layla
    // GR1 + GR2) is also written here, atomically, when this path
    // fires while `BYPASS_KYC_FOR_BETA=true`. Background:
    //
    //   Android sign-in flows (phone / Google / email) call
    //   [UserDocBootstrap.ensureUserDoc] which stamps `bypass_grant`
    //   on `/users/{uid}` and mirrors a `kyc_bypass_granted` row into
    //   `/audit/{uid}/events`. That client-side write is the primary
    //   path. But [UserDocBootstrap] is documented to be **fail-open**
    //   on Firestore read failures (Result.ReadFailed, see line ~85)
    //   — when the initial read of `/users/{uid}` throws (offline,
    //   transient quota, rule propagation lag), the Android side
    //   silently returns without writing the doc, expecting this
    //   backend lazy-create branch to fill it in on the first
    //   authenticated REST call.
    //
    //   Pre-fix that fall-through wrote a user doc WITHOUT
    //   `bypass_grant`. The /api/wallet/withdraw 403 hard-block
    //   (Layla GR4) keys on `bypass_grant.will_reverify === true`,
    //   so this cohort silently shipped as already-cleared even
    //   though they were grandfathered. Same exploit shape as
    //   PR #87 (which closed the immutability hole), just a
    //   different write path.
    //
    // Authority model: when the Android client and the backend
    // disagree about whether to stamp `bypass_grant`, the backend
    // wins. Both code paths key on `BYPASS_KYC_FOR_BETA`; the env
    // var is the source of truth. Android writes are a best-effort
    // optimization to avoid the phantom-guest UI flicker — the
    // backend lazy-create is the durable enforcement point.
    //
    // We write the user doc + the audit-event row in a single
    // Firestore batch so the doc never exists in a half-stamped
    // state ("user doc has bypass_grant but no audit row" or
    // vice versa). Admin SDK bypasses firestore.rules so no
    // rule changes are needed for the audit write.
    const initialDisplayName = (decoded.name || "").trim();
    const bypassActive = process.env.BYPASS_KYC_FOR_BETA === "true";
    const userDocPayload: Record<string, unknown> = {
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
    };
    if (bypassActive) {
      userDocPayload.bypass_grant = {
        reason: "BETA_M0_BACKEND_LAZY_CREATE",
        granted_at: new Date().toISOString(),
        granted_via: "BYPASS_KYC_FOR_BETA",
        will_reverify: true,
      };
    }

    const db = adminFirestore();
    const batch = db.batch();
    batch.set(userRef, userDocPayload, { merge: true });
    if (bypassActive) {
      // Mirror the same grant into a separate, append-only audit
      // trail at `/audit/{uid}/events/{auto}` (Layla GR2). Same
      // schema the Android-side [UserDocBootstrap] writes — staff
      // queries enumerate this collection without caring about the
      // origin path.
      const auditRef = db.collection("audit").doc(uid).collection("events").doc();
      batch.set(auditRef, {
        uid,
        type: "kyc_bypass_granted",
        granted_at: new Date().toISOString(),
        reason: "BETA_M0_BACKEND_LAZY_CREATE",
        env_flag_value: true,
      });
    }
    await batch.commit();
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
