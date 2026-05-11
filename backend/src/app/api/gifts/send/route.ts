import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import { assertNotBanned } from "@/lib/bans";
import { findGift } from "@/lib/gifts";
import {
  assertAndIncrementGiftRate,
  assertNotBlockedFromGifting,
  MAX_BATCH_COUNT,
} from "@/lib/gift-rate-limit";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * POST /api/gifts/send
 *
 * Body: { streamId: string, giftId: string, count?: number }
 *
 * Atomic Firestore transaction. The whole flow either commits or rolls
 * back — there is no intermediate state where a sender's coins are
 * gone but the host's diamonds didn't arrive (the bug Yasser flagged
 * as a hard blocker for any monetisation: "if the gift can fail
 * mid-way you've already lost user trust forever").
 *
 * Steps inside the txn:
 *   1. Read sender wallet → assert `coins >= price * count`.
 *      If the wallet doc doesn't exist yet, treat balance as 0
 *      (refuse — we never auto-credit on write).
 *   2. Read stream → assert `status == "live"` and pluck `ownerUid`.
 *      Refuse if the host is the same as the sender (no self-gifting
 *      to inflate diamonds).
 *   3. Debit sender:    coins -= total, coinsSpent += total
 *   4. Credit host:     diamonds += yield, diamondsEarned += yield
 *   5. Bump stream:     giftTotal += yield (drives the Diamonds
 *                       overlay on the broadcaster screen via
 *                       StreamSnapshot SSoT).
 *   6. Audit:           streams/{streamId}/gifts/{txnId} immutable
 *                       record. Server time, full breakdown.
 *
 * Returns: { ok: true, balance: { coins, diamonds }, txnId }
 *
 * Server is the source of truth for *all* prices and yields — the
 * client is asked only for the gift id. A tampered client cannot send
 * a "100-coin gift" and credit 50 diamonds.
 */
export async function POST(req: NextRequest) {
  try {
    const sender = await requireUser(req);
    // P0 — refuse gifts from a banned account. Until this gate landed,
    // the bans collection only stopped broadcasting / viewing; a banned
    // user could keep POSTing /api/gifts/send directly (curl, Postman,
    // an old Android session that already had a streamId cached) and
    // continue the very harassment-via-gifting that triggered the ban.
    await assertNotBanned(sender.uid);

    const body = (await req.json().catch(() => ({}))) as {
      streamId?: unknown;
      giftId?: unknown;
      count?: unknown;
    };
    const streamId = typeof body.streamId === "string" ? body.streamId.trim() : "";
    const giftId = typeof body.giftId === "string" ? body.giftId.trim() : "";

    if (!streamId) throw new HttpError(400, "streamId is required");
    if (!giftId) throw new HttpError(400, "giftId is required");

    // Yasser Y-H2 — strict count validation BEFORE the txn. The earlier
    // implementation silently clamped (`Math.max(1, Math.min(99,
    // Math.floor(countRaw)))`) which:
    //   - accepted NaN  -> floor(NaN)=NaN -> Math.min(99,NaN)=NaN ->
    //     Math.max(1,NaN)=NaN, then totalCoins = price * NaN = NaN,
    //     and `senderCoins < NaN` is FALSE (any comparison with NaN is
    //     false), so the balance gate passed and we tried to debit NaN
    //     coins. FieldValue.increment(-NaN) is a no-op on the count but
    //     coinsSpent ended up NaN too, corrupting the wallet doc.
    //   - accepted Infinity -> floor(Infinity)=Infinity ->
    //     Math.min(99,Infinity)=99, so this particular path was clamped
    //     to 99 (safe). But the silent normalisation hid the client bug
    //     from observability — nothing logged that the client sent
    //     garbage.
    //   - accepted negative / zero / fractional -> clamped to 1 (safe)
    //     but again silent normalisation hid client bugs.
    //
    // Strict validation: must be a finite, positive integer in
    // [1, MAX_BATCH_COUNT]. Reject 400 with a structured `code` so the
    // Android client can distinguish input-validation failures from
    // generic 400s in metrics.
    const countRaw = body.count;
    let count: number;
    if (countRaw === undefined || countRaw === null) {
      // Backwards-compat: clients that omit `count` get `count=1` for
      // a single-shot gift. This is the most common path.
      count = 1;
    } else if (
      typeof countRaw !== "number" ||
      !Number.isFinite(countRaw) ||
      !Number.isInteger(countRaw) ||
      countRaw < 1 ||
      countRaw > MAX_BATCH_COUNT
    ) {
      throw new HttpError(
        400,
        `count must be an integer between 1 and ${MAX_BATCH_COUNT}.`,
        "INVALID_COUNT"
      );
    } else {
      count = countRaw;
    }

    const gift = findGift(giftId);
    if (!gift) throw new HttpError(404, `Unknown gift: ${giftId}`);

    const totalCoins = gift.priceCoins * count;
    const totalDiamonds = gift.yieldDiamonds * count;

    const db = adminFirestore();
    const senderWalletRef = db.collection("wallets").doc(sender.uid);
    const streamRef = db.collection("streams").doc(streamId);

    const result = await db.runTransaction(async (tx) => {
      const [senderWalletSnap, streamSnap] = await Promise.all([
        tx.get(senderWalletRef),
        tx.get(streamRef),
      ]);

      if (!streamSnap.exists) {
        throw new HttpError(404, "Stream not found");
      }
      const streamData = streamSnap.data() ?? {};
      if (streamData.status !== "live") {
        throw new HttpError(409, "Stream is not live");
      }
      const ownerUid = String(streamData.ownerUid ?? "");
      if (!ownerUid) {
        throw new HttpError(409, "Stream missing ownerUid");
      }
      if (ownerUid === sender.uid) {
        throw new HttpError(403, "Cannot gift your own stream");
      }

      // Yasser Y-H3 — linked-account self-gift block.
      //
      // The simple `ownerUid === sender.uid` guard above only catches
      // the trivial self-gift case where the sender's own Auth account
      // is hosting the stream. It does NOT catch the laundering vector
      // where a user creates a SECOND Auth account (sister phone,
      // second Google), tops it up via the beta wallet grant, and
      // self-gifts from the secondary to the primary to inflate
      // `diamondTotal` on the leaderboard.
      //
      // We close the loophole by reading the sender's user doc inside
      // the txn snapshot and rejecting if `ownerUid` appears in their
      // `linked_accounts` array. The linkage is established by a
      // moderator-or-admin call to
      // `/api/admin/users/{uid}/linked-accounts` (PR-M new endpoint)
      // which writes the array on BOTH sides of the pair atomically,
      // so checking only the sender's side is sufficient. The check
      // is a snapshot READ inside the txn, so a moderator linking the
      // pair after our read but before our commit triggers Firestore
      // optimistic retry and the gift will be re-evaluated against
      // the new linkage — close-race safe.
      const senderUserRef = db.collection("users").doc(sender.uid);
      const senderUserSnap = await tx.get(senderUserRef);
      const senderUserData = senderUserSnap.exists ? senderUserSnap.data() ?? {} : {};
      const linkedRaw = senderUserData.linked_accounts;
      const linkedAccounts: string[] = Array.isArray(linkedRaw)
        ? linkedRaw.filter((v): v is string => typeof v === "string")
        : [];
      if (linkedAccounts.includes(ownerUid)) {
        throw new HttpError(
          403,
          "Cannot gift a linked secondary account.",
          "LINKED_ACCOUNT_SELF_GIFT"
        );
      }

      // Host blocklist check inside the txn snapshot. Passing `tx`
      // here is what actually makes Firestore retry the transaction
      // when the host writes to their blocklist between our read and
      // our commit — a plain `.get()` would silently return a stale
      // snapshot and the gift would land anyway. See
      // `assertNotBlockedFromGifting` for the contract.
      await assertNotBlockedFromGifting(ownerUid, sender.uid, tx);

      // Per-(streamId, senderUid) rate-limit check + counter bump.
      // MUST be called inside the txn before any tx.set so the read
      // participates in the txn snapshot and the increment commits
      // atomically with the wallet debit. Earlier versions ran an
      // aggregate `count()` query outside the txn — 5+ concurrent
      // requests all read count=0 and bypassed the cap. See the
      // module-level docstring on `gift-rate-limit.ts` for the full
      // story.
      //
      // Yasser Y-H1: caps are now UNIT-based; we pass `count` so the
      // counter increments by N units, not by 1. A request with
      // count=50 trips the per-minute cap by itself.
      await assertAndIncrementGiftRate(streamId, sender.uid, count, tx);

      const senderCoins = senderWalletSnap.exists
        ? Number(senderWalletSnap.data()?.coins ?? 0)
        : 0;
      if (senderCoins < totalCoins) {
        throw new HttpError(402, `Insufficient coins: need ${totalCoins}, have ${senderCoins}`);
      }

      const hostWalletRef = db.collection("wallets").doc(ownerUid);
      const giftAuditRef = streamRef.collection("gifts").doc();

      // Sender debit. We use FieldValue.increment so the wallet doc
      // gets the consistent merge even if another concurrent gift
      // arrives between our read and our write — the increment is
      // commutative and the txn re-runs if either read changed.
      tx.set(
        senderWalletRef,
        {
          uid: sender.uid,
          coins: FieldValue.increment(-totalCoins),
          coinsSpent: FieldValue.increment(totalCoins),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      // Host credit.
      tx.set(
        hostWalletRef,
        {
          uid: ownerUid,
          diamonds: FieldValue.increment(totalDiamonds),
          diamondsEarned: FieldValue.increment(totalDiamonds),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      // Stream-level rollup (drives StreamSnapshot.giftTotal).
      tx.set(
        streamRef,
        {
          giftTotal: FieldValue.increment(totalDiamonds),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

      // Audit record. Immutable, never overwritten. Stream-scoped
      // subcollection — drives stream summary UI ("X diamonds in this
      // stream", per-stream leaderboards). The rate-limit module no
      // longer queries this collection; rate state lives in the
      // per-(stream,sender) counter doc at
      // streams/{streamId}/giftRateCounters/{senderUid}.
      tx.set(giftAuditRef, {
        txnId: giftAuditRef.id,
        streamId,
        senderUid: sender.uid,
        receiverUid: ownerUid,
        giftId: gift.id,
        giftName: gift.name,
        giftEmoji: gift.emoji,
        count,
        priceCoins: gift.priceCoins,
        yieldDiamonds: gift.yieldDiamonds,
        totalCoins,
        totalDiamonds,
        createdAt: FieldValue.serverTimestamp(),
      });

      // User-scoped audit entries. The streams/{streamId}/gifts/
      // subcollection above is great for stream-scoped queries, but
      // the staff-facing `GET /api/audit/[uid]` endpoint queries the
      // root `/audit_log` collection by `userId` — gifts were the
      // only money-moving action in the system that DID NOT show up
      // there. A moderator investigating "user X is being harassed
      // via spam-gifting" or "user Y suddenly has 50,000 diamonds"
      // had to drop into the Firebase Console manually because
      // their audit view showed kyc_submit / stream_start /
      // stream_end / wallet_topup but no gifts.
      //
      // We write two entries per gift so each side can be queried by
      // `userId == uid` in a single Firestore index hit (the existing
      // composite index on `audit_log (userId ASC, timestamp DESC)`
      // already covers this). The metadata mirrors the per-stream
      // record above but flat enough to render in the audit view
      // without joining back to streams/{streamId}/gifts.
      const sendAuditRef = db.collection("audit_log").doc();
      tx.set(sendAuditRef, {
        userId: sender.uid,
        action: "gift_send",
        timestamp: new Date().toISOString(),
        metadata: {
          streamId,
          giftId: gift.id,
          giftName: gift.name,
          receiverUid: ownerUid,
          count,
          totalCoins,
          totalDiamonds,
          txnId: giftAuditRef.id,
        },
      });
      const receiveAuditRef = db.collection("audit_log").doc();
      tx.set(receiveAuditRef, {
        userId: ownerUid,
        action: "gift_receive",
        timestamp: new Date().toISOString(),
        metadata: {
          streamId,
          giftId: gift.id,
          giftName: gift.name,
          senderUid: sender.uid,
          count,
          totalCoins,
          totalDiamonds,
          txnId: giftAuditRef.id,
        },
      });

      return {
        txnId: giftAuditRef.id,
        // Echo the post-txn balance the client should display. Reading
        // it back inside the txn would force an extra round trip; the
        // increment is deterministic so we compute it locally.
        balance: {
          coins: senderCoins - totalCoins,
          diamonds: 0, // sender is the giver, not the receiver
        },
      };
    });

    return asJson(200, {
      ok: true,
      txnId: result.txnId,
      balance: result.balance,
      gift: { id: gift.id, name: gift.name, emoji: gift.emoji },
      total: { coins: totalCoins, diamonds: totalDiamonds, count },
    });
  } catch (e) {
    return asError(e);
  }
}
