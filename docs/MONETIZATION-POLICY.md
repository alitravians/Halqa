# Monetization Policy — Halqa

**Last updated:** 2 May 2026
**Owner:** Layla (Trust & Safety) + Noura (Product) + Ali (founder).

This document defines what monetization features are allowed in each
release stage, and the engineering invariants that enforce the policy
in code so a misconfigured deploy can't accidentally turn money on.

---

## 1) Stages

| Stage | `MONETIZATION_MODE` | Coins/Diamonds | IAP | Payouts |
|---|---|---|---|---|
| Closed beta | `beta` (default) | display + grant via `/wallet/topup` | **disabled** (returns 503) | **disabled** |
| Internal demo | `beta` | same | disabled | disabled |
| Public launch | `live` | display + real IAP | enabled | enabled (after KYB) |

Currently we are in **Closed Beta**. `MONETIZATION_MODE` is unset on
prod, which the code treats as `beta`.

## 2) Engineering Invariants

These are enforced in `backend/src/lib/kyc-allowlist.ts :: assertProdSafe()` and called from every monetization-relevant route:

1. **`MONETIZATION_MODE=live` requires `BYPASS_KYC_FOR_BETA` to be unset or `false`.** A live deploy that still has the global KYC bypass on is refused — the route returns 500 instead of issuing a token. This prevents the "we forgot to flip the flag back" failure mode.
2. **`BYPASS_TOPUP_FOR_BETA` must be `true` only in stage=beta.** Once `MONETIZATION_MODE=live`, the topup route returns 503 regardless of this flag.
3. **No route may write money fields without an audit log.** Every increment to `wallets.coins`, `wallets.diamonds`, or `streams.giftTotal` must be accompanied by a write to the `gift_txns/` or `wallet_txns/` audit collection in the same Firestore transaction.

## 3) Closed Beta Rules

- **No real IAP.** Top-up grants 1000 coins for free, once per 24h. Implemented in `backend/src/app/api/wallet/topup/route.ts`.
- **No host payouts.** Diamonds accumulate as a display number on `streams/{id}.giftTotal` and `wallets/{uid}.diamonds`, but there is no flow to convert them to real currency. We will add that flow only after KYB (Know-Your-Business) on the host side, scheduled for v0.3+.
- **NDA on every tester.** Every UID added to `KYC_BETA_ALLOWLIST` must have a signed NDA on file (see `docs/legal/NDA-Beta-Tester-AR.md`).
- **Privacy policy disclosed in app.** First launch shows the privacy policy and requires acknowledgment.

### 3.1) Saudi e-commerce framing (Faisal — compliance review)

خلال البيتا المغلقة، لا تتوفر أي عمليات شحن مدفوعة (paid top-up). يحصل كل مختبِر على منحة مجانية قدرها 1000 كوينز كل 24 ساعة. الكوينز والهدايا الافتراضية في هذه المرحلة ليس لها قيمة نقدية وغير قابلة للاسترداد أو التحويل أو التداول. يُفعَّل الشحن المدفوع فقط بعد استكمال السجل التجاري وتسجيل ZATCA لضريبة القيمة المضافة (15%) وربط الفوترة الإلكترونية Fatoora.

### 3.2) KYC bypass time cap

`BYPASS_KYC_FOR_BETA` expires 14 calendar days after first activation; after expiry, KYC verification becomes mandatory for all testers. Extension requires written approval from compliance lead.

## 4) Live Launch Checklist

Before flipping `MONETIZATION_MODE=live`:

- [ ] PDPL compliance review by Saudi-licensed counsel (Layla owns).
- [ ] KYB process designed for hosts who want to receive payouts.
- [ ] Real IAP integration (Google Play Billing) with server-side receipt verification.
- [ ] Refund/dispute handling SOP + customer support tooling.
- [ ] Tax registration confirmed (ZATCA).
- [ ] Drain `KYC_BETA_ALLOWLIST` to founders only; everyone else must KYC.
- [ ] Set `BYPASS_KYC_FOR_BETA=false` (or remove the env entirely).
- [ ] Set `BYPASS_TOPUP_FOR_BETA=false`.
- [ ] Deploy. `assertProdSafe()` will refuse to start if any of the above are inconsistent — that's the safety net.

## 5) Feature Flags Reference

| Env var | Type | Default | Purpose |
|---|---|---|---|
| `MONETIZATION_MODE` | `beta` \| `live` | `beta` | Stage selector. Unset = beta. |
| `BYPASS_KYC_FOR_BETA` | `true` \| `false` | `false` | Legacy global KYC bypass. Logs a warning every grant. Replaced by `KYC_BETA_ALLOWLIST`. **Expires 14 calendar days after first activation; extension requires compliance-lead written approval.** |
| `KYC_BETA_ALLOWLIST` | CSV of UIDs | empty | Per-UID KYC bypass for trusted testers. |
| `BYPASS_TOPUP_FOR_BETA` | `true` \| `false` | `false` | Enables `/wallet/topup` in beta. |
