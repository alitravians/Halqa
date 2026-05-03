import type { NextRequest } from "next/server";
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
 *
 * ## Public vs. internal response
 *
 * This endpoint is unauthenticated by Cloudflare/Vercel routing, so any
 * scraper can hit it. We split the payload into two shapes:
 *
 *   - **Public** (default): only the booleans that the mobile app
 *     legitimately needs to drive its maintenance-mode banner. No env
 *     fingerprinting, no internal feature-flag state, no error
 *     messages from the underlying SDKs.
 *
 *   - **Internal**: full detail (latency, LiveKit URL, kycBypass flag,
 *     verbose error strings). Gated behind a bearer token check
 *     against `HEALTH_INTERNAL_TOKEN`. The post-deploy script and any
 *     ops/monitoring runs with the token; everyone else sees the
 *     sanitised shape.
 *
 * `kycBypass.enabled` in particular MUST NOT be public. Telling the
 * world "this deploy still has the closed-beta KYC bypass on" is a
 * loud signal to abusers that they can broadcast without identity
 * verification — exactly the kind of leak this audit is closing
 * before public launch.
 */
export async function GET(req: NextRequest) {
  const internal = isInternalAuthed(req);

  type FirebaseDetail = { ok: boolean; latencyMs?: number; error?: string };
  type LivekitDetail = { ok: boolean; url?: string; error?: string };
  type KycDetail = { enabled: boolean };

  const detail: {
    firebase: FirebaseDetail;
    livekit: LivekitDetail;
    kycBypass: KycDetail;
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
    detail.firebase = { ok: true, latencyMs: Date.now() - fbStart };
  } catch (e) {
    detail.firebase = {
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
    detail.livekit = { ok: true, url };
  } catch (e) {
    detail.livekit = {
      ok: false,
      error: e instanceof Error ? e.message : String(e),
    };
  }

  const failures: string[] = [];
  if (!detail.firebase.ok) failures.push("firebase");
  if (!detail.livekit.ok) failures.push("livekit");
  const ok = failures.length === 0;

  // Default sanitised public payload — booleans only, no env strings.
  const publicChecks = {
    firebase: { ok: detail.firebase.ok },
    livekit: { ok: detail.livekit.ok },
  };

  const responseBody = internal
    ? { ok, service: "halqa-backend", time: new Date().toISOString(), checks: detail, failures }
    : { ok, service: "halqa-backend", time: new Date().toISOString(), checks: publicChecks, failures };

  return new Response(JSON.stringify(responseBody), {
    status: ok ? 200 : 503,
    headers: {
      "content-type": "application/json",
      "cache-control": "no-store",
    },
  });
}

/**
 * Constant-time bearer-token check against `HEALTH_INTERNAL_TOKEN`.
 *
 *   - Token absent in env → no caller can ever authenticate; the
 *     endpoint serves the sanitised payload for everyone, which is
 *     the safe default until ops provisions the token. This is the
 *     state right after this PR merges; the post-deploy script
 *     continues to work in non-strict mode.
 *
 *   - Token present in env → callers must send
 *     `Authorization: Bearer <token>`. Mismatched / missing → public
 *     payload; matching → internal payload (kycBypass, livekit.url,
 *     verbose errors).
 *
 * Constant-time comparison so a curious attacker can't time-leak the
 * token byte-by-byte (Node `===` short-circuits on first mismatch).
 */
function isInternalAuthed(req: NextRequest): boolean {
  const expected = process.env.HEALTH_INTERNAL_TOKEN;
  if (!expected) return false;
  const header = req.headers.get("authorization") ?? "";
  const prefix = "Bearer ";
  if (!header.startsWith(prefix)) return false;
  const provided = header.slice(prefix.length);
  return safeEqual(provided, expected);
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let mismatch = 0;
  for (let i = 0; i < a.length; i++) {
    mismatch |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return mismatch === 0;
}
