import type { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, HttpError, requireUser } from "@/lib/auth";
import { assertNotBanned } from "@/lib/bans";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * POST /api/wallet/withdraw
 *
 * Withdrawals (cashout of host earnings → bank / Mada / STC Pay) are NOT
 * yet a live product surface — the closed-beta build returns 503 to every
 * caller. This route exists today exclusively so Layla's T&S guardrail
 * **GR4 (KYC bypass re-verification block)** is wired up before the real
 * cashout path ships, and we don't ever land in a state where:
 *
 *   1. v0.1.x (closed beta) admits users under `BYPASS_KYC_FOR_BETA=true`
 *      with a stamped `bypass_grant.will_reverify == true` on their
 *      `/users/{uid}` doc (see android `UserDocBootstrap.kt`),
 *   2. v0.2.x flips `BYPASS_KYC_FOR_BETA=false` and ships withdrawals
 *      simultaneously, and
 *   3. a grandfathered user whose KYC was never document-verified
 *      withdraws real currency before manual re-KYC review catches them.
 *
 * That order-of-operations is the grandfather-then-cashout exploit
 * Layla's GR4 prevents. Rather than wiring it later, the gate goes in
 * NOW — even though it returns 503 to everyone today, the gate's first
 * branch is the bypass-grant check, so when the 503 is removed in v0.2
 * the existing withdraw call sites trip the right error.
 *
 * Order of checks (DO NOT reorder — the bypass check must precede the
 * generic 503 so the error code returned to the client is the
 * actionable one, not the misleading "feature unavailable"):
 *
 *   1. authn (`requireUser`).
 *   2. ban gate (`assertNotBanned`) — banned accounts cannot cashout
 *      even if they slipped past every other check.
 *   3. **bypass-grant block (GR4).** Read `/users/{uid}.bypass_grant`.
 *      If `will_reverify == true`, return HTTP 403 with the explicit
 *      error code `KYC_BYPASS_REVERIFY_REQUIRED`. The Android client
 *      maps this code to an Arabic explainer ("يجب إعادة التحقق من
 *      هويتك قبل سحب الأرباح") and a deep-link to the KYC re-verify
 *      flow.
 *   4. generic feature-not-yet-available 503 — for everyone else.
 *
 * When the real cashout path lands in v0.2, the 503 stub is replaced
 * with the actual flow (validate amount, debit wallet inside a txn,
 * enqueue payout, write `/audit_log` entry). The first three gates
 * above stay verbatim — they're the durable security envelope.
 *
 * Telemetry intent: every 403 from this endpoint is a real grandfathered
 * user who just attempted a withdrawal. That number is a useful canary
 * for "how many beta users are we still holding pending re-KYC" — when
 * it reaches a small number, we can run a re-KYC outreach campaign.
 */
export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);

    // Banned account → no cashout regardless of any other state.
    // assertNotBanned throws HttpError(403, "Account is banned.") which
    // asError forwards verbatim. Keep this check ahead of the bypass
    // gate so a banned user gets the ban message, not the bypass
    // message — bans are higher-severity and we want the more accurate
    // error class on top.
    await assertNotBanned(user.uid);

    // Layla's T&S guardrail GR4 — the durable cashout block for users
    // who were grandfathered past closed-beta KYC. The Android client
    // (and any future iOS client) writes `bypass_grant` synchronously
    // on first sign-in when `BuildConfig.BYPASS_KYC_FOR_BETA` is on.
    // The field is immutable from the client side (the user-doc
    // self-update rule blocks writes to `role/createdAt/uid` but
    // permits arbitrary other-field writes — until that rule is
    // tightened, the manual re-KYC flow that clears `will_reverify`
    // MUST go through the Admin SDK so a malicious client cannot
    // self-clear the gate).
    //
    // Non-existence of the user doc, or a doc without `bypass_grant`,
    // is treated as "no grandfathering" and falls through. The
    // backend `requireUser` self-create path doesn't write
    // `bypass_grant`, so a server-created doc has no grant; the
    // grant is only stamped on the client-side first-sign-in path
    // (which is the only path under the closed-beta bypass).
    const userRef = adminFirestore().collection("users").doc(user.uid);
    const userSnap = await userRef.get();
    const userData = userSnap.exists ? userSnap.data() ?? {} : {};
    const grant = userData.bypass_grant as
      | { will_reverify?: unknown }
      | undefined;
    if (grant && grant.will_reverify === true) {
      // Use the explicit code in the error message so the Android
      // client can string-match on it. Plain English message body
      // because the localised user-facing string lives on the client
      // (see PR #50 humanize() pattern). The code is stable; the
      // wording can evolve without breaking the client mapping.
      throw new HttpError(
        403,
        "KYC_BYPASS_REVERIFY_REQUIRED — manual re-KYC review required before withdrawal."
      );
    }

    // Real cashout path is post-v0.2. Until then, return a 503 with a
    // stable code the client can match on for "withdrawals coming
    // soon" UI. NOT a 403 because this is a feature-availability
    // state, not a permission denial — clients should not display
    // the same "you're blocked" UI for a state that affects everyone.
    throw new HttpError(
      503,
      "WITHDRAW_NOT_AVAILABLE — withdrawals are not yet enabled in this beta."
    );
  } catch (e) {
    return asError(e);
  }
}
