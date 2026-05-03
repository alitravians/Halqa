import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
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

      // Audit record.
      const auditRef = db.collection("topups").doc();
      tx.set(auditRef, {
        txnId: auditRef.id,
        uid: user.uid,
        packId: BETA_TOPUP_PACK.id,
        coinsCredited: BETA_TOPUP_PACK.coins,
        source: "beta_grant",
        createdAt: FieldValue.serverTimestamp(),
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
