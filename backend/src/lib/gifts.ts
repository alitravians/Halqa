/**
 * Server-authoritative gift catalogue (Halqa MVP — 4 items).
 *
 * Yasser + Sara council recommendation: ship with exactly four tiers so
 * the bottom-sheet picker fits comfortably on a 5.5" screen and the
 * tap-to-send flow never spills into a second page during the closed
 * beta. The wider catalog (12 items, lions/falcons/dragons) is parked
 * until v0.2 once we have telemetry on which gifts actually move.
 *
 * Conversion: 1 diamond = 100 coins paid by sender. Host always
 * receives `floor(price / 100)` diamonds — clean integer math, no
 * floating-point drift in Firestore transactions.
 *
 * Pricing is duplicated on the Android side ONLY for the picker UI —
 * the actual debit/credit always uses *this* table on the backend so
 * a tampered client cannot send a 100-coin gift and credit 50
 * diamonds to the host.
 */

export type GiftTier = "basic" | "rare" | "epic";

export interface GiftDef {
  id: string;
  name: string;
  emoji: string;
  priceCoins: number;
  yieldDiamonds: number;
  tier: GiftTier;
}

export const GIFT_CATALOG: ReadonlyArray<GiftDef> = Object.freeze([
  { id: "g_rose",   name: "وردة",   emoji: "🌹", priceCoins:  100, yieldDiamonds:  1, tier: "basic" },
  { id: "g_heart",  name: "قلب",    emoji: "❤️", priceCoins:  500, yieldDiamonds:  5, tier: "basic" },
  { id: "g_crown",  name: "تاج",    emoji: "👑", priceCoins: 2000, yieldDiamonds: 20, tier: "rare"  },
  { id: "g_rocket", name: "صاروخ", emoji: "🚀", priceCoins: 5000, yieldDiamonds: 50, tier: "epic"  },
] as const);

export function findGift(id: string): GiftDef | null {
  return GIFT_CATALOG.find((g) => g.id === id) ?? null;
}

/**
 * Closed-beta coin top-up grant. One pack only. When real billing
 * lands in v0.2 (Stripe / Apple IAP / Google Play Billing), this
 * helper goes away — the receipt-verification endpoint will issue
 * the same coin delta server-side from a verified purchase token.
 *
 * BYPASS_TOPUP_FOR_BETA must equal "true" in the env for this
 * endpoint to actually credit, otherwise the endpoint 403s with a
 * "billing not yet available" message.
 */
export const BETA_TOPUP_PACK = Object.freeze({
  id: "pack_beta_starter",
  coins: 1000,
  priceLabel: "بيتا — مجاني مؤقتاً",
} as const);
