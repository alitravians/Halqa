import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import { assertNotBanned } from "@/lib/bans";
import { BETA_TOPUP_PACK } from "@/lib/gifts";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * POST /api/wallet/topup
 *
 * Closed-beta-only: grants the single beta starter pack
 * ([BETA_TOPUP_PACK].coins) to the authenticated user. Real billing
 * (Stripe / Apple IAP / Google Play Billing) lands in v0.2 — at that
 * point this handler is replaced by a receipt-verification endpoint
 * that issues the same coin delta from a verified purchase token.
 *
 * Two safety rails:
 *   - `BYPASS_TOPUP_FOR_BETA` env var must equal "true". Before
 *     public launch we flip this off in Vercel and the endpoint
 *     starts returning 403, so a forgotten dev surface can't be
 *     trivially abused.
 *   - One pack per user per 24h, tracked via `wallets/{uid}.lastTopupAt`.
 *     This is anti-abuse, not a billing limit; the real limit comes
 *     from the IAP receipt in v0.2.
 *
 * Body: {} (the pack is fixed). Response includes the new balance so
 * the wallet UI can update without waiting for the Firestore listener.
 */
export async function POST(req: NextRequest) {
  try {
    if (process.env.BYPASS_TOPUP_FOR_BETA !== "true") {
      throw new HttpError(403, "billing not yet available — coming in v0.2");
    }
    const user = await requireUser(req);
    // Don't extend the wallet of a banned account. Otherwise the
    // ban → topup → spend loop survives the ban: the user maxes out
    // their balance while suspended and immediately resumes the abuse
    // when the ban is lifted (or via the gifts/send gate before this
    // session's PR landed).
    await assertNotBanned(user.uid);
    const db = adminFirestore();
    const walletRef = db.collection("wallets").doc(user.uid);

    const result = await db.runTransaction(async (tx) => {
      const snap = await tx.get(walletRef);
      const data = snap.data() ?? {};
      const lastTopupMs = (() => {
        const v = data.lastTopupAt;
        if (typeof v === "string") return Date.parse(v);
        if (v && typeof (v as { toMillis?: () => number }).toMillis === "function") {
          return (v as { toMillis: () => number }).toMillis();
        }
        return 0;
      })();
      const cooldownMs = 24 * 60 * 60 * 1000;
      if (lastTopupMs && Date.now() - lastTopupMs < cooldownMs) {
        const wait = Math.ceil((cooldownMs - (Date.now() - lastTopupMs)) / 60000);
        throw new HttpError(429, `pack already redeemed — try again in ${wait} minutes`);
      }

      const currentCoins = Number(data.coins ?? 0);
      const newCoins = currentCoins + BETA_TOPUP_PACK.coins;

      tx.set(
        walletRef,
        {
          uid: user.uid,
          coins: FieldValue.increment(BETA_TOPUP_PACK.coins),
          lastTopupAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      // Audit record. Lands in /audit_log alongside every other
      // server-recorded action (kyc_submit, profile_update,
      // stream_start, stream_end, …) so the staff audit endpoint
      // (`GET /api/audit/[uid]`) returns a complete user history with
      // a single query. The previous implementation wrote into a
      // separate `/topups` collection that:
      //   - had no Firestore rule (default-deny → staff couldn't even
      //     read it via the SDK; only Admin SDK could),
      //   - was never read by anything in the codebase (audit/[uid]
      //     queries `/audit_log` only, no UI surface for /topups),
      //   - left wallet top-ups invisible during incident response —
      //     a staff member chasing "why does this user have 50000
      //     coins" couldn't reconstruct the grant history without
      //     dropping into the Firebase Console.
      // Putting the entry in /audit_log also matches the format
      // (`userId / action / timestamp / metadata`) every other
      // audit-aware endpoint already uses.
      const auditRef = db.collection("audit_log").doc();
      tx.set(auditRef, {
        userId: user.uid,
        action: "wallet_topup",
        timestamp: new Date().toISOString(),
        metadata: {
          packId: BETA_TOPUP_PACK.id,
          coinsCredited: BETA_TOPUP_PACK.coins,
          source: "beta_grant",
        },
      });

      return { newCoins };
    });

    return asJson(200, {
      ok: true,
      pack: BETA_TOPUP_PACK,
      balance: { coins: result.newCoins },
    });
  } catch (e) {
    return asError(e);
  }
}
