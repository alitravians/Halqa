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
 *
 * --- Design note: counter doc, not aggregate query ---
 *
 * Earlier versions of this module exposed an `assertGiftRateOk(streamId,
 * senderUid)` helper that ran a `count()` aggregation over
 * `streams/{streamId}/gifts/` filtered by `senderUid` + `createdAt`.
 * That helper executed OUTSIDE the gift transaction and returned a stale
 * snapshot under concurrency: 5+ requests fired in the same ~200ms
 * window all read `count = 0`, all passed the assertion, all entered the
 * txn body in parallel, all committed — the per-(stream,sender) caps
 * were silently bypassed. With closed-beta scale (50 testers) the
 * realistic exploitation surface was small, but at any non-trivial
 * scale a malicious sender could send arbitrary gifts/second by
 * pipelining requests and the moderation contract Mohammed signed off
 * on would be a lie.
 *
 * The fix is to maintain a per-(streamId, senderUid) counter doc at
 * `streams/{streamId}/giftRateCounters/{senderUid}` and read+update it
 * inside the same Firestore transaction as the wallet debit. Two
 * sliding windows (60s + 1h) are stored as `count` + `resetAt` fields
 * each; when `resetAt` is in the past we reset the count to zero and
 * push the resetAt forward by the window length. Concurrent txns that
 * both touch the same counter doc trigger Firestore's optimistic
 * concurrency retry — exactly what we want, and the same mechanism the
 * wallet debit relies on.
 *
 * Cleanup: counter docs accumulate per (stream, sender). For a stream
 * with N unique senders this is N docs. We do not delete them on
 * stream-end — Firestore reads are cheap, the rule blocks all client
 * access, and they age out naturally when a follow-up gift in the same
 * stream resets the windows. If/when stream-archival GC ships, sweep
 * `streams/{streamId}/giftRateCounters/*` alongside the gifts
 * subcollection.
 */

const RATE_LIMIT_PER_60S = 5;
const RATE_LIMIT_PER_HOUR = 60;
const WINDOW_60S_MS = 60_000;
const WINDOW_HOUR_MS = 3_600_000;

interface RateCounterDoc {
  windowSecCount?: number;
  windowSecResetAt?: Timestamp;
  windowHourCount?: number;
  windowHourResetAt?: Timestamp;
  updatedAt?: Timestamp;
}

/**
 * Assert the sender is within the per-stream gift rate limits and bump
 * the counter — atomically, inside the caller's gift transaction.
 *
 * Contract:
 *   - MUST be called from inside `db.runTransaction(...)`.
 *   - MUST be called BEFORE the first `tx.set` in that transaction
 *     (Firestore requires all reads before any writes inside a txn).
 *   - The counter `tx.set` issued at the end of this function counts
 *     as a write, so the caller's wallet/stream/audit `tx.set` calls
 *     must come AFTER this function returns.
 *
 * On rate limit hit: throws `HttpError(429, …)` with an Arabic message.
 * The thrown error rolls the entire txn back, so the counter increment
 * does NOT persist for rejected attempts — only successful gifts count
 * toward the cap. (This intentionally mirrors the old aggregate-query
 * behaviour where only committed gift docs in /streams/{id}/gifts/
 * contributed to the count.)
 */
export async function assertAndIncrementGiftRate(
  streamId: string,
  senderUid: string,
  tx: Transaction
): Promise<void> {
  const db = adminFirestore();
  const counterRef = db
    .collection("streams")
    .doc(streamId)
    .collection("giftRateCounters")
    .doc(senderUid);

  const snap = await tx.get(counterRef);
  const data = (snap.exists ? snap.data() : {}) as RateCounterDoc;

  const now = Date.now();
  const nowTs = Timestamp.fromMillis(now);

  // 60-second sliding window.
  let secCount = typeof data.windowSecCount === "number" ? data.windowSecCount : 0;
  let secResetAtMs = data.windowSecResetAt?.toMillis() ?? 0;
  if (secResetAtMs <= now) {
    secCount = 0;
    secResetAtMs = now + WINDOW_60S_MS;
  }

  if (secCount >= RATE_LIMIT_PER_60S) {
    throw new HttpError(
      429,
      `هدّئ السرعة — حد ${RATE_LIMIT_PER_60S} هدايا في الدقيقة الواحدة لهذا البث.`
    );
  }

  // 1-hour sliding window.
  let hourCount = typeof data.windowHourCount === "number" ? data.windowHourCount : 0;
  let hourResetAtMs = data.windowHourResetAt?.toMillis() ?? 0;
  if (hourResetAtMs <= now) {
    hourCount = 0;
    hourResetAtMs = now + WINDOW_HOUR_MS;
  }

  if (hourCount >= RATE_LIMIT_PER_HOUR) {
    throw new HttpError(
      429,
      `وصلت الحد الأقصى ${RATE_LIMIT_PER_HOUR} هدية في الساعة لهذا البث.`
    );
  }

  // Increment + write back inside the same txn snapshot. If the
  // surrounding txn aborts (insufficient coins, host blocklist, write
  // contention), this set is rolled back — failed attempts do NOT
  // count toward the cap, matching the old query-the-audit-log
  // semantics.
  tx.set(counterRef, {
    senderUid,
    windowSecCount: secCount + 1,
    windowSecResetAt: Timestamp.fromMillis(secResetAtMs),
    windowHourCount: hourCount + 1,
    windowHourResetAt: Timestamp.fromMillis(hourResetAtMs),
    updatedAt: nowTs,
  });
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
