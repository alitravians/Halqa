import { Timestamp, type Transaction } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { HttpError } from "@/lib/auth";

/**
 * Gift-bombing rate limits — Mohammed Al-Qahtani (Stream Moderation Lead)
 * council session: c6e9353660d84045b075478745a3c35f
 *
 * Mohammed's spec for closed-beta abuse mitigation:
 *   - 5 gifts/sender/stream/60s   — guard against rapid-fire spam in a single stream
 *   - 60 gifts/sender/stream/hour — guard against sustained bombing
 *   - host blocklist              — host can ban a specific sender from gifting them
 *
 * The cross-stream "max 10 distinct recipients/sender/hour" rule from
 * Mohammed's spec requires a Firestore composite index on the
 * `gifts` collectionGroup. Deferred to v0.1.15 (M4) so the closed-beta
 * cohort (50 testers) is not blocked on infra setup. Per-stream limits
 * + blocklist cover the realistic abuse vectors at this size.
 */

const RATE_LIMIT_PER_60S = 5;
const RATE_LIMIT_PER_HOUR = 60;

export async function assertGiftRateOk(
  streamId: string,
  senderUid: string
): Promise<void> {
  const db = adminFirestore();
  const giftsRef = db.collection("streams").doc(streamId).collection("gifts");
  const now = Date.now();
  const oneMinuteAgo = Timestamp.fromMillis(now - 60_000);
  const oneHourAgo = Timestamp.fromMillis(now - 3_600_000);

  const [last60s, lastHour] = await Promise.all([
    giftsRef
      .where("senderUid", "==", senderUid)
      .where("createdAt", ">=", oneMinuteAgo)
      .count()
      .get(),
    giftsRef
      .where("senderUid", "==", senderUid)
      .where("createdAt", ">=", oneHourAgo)
      .count()
      .get(),
  ]);

  if (last60s.data().count >= RATE_LIMIT_PER_60S) {
    throw new HttpError(
      429,
      `هدّئ السرعة — حد ${RATE_LIMIT_PER_60S} هدايا في الدقيقة الواحدة لهذا البث.`
    );
  }
  if (lastHour.data().count >= RATE_LIMIT_PER_HOUR) {
    throw new HttpError(
      429,
      `وصلت الحد الأقصى ${RATE_LIMIT_PER_HOUR} هدية في الساعة لهذا البث.`
    );
  }
}

/**
 * Host-managed blocklist: writes at users/{hostUid}/giftBlocklist/{senderUid}
 * Any doc presence (regardless of contents) blocks the sender from gifting
 * the host. Hosts manage this from their stream UI (Phase C).
 *
 * Transaction support: when [tx] is supplied, the blocklist read participates
 * in the caller's Firestore transaction snapshot. This is what makes the
 * "host blocks the sender after the read but before the write" race actually
 * close — without it, a vanilla `.get()` would return a stale snapshot that
 * Firestore's commit step has no way to invalidate, and the gift would still
 * land. Callers inside `runTransaction(...)` MUST pass the transaction.
 */
export async function assertNotBlockedFromGifting(
  hostUid: string,
  senderUid: string,
  tx?: Transaction
): Promise<void> {
  const ref = adminFirestore()
    .collection("users")
    .doc(hostUid)
    .collection("giftBlocklist")
    .doc(senderUid);
  const blockSnap = tx ? await tx.get(ref) : await ref.get();

  if (blockSnap.exists) {
    throw new HttpError(
      403,
      "هذا المضيف منعك من إرسال الهدايا."
    );
  }
}
