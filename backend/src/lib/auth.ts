import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminAuth, adminFirestore } from "./firebase-admin";

// PR-L (LAYLA-R2-001). Closed-beta daily signup cap mirror.
//
// Layla's GR5 caps signups at 20/UTC-day. The primary enforcement is
// `/api/signup/heartbeat` which Android calls on UserDocBootstrap.Created.
// But that heartbeat is bypassable: if the Android bootstrap hits
// `Result.ReadFailed` (network / quota / rule-lag), the client silently
// returns without calling heartbeat — and a tampered client that POSTs
// to /users/{uid} directly through the Firestore SDK never calls
// heartbeat at all. Either way, the user-doc gets created (here, in the
// backend lazy-create branch below) but the counter never increments.
//
// This PR closes that gap: whenever the backend lazy-creates a /users/
// doc (the only path that writes `bypass_grant` server-side), it ALSO
// atomically increments /metrics/signups/days/{YYYY-MM-DD}.count inside
// the same transaction. If the counter has already reached the cap, we
// stamp `bypass_grant.over_cap=true` on the new doc — for cohort
// tracking — but we DO NOT block the user from completing sign-in.
// The Auth account already exists at this point (token verified
// successfully); refusing the Firestore write would leave a phantom
// user with no profile and the bypass_grant audit would never write.
//
// The 20/day cap is duplicated here rather than imported from
// heartbeat/route.ts because lib/ should not import from app/api/
// (Next.js layering); see comment on LAZY_CREATE_DAILY_CAP.
const LAZY_CREATE_DAILY_CAP = 20;

function lazyCreateTodayBucket(): string {
  const now = new Date();
  const yyyy = now.getUTCFullYear().toString().padStart(4, "0");
  const mm = (now.getUTCMonth() + 1).toString().padStart(2, "0");
  const dd = now.getUTCDate().toString().padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function lazyCreateCarrierFromPhone(phone: string | null | undefined): string {
  if (!phone || typeof phone !== "string") return "unknown";
  const m = phone.trim().match(/^\+(\d{1,4})/);
  return m ? `+${m[1]}` : "unknown";
}

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
  /**
   * PR-H — fast-path ban state read from `/users/{uid}.banned`. Mirror
   * of the `active==true` rows in `/bans/` written by the admin/mod
   * ban endpoint inside the same Firestore txn. Endpoints that gate
   * banned users (gifts/send, wallet/topup, livekit/token, etc) read
   * this from the AuthedUser to avoid a second Firestore round-trip.
   * Exempt endpoints (kyc/submit, users/me, settings, audit/[uid],
   * streams/end) ignore the field.
   */
  banned: boolean;
}

/**
 * Options for {@link requireUser}.
 */
export interface RequireUserOptions {
  /**
   * PR-H — when `true`, requireUser does NOT 403 a banned user. Use
   * this on endpoints that must remain reachable while banned (the
   * appeal/cleanup paths documented in `bans.ts`: kyc/submit,
   * users/me, settings, audit/[uid], streams/end, wallet/me-GET).
   *
   * Default is `false` (fail-closed). Adding a new endpoint? Decide
   * deliberately: most endpoints should NOT pass this. If you're not
   * sure, leave it off — the worst case is a banned user gets a 403
   * on a path you'd rather they not, which is reversible. The opposite
   * (silently letting a banned user reach a gated endpoint) is a
   * moderation bypass.
   */
  allowBanned?: boolean;
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
export async function requireUser(
  req: NextRequest,
  opts: RequireUserOptions = {}
): Promise<AuthedUser> {
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
  let banned = false;
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

    // PR-H — fast-path ban state mirror of /bans/{...}.active==true.
    // Written by /api/admin/users/{uid}/ban inside the same txn as
    // the /bans/ row + audit_log. The strict === true check rejects
    // truthy non-booleans (string "true", number 1) that a tampered
    // doc might carry — only a deliberate boolean true is a ban.
    banned = data.banned === true;

    // KHALID-R2-002 — backfill bypass_grant when the doc exists but
    // the field is missing.
    //
    // Attack we close: a tampered client (modified APK, web Firestore
    // SDK, curl with a valid id token) authenticates with Firebase
    // Auth, then writes `/users/{uid}` DIRECTLY via the Firestore SDK
    // with `role: 'user'` + `uid: uid` and NO bypass_grant field. The
    // CREATE rule on /users permits this — `bypass_grant` is optional
    // on create (`!('bypass_grant' in request.resource.data) || ...`).
    // From that point on, every authenticated REST call lands here,
    // sees `snap.exists`, falls into THIS branch, and the lazy-create
    // backfill (the `else` branch below) never runs. The user's doc
    // permanently lacks `bypass_grant`, and the /api/wallet/withdraw
    // GR4 gate (which keys on `bypass_grant.will_reverify === true`)
    // treats them as "not grandfathered" — falling through to the
    // generic 503 today and (when v0.2 lifts that 503) to the actual
    // cashout path WITHOUT manual re-KYC.
    //
    // The Android `UserDocBootstrap` writes `bypass_grant` correctly,
    // and `requireUser`'s lazy-create branch (PR #92) writes it on
    // server-side first-touch — but BOTH paths short-circuit when the
    // doc already exists. A malicious client races them by creating
    // the doc themselves, with the grant field omitted, before any
    // backend call lands.
    //
    // Fix: any time we observe an existing /users/{uid} doc that is
    // MISSING bypass_grant while the closed-beta flag is active,
    // stamp it now. Admin SDK bypasses firestore.rules so the write
    // succeeds even though the client-side rules don't permit
    // `bypass_grant` in a self-update. The corresponding audit row
    // gets the dedicated reason "BETA_M0_BACKEND_BACKFILL" so staff
    // can distinguish a backfill from a normal first-sign-in stamp
    // (which uses "BETA_M0_BACKEND_LAZY_CREATE" or the Android
    // "BETA_M0_PHONE_OTP" / "BETA_M0_EMAIL").
    //
    // Idempotency: if a concurrent request runs the same backfill,
    // both write the same shape with `merge: true` and Firestore
    // commits commutatively. The audit row uses an auto-id so two
    // races write two audit rows — duplicate-but-harmless. Better
    // than racing for a single audit row and losing one branch.
    const bypassActive = process.env.BYPASS_KYC_FOR_BETA === "true";
    if (bypassActive && !data.bypass_grant) {
      const nowIso = new Date().toISOString();
      const db = adminFirestore();
      const batch = db.batch();
      batch.set(
        userRef,
        {
          bypass_grant: {
            reason: "BETA_M0_BACKEND_BACKFILL",
            granted_at: nowIso,
            granted_via: "BYPASS_KYC_FOR_BETA",
            will_reverify: true,
          },
          updatedAt: nowIso,
        },
        { merge: true }
      );
      const auditRef = db.collection("audit").doc(uid).collection("events").doc();
      batch.set(auditRef, {
        uid,
        type: "kyc_bypass_granted",
        granted_at: nowIso,
        reason: "BETA_M0_BACKEND_BACKFILL",
        env_flag_value: true,
      });
      await batch.commit();
    }
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
    const phoneNumber = decoded.phone_number || null;

    // PR-L: atomic cap-counter integration. We perform the user-doc
    // create + audit-event write + cap-counter increment in a single
    // Firestore transaction. The txn reads the day-bucket FIRST so it
    // knows whether the cap is hit BEFORE deciding whether to stamp
    // bypass_grant.over_cap=true on the new user doc.
    //
    // Why a txn (not a batch.commit like before)? Because we need a
    // read-before-write on the bucket to know prev count. A batch is
    // write-only and would force us to choose between unconditional
    // increment (which is fine for the counter itself but doesn't give
    // us the prev value to set over_cap) and an unsafe two-step
    // (read outside, increment inside batch — TOCTOU on the cap).
    //
    // What's NOT done here: blocking the user. Even when over_cap, we
    // still create the user doc, write audit, and increment counter.
    // The auth-Firebase user already exists at this point (we
    // successfully verifyIdToken'd above) — leaving them without a
    // Firestore profile would create the same phantom-guest class
    // PR #78 closed. The Android client UI is the cap gate (heartbeat
    // returns 423); this txn is the defence-in-depth metric.
    const date = lazyCreateTodayBucket();
    const carrier = lazyCreateCarrierFromPhone(phoneNumber);
    const carrierField = `carriers.${carrier}`;

    const db = adminFirestore();
    const bucketRef = db
      .collection("metrics")
      .doc("signups")
      .collection("days")
      .doc(date);
    const auditEventRef = db
      .collection("audit")
      .doc(uid)
      .collection("events")
      .doc();
    const auditLogRef = db.collection("audit_log").doc();

    await db.runTransaction(async (tx) => {
      const bucketSnap = await tx.get(bucketRef);
      const bucketData = bucketSnap.exists ? bucketSnap.data() ?? {} : {};
      const prevCount = Number(bucketData.count ?? 0);
      const overCap = prevCount >= LAZY_CREATE_DAILY_CAP;
      const newCount = prevCount + 1;
      const willLock = newCount >= LAZY_CREATE_DAILY_CAP;
      const nowIso = new Date().toISOString();

      const userDocPayload: Record<string, unknown> = {
        uid,
        email: decoded.email || null,
        phoneNumber,
        displayName: initialDisplayName,
        handle: "",
        bio: "",
        avatar: "",
        role: "user",
        createdAt: nowIso,
        updatedAt: nowIso,
      };
      if (bypassActive) {
        userDocPayload.bypass_grant = {
          reason: "BETA_M0_BACKEND_LAZY_CREATE",
          granted_at: nowIso,
          granted_via: "BYPASS_KYC_FOR_BETA",
          will_reverify: true,
          // PR-L cohort marker: this user signed up while the cap was
          // already reached. Staff dashboards filter on this to identify
          // the "over-cap cohort" — they got in despite the cap because
          // the cap is documented as a soft signal, not a hard block.
          over_cap: overCap,
        };
      }
      tx.set(userRef, userDocPayload, { merge: true });

      if (bypassActive) {
        // Append-only audit-event row (Layla GR2). Same schema
        // [UserDocBootstrap] writes from Android.
        tx.set(auditEventRef, {
          uid,
          type: "kyc_bypass_granted",
          granted_at: nowIso,
          reason: "BETA_M0_BACKEND_LAZY_CREATE",
          env_flag_value: true,
          over_cap: overCap,
        });
      }

      // Atomic cap-counter bump. FieldValue.increment is safe on first
      // write (creates the counter at 1). The carrier sub-map uses the
      // same dotted-path pattern as /api/signup/heartbeat so the staff
      // dashboard's carrier-breakdown query keeps working unchanged.
      tx.set(
        bucketRef,
        {
          date,
          count: FieldValue.increment(1),
          [carrierField]: FieldValue.increment(1),
          updatedAt: FieldValue.serverTimestamp(),
          ...(bucketSnap.exists
            ? {}
            : { createdAt: FieldValue.serverTimestamp() }),
          signup_locked: willLock,
          ...(willLock ? { locked_at: FieldValue.serverTimestamp() } : {}),
        },
        { merge: true }
      );

      // Audit_log mirror — same convention as
      // /api/signup/heartbeat:signup_heartbeat, but action key
      // 'signup_heartbeat_backend' makes the path easy to filter
      // in staff investigations ("were these counted via Android
      // or via the backend lazy-create fallback?").
      tx.set(auditLogRef, {
        userId: uid,
        action: "signup_heartbeat_backend",
        timestamp: nowIso,
        metadata: {
          carrier,
          dailyBucket: date,
          countAfter: newCount,
          locked: willLock,
          over_cap: overCap,
          source: "auth.requireUser_lazy_create",
        },
      });
    });
    displayName = initialDisplayName.length > 0 ? initialDisplayName : null;
  }

  // PR-H — global ban gate (fail-closed).
  //
  // After the user-doc snapshot is read and `banned` is populated, a
  // banned user is 403'd here for every authed endpoint UNLESS the
  // endpoint passes `{ allowBanned: true }`. The exempt list (see
  // `bans.ts` doc comment + `RequireUserOptions.allowBanned` JSDoc):
  //
  //   kyc/submit    — appeal path
  //   users/me      — profile updates (correct handle, etc)
  //   settings      — preferences, no abuse vector
  //   audit/[uid]   — own audit log read
  //   streams/end   — clean up own running stream
  //   wallet/me-GET — see own balance
  //
  // Every other endpoint receives the default `allowBanned: false`
  // and 403s on the ban. The error message intentionally matches the
  // existing `assertNotBanned()` helper in `bans.ts` so the Android
  // client's `Throwable.humanize` localisation path stays consistent
  // across both code paths (gradual migration from per-endpoint
  // assertNotBanned to global requireUser gate).
  if (banned && !opts.allowBanned) {
    throw new HttpError(403, "Account is banned.");
  }

  return {
    uid,
    email: decoded.email || null,
    phoneNumber: decoded.phone_number || null,
    displayName,
    handle,
    role,
    banned,
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
