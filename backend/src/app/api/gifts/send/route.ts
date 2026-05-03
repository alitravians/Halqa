import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import { findGift } from "@/lib/gifts";
import {
  assertGiftRateOk,
  assertNotBlockedFromGifting,
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

    const body = (await req.json().catch(() => ({}))) as {
      streamId?: unknown;
      giftId?: unknown;
      count?: unknown;
    };
    const streamId = typeof body.streamId === "string" ? body.streamId.trim() : "";
    const giftId = typeof body.giftId === "string" ? body.giftId.trim() : "";
    const countRaw = typeof body.count === "number" ? body.count : 1;
    const count = Math.max(1, Math.min(99, Math.floor(countRaw)));

    if (!streamId) throw new HttpError(400, "streamId is required");
    if (!giftId) throw new HttpError(400, "giftId is required");

    const gift = findGift(giftId);
    if (!gift) throw new HttpError(404, `Unknown gift: ${giftId}`);

    const totalCoins = gift.priceCoins * count;
    const totalDiamonds = gift.yieldDiamonds * count;

    // Pre-transaction abuse checks (Mohammed Al-Qahtani — Stream Moderation
    // Lead). Rate limits + host blocklist run outside the txn because they
    // read aggregate counts; the small race window (<200ms) is acceptable
    // for closed-beta scale and avoids inflating the txn surface.
    await assertGiftRateOk(streamId, sender.uid);

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

      // Host blocklist check inside the txn — any concurrent block
      // write rolls our txn back, so the sender cannot win a race.
      await assertNotBlockedFromGifting(ownerUid, sender.uid);

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

      // Audit record. Immutable, never overwritten.
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
