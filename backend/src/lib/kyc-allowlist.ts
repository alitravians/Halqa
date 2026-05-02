/**
 * Closed-beta KYC allowlist (Layla, M3 hardening).
 *
 * Replaces the blanket `BYPASS_KYC_FOR_BETA` flag with a narrowly-scoped
 * allowlist of UIDs that can publish without an approved KYC submission.
 *
 * Threat model
 * ------------
 * The blanket bypass is a footgun: a single mis-set Vercel env var lets
 * the entire internet broadcast on a platform that legally must verify
 * identity (Saudi PDPL + child-safety policy). Allowlisting narrows the
 * blast radius to a known set of UIDs even if the flag is left enabled
 * accidentally.
 *
 * Two layers, evaluated in order:
 *   1. KYC_BETA_ALLOWLIST env var (CSV of Firebase UIDs) — preferred,
 *      lets the operator add/remove testers without a redeploy. Trimmed
 *      and de-duplicated.
 *   2. Hardcoded `STATIC_ALLOWLIST` below — ONLY for the founding team
 *      so we can't lock ourselves out of broadcasting in an emergency.
 *
 * The legacy `BYPASS_KYC_FOR_BETA=true` still works as a global override
 * (we don't break Ali's mid-test runs), but it now LOGS A WARNING on
 * every grant so it shows up in Vercel logs and can't quietly stay on
 * forever. The plan is to delete that branch entirely once #19 is in
 * production for one full week without incident.
 */

/**
 * Founding-team UIDs. These bypass KYC even if the env var is empty.
 * Keep this list TINY. Anyone listed here can broadcast without ever
 * submitting KYC, which is a real exposure for child-safety review.
 */
const STATIC_ALLOWLIST: ReadonlyArray<string> = Object.freeze([
  // Add real UIDs here once we have them. Empty for now so the env var
  // is the only path until Ali opts founding-team UIDs in by hand.
]);

function parseEnvAllowlist(): Set<string> {
  const raw = process.env.KYC_BETA_ALLOWLIST ?? "";
  if (!raw.trim()) return new Set();
  return new Set(
    raw
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
  );
}

export type KycBetaDecision =
  | { allowed: false }
  | { allowed: true; via: "static-allowlist" | "env-allowlist" | "global-bypass" };

export function evaluateKycBeta(uid: string): KycBetaDecision {
  if (STATIC_ALLOWLIST.includes(uid)) {
    return { allowed: true, via: "static-allowlist" };
  }
  const envAllow = parseEnvAllowlist();
  if (envAllow.has(uid)) {
    return { allowed: true, via: "env-allowlist" };
  }
  if (process.env.BYPASS_KYC_FOR_BETA === "true") {
    // Loud signal — every legacy bypass shows up in Vercel logs so we
    // notice if the flag is left on past its useful window.
    console.warn(
      `[kyc] global BYPASS_KYC_FOR_BETA active; granting publisher access to uid=${uid}. ` +
        `Switch to KYC_BETA_ALLOWLIST CSV before public launch.`
    );
    return { allowed: true, via: "global-bypass" };
  }
  return { allowed: false };
}

/**
 * Production-mode invariant: the global bypass must NOT be on once the
 * app is in monetized public mode. Endpoints can call this on startup
 * or per-request to fail fast if a deploy left the flag enabled.
 */
export function assertProdSafe(): void {
  if (
    process.env.MONETIZATION_MODE === "live" &&
    process.env.BYPASS_KYC_FOR_BETA === "true"
  ) {
    throw new Error(
      "[kyc] BYPASS_KYC_FOR_BETA cannot be true when MONETIZATION_MODE=live. " +
        "Refusing to start in unsafe configuration."
    );
  }
}
