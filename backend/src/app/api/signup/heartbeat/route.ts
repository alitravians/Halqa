import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Closed-beta soft cap. Layla's GR5 specifies 20/day; bumping the
 * constant is a deliberate, conscious decision and lives in code (not
 * env) so a flip leaves a `git blame` audit. When public launch ships
 * this constant becomes irrelevant and the route either deletes or
 * rises into the thousands.
 */
const DAILY_CAP = 20;

/**
 * Carrier-prefix bucket sanitiser. Layla's GR5 calls for a "carrier
 * prefix breakdown" so a sudden burst from a single MNO tells us the
 * abuse vector (e.g. a SIM-farm prefix). We accept the canonical
 * country dial code from the Android client (`+966`, `+20`, …) and
 * fall back to `unknown` when absent / malformed. The bucket is
 * normalised to "+<digits>" with no whitespace.
 */
function normaliseCarrier(raw: unknown): string {
  if (typeof raw !== "string") return "unknown";
  const trimmed = raw.trim();
  if (trimmed.length === 0) return "unknown";
  // Accept "+<digits>" with up to 4 digit positions (E.164 dial codes
  // are 1–3 digits; cap at 4 to absorb edge cases without permitting
  // unbounded strings as Firestore field names).
  const m = trimmed.match(/^\+(\d{1,4})$/);
  if (!m) return "unknown";
  return `+${m[1]}`;
}

/**
 * UTC date bucket. Locking is per-day; the day rolls at 00:00 UTC, not
 * Riyadh time. Using UTC keeps the bucket key trivially comparable and
 * avoids DST math (Saudi Arabia doesn't observe DST but we may add EU
 * users later who do, and the bucket key must stay stable).
 */
function todayBucket(): string {
  const now = new Date();
  const yyyy = now.getUTCFullYear().toString().padStart(4, "0");
  const mm = (now.getUTCMonth() + 1).toString().padStart(2, "0");
  const dd = now.getUTCDate().toString().padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

/**
 * POST /api/signup/heartbeat
 *
 * Layla's T&S guardrail GR5. The Android client calls this exactly
 * once per first-time sign-in, immediately after [UserDocBootstrap]
 * finishes creating `/users/{uid}` (Phone OTP and Google paths). The
 * route maintains a per-day signup counter at
 * `/metrics/signups/{YYYY-MM-DD}` and returns HTTP 423 once the daily
 * cap is reached so the client can display a "we've hit the closed-beta
 * cap; come back tomorrow" Arabic dialog and refuse to navigate to
 * Main.
 *
 * Why a POST not a Firestore trigger?
 * -----------------------------------
 * A `/users/{uid}` `onCreate` Cloud Function would be a cleaner
 * meter-point, but Cloud Functions on this project would mean wiring
 * up a billed Functions project alongside the existing Vercel-only
 * backend. A simple authed POST to Vercel costs nothing extra and is
 * idempotent enough for closed-beta scale.
 *
 * Why is the route NOT the gate that prevents the user-doc creation?
 * ------------------------------------------------------------------
 * `[UserDocBootstrap]` runs FIRST (it's part of the signInWith*Bootstrap
 * suspend chain) and writes `/users/{uid}` synchronously before the
 * Android client has a chance to even attempt this heartbeat call.
 * That ordering closes the phantom-guest bug (PR #78). It does mean
 * the 21st signup creates their `/users/{uid}` doc, then their
 * heartbeat increments the counter to 21, sets `signup_locked: true`,
 * but their nav-to-Main is the last thing the screen waits on. The
 * 22nd signup's heartbeat reads `signup_locked === true` and returns
 * HTTP 423, so they never reach Main; the staff team can either
 * unlock the day's bucket (set `signup_locked: false` via the Admin
 * SDK / Firebase Console) or wait for the UTC day rollover. The 22nd
 * user's orphaned `/users/{uid}` doc is harmless: they retry sign-in
 * after the unlock and patch through the existing-doc branch of
 * UserDocBootstrap.
 *
 * Layla's spec describes a Firestore-rule-based enforcement variant
 * ("`allow create only if signup_locked != true`"), but that path is
 * infeasible here — Firestore rules cannot compute the current date
 * to look up the right bucket — so this route IS the enforcement, per
 * the spec's documented fallback.
 *
 * Body
 * ----
 *   { phoneCountryCode?: string }   // canonical dial code, e.g. "+966"
 *
 * Response
 * --------
 *   200 { ok: true, count, locked: false }       — incremented OK
 *   200 { ok: true, count, locked: true }        — incremented and JUST
 *                                                  hit the cap; this
 *                                                  signup succeeded but
 *                                                  the next won't.
 *   423 { error: "SIGNUP_DAILY_CAP_REACHED — …" } — already locked.
 *   401 / 500                                    — the usual auth /
 *                                                  unhandled paths.
 */
export async function POST(req: NextRequest) {
  try {
    // requireUser self-creates `/users/{uid}` if absent. By the time
    // the Android client calls this route, UserDocBootstrap has
    // already created the doc (with bypass_grant + audit event), so
    // this is a no-op fetch in practice. Kept here for the same
    // defensive reasons as every other authenticated route — we never
    // trust a bare token without a user-doc lookup.
    const user = await requireUser(req);

    let body: { phoneCountryCode?: unknown } = {};
    try {
      body = (await req.json()) as { phoneCountryCode?: unknown };
    } catch {
      // Empty body is fine; carrier defaults to "unknown".
    }
    const carrier = normaliseCarrier(body.phoneCountryCode);

    const date = todayBucket();
    const ref = adminFirestore().collection("metrics").doc("signups");
    // Daily bucket lives in a subcollection so the singleton id space
    // (`/metrics/signups`) stays usable for "current state" queries
    // like a future "global locked yes/no" flag.
    const bucketRef = ref.collection("days").doc(date);

    const result = await adminFirestore().runTransaction(async (tx) => {
      const snap = await tx.get(bucketRef);
      const data = snap.exists ? snap.data() ?? {} : {};
      const previouslyLocked = data.signup_locked === true;

      if (previouslyLocked) {
        // Already over cap. Do NOT increment. Return the locked state
        // verbatim so the client can quote the count in its UI ("سُجِّل
        // X مستخدم اليوم"). The throw escapes the transaction without
        // a write, which is exactly what we want.
        throw new HttpError(
          423,
          `SIGNUP_DAILY_CAP_REACHED — closed beta daily cap of ${DAILY_CAP} signups has been reached. Try again tomorrow.`
        );
      }

      const newCount = Number(data.count ?? 0) + 1;
      const willLock = newCount >= DAILY_CAP;

      // Build the carrier increment as a dotted-path field so we can
      // increment a sub-key without overwriting the rest of the
      // breakdown map. This is the documented pattern for partial map
      // updates with FieldValue.increment.
      const carrierField = `carriers.${carrier}`;

      tx.set(
        bucketRef,
        {
          date,
          count: FieldValue.increment(1),
          [carrierField]: FieldValue.increment(1),
          updatedAt: FieldValue.serverTimestamp(),
          // Stamp createdAt only on the first write of the bucket, so
          // the staff dashboard can show "first signup of the day was
          // at HH:MM". `set` with `{ merge: true }` would overwrite
          // every call; using FieldValue.serverTimestamp on a field
          // that already exists would replace it. The cleanest option
          // is to write createdAt only inside the txn when the bucket
          // didn't exist before the read.
          ...(snap.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
          signup_locked: willLock,
          ...(willLock
            ? { locked_at: FieldValue.serverTimestamp() }
            : {}),
        },
        { merge: true }
      );

      // Mirror the signup as a structured audit row so /api/audit/[uid]
      // surfaces it for incident response, same convention as PR #61
      // (wallet topup audit) and PR #64 (gift audit).
      const auditRef = adminFirestore().collection("audit_log").doc();
      tx.set(auditRef, {
        userId: user.uid,
        action: "signup_heartbeat",
        timestamp: new Date().toISOString(),
        metadata: {
          carrier,
          dailyBucket: date,
          countAfter: newCount,
          locked: willLock,
        },
      });

      return { count: newCount, locked: willLock };
    });

    return asJson(200, {
      ok: true,
      count: result.count,
      locked: result.locked,
    });
  } catch (err) {
    return asError(err);
  }
}
