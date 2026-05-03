import { adminFirestore } from "@/lib/firebase-admin";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Health probe used by:
 *   - Mobile-app cold-start to early-fail with a maintenance-mode UI
 *     instead of letting the user tap Go-Live and hit a 500.
 *   - Post-deploy gate (`scripts/post-deploy-health-check.sh`) so a
 *     broken release on Vercel doesn't silently sit in production.
 *
 * Three probes:
 *   1. `firebase`  — round-trip a metadata read against
 *                    `meta/health` doc. Confirms the service-account
 *                    secret is loaded and Firestore is reachable.
 *   2. `livekit`   — env vars present + URL parses. We do NOT open a
 *                    websocket here; that's covered by the LiveKit
 *                    webhook test which only fires under real traffic.
 *   3. `kycBypass` — surfaces the BYPASS_KYC_FOR_BETA flag so the post-
 *                    deploy script can refuse to promote a release that
 *                    still has the flag enabled past the closed-beta gate.
 *
 * Returns 200 with `ok: true` only if every required probe passes.
 * Otherwise 503 with `ok: false` and a `failures[]` list.
 */
export async function GET() {
  const checks: {
    firebase: { ok: boolean; latencyMs?: number; error?: string };
    livekit: { ok: boolean; url?: string; error?: string };
    kycBypass: { enabled: boolean };
  } = {
    firebase: { ok: false },
    livekit: { ok: false },
    kycBypass: { enabled: process.env.BYPASS_KYC_FOR_BETA === "true" },
  };

  // 1) Firestore round-trip
  const fbStart = Date.now();
  try {
    if (!process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON missing");
    }
    await adminFirestore().collection("meta").doc("health").get();
    checks.firebase = { ok: true, latencyMs: Date.now() - fbStart };
  } catch (e) {
    checks.firebase = {
      ok: false,
      latencyMs: Date.now() - fbStart,
      error: e instanceof Error ? e.message : String(e),
    };
  }

  // 2) LiveKit env validation (lightweight — no socket open)
  try {
    const url = process.env.LIVEKIT_URL;
    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    if (!url || !apiKey || !apiSecret) {
      throw new Error("LIVEKIT_URL / LIVEKIT_API_KEY / LIVEKIT_API_SECRET not all set");
    }
    if (!/^wss?:\/\//.test(url)) {
      throw new Error(`LIVEKIT_URL must start with wss:// or ws:// (got ${url.slice(0, 12)}\u2026)`);
    }
    checks.livekit = { ok: true, url };
  } catch (e) {
    checks.livekit = {
      ok: false,
      error: e instanceof Error ? e.message : String(e),
    };
  }

  const failures: string[] = [];
  if (!checks.firebase.ok) failures.push("firebase");
  if (!checks.livekit.ok) failures.push("livekit");
  const ok = failures.length === 0;

  return new Response(
    JSON.stringify({
      ok,
      service: "halqa-backend",
      time: new Date().toISOString(),
      checks,
      failures,
    }),
    {
      status: ok ? 200 : 503,
      headers: {
        "content-type": "application/json",
        "cache-control": "no-store",
      },
    }
  );
}
