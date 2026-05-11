import { Timestamp, type Transaction } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { HttpError } from "@/lib/auth";

/**
 * Gift-bombing rate limits — Mohammed Al-Qahtani (Stream Moderation Lead)
 * council session: c6e9353660d84045b075478745a3c35f
 *
 * Mohammed's original spec for closed-beta abuse mitigation:
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
 * --- Yasser Round-2 HIGH (Y-H1): unit-based caps ---
 *
 * The original implementation counted REQUESTS, not gift units. A
 * sender could send one request with `count=99` and trip the rate-
 * limit counter by exactly 1, while actually delivering 99 gifts'
 * worth of visual/audible spam plus 99× the coin debit. A whale
 * batching `count=99` could bypass the per-minute cap by ~99×.
 *
 * Caps now express gift UNITS per window (a request with `count=N`
 * costs N units). Defaults preserve roughly the original burst budget
 * for count=1 senders but cap the worst-case unit throughput:
 *
 *   GIFT_UNITS_PER_MIN_PER_SENDER  default 50   (was 5 requests)
 *   GIFT_UNITS_PER_HOUR_PER_SENDER default 600  (was 60 requests)
 *
 * Both are env-configurable for production tuning without redeploy.
 * On rejection we report the user-visible cap so the client can
 * tell the sender exactly how big the limit is.
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

/**
 * Maximum number of gift units carried by a single `/api/gifts/send`
 * request. This is the FIRST gate (route-handler-level) — strict input
 * validation. It bounds the per-request cost and prevents one request
 * from blowing up the txn (Firestore txn size limits, audit_log row
 * sprawl, single-request whale annihilation). Per-window caps are
 * applied separately inside `assertAndIncrementGiftRate`.
 */
export const MAX_BATCH_COUNT = 99;

function envPositiveInt(name: string, fallback: number): number {
  const raw = process.env[name];
  if (typeof raw !== "string" || raw.trim().length === 0) return fallback;
  const n = Number.parseInt(raw, 10);
  if (!Number.isFinite(n) || n <= 0) return fallback;
  return n;
}

const RATE_UNITS_PER_60S = envPositiveInt("GIFT_UNITS_PER_MIN_PER_SENDER", 50);
const RATE_UNITS_PER_HOUR = envPositiveInt("GIFT_UNITS_PER_HOUR_PER_SENDER", 600);
const WINDOW_60S_MS = 60_000;
const WINDOW_HOUR_MS = 3_600_000;

interface RateCounterDoc {
  // Stored as cumulative UNIT counts (gift units sent in the current
  // window). Field names kept verbatim for backward compat with docs
  // written before the unit-based switch — a sender whose old doc has
  // count=4 simply rolls forward as 4 units (close enough; under-
  // counting old activity in transition is safer than over-counting).
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
 * Yasser Y-H1: caps are measured in gift UNITS (sum of `count` across
 * recent requests), not in request count. A request that delivers N
 * gifts costs N units. The caller passes the request's `units` so the
 * counter can `current + units > cap` correctly. (A request with
 * count=1 still costs 1 unit, preserving prior single-shot behaviour.)
 *
 * Contract:
 *   - MUST be called from inside `db.runTransaction(...)`.
 *   - MUST be called BEFORE the first `tx.set` in that transaction
 *     (Firestore requires all reads before any writes inside a txn).
 *   - The counter `tx.set` issued at the end of this function counts
 *     as a write, so the caller's wallet/stream/audit `tx.set` calls
 *     must come AFTER this function returns.
 *   - `units` MUST be a finite positive integer ≤ MAX_BATCH_COUNT.
 *     Callers are expected to have validated this BEFORE entering the
 *     txn (so we don't waste a Firestore read on garbage input). We
 *     defensively assert anyway because a route bug that lets NaN
 *     through would otherwise silently store NaN in the counter doc
 *     and permanently jam the sender.
 *
 * On rate limit hit: throws `HttpError(429, …, code="RATE_LIMITED")`.
 * The thrown error rolls the entire txn back, so the counter increment
 * does NOT persist for rejected attempts — only successful gifts count
 * toward the cap. (This intentionally mirrors the old aggregate-query
 * behaviour where only committed gift docs in /streams/{id}/gifts/
 * contributed to the count.)
 */
export async function assertAndIncrementGiftRate(
  streamId: string,
  senderUid: string,
  units: number,
  tx: Transaction
): Promise<void> {
  if (!Number.isInteger(units) || units < 1 || units > MAX_BATCH_COUNT) {
    // Defence-in-depth — route MUST have rejected this before calling
    // us. Throw as 500 because by the time we get here it's a server
    // contract violation, not a client-correctable error.
    throw new HttpError(500, "Invalid rate-limit unit count.");
  }

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

  if (secCount + units > RATE_UNITS_PER_60S) {
    throw new HttpError(
      429,
      `هدّئ السرعة — حد ${RATE_UNITS_PER_60S} وحدة هدية في الدقيقة لهذا البث.`,
      "RATE_LIMITED"
    );
  }

  // 1-hour sliding window.
  let hourCount = typeof data.windowHourCount === "number" ? data.windowHourCount : 0;
  let hourResetAtMs = data.windowHourResetAt?.toMillis() ?? 0;
  if (hourResetAtMs <= now) {
    hourCount = 0;
    hourResetAtMs = now + WINDOW_HOUR_MS;
  }

  if (hourCount + units > RATE_UNITS_PER_HOUR) {
    throw new HttpError(
      429,
      `وصلت الحد الأقصى ${RATE_UNITS_PER_HOUR} وحدة هدية في الساعة لهذا البث.`,
      "RATE_LIMITED"
    );
  }

  // Increment + write back inside the same txn snapshot. If the
  // surrounding txn aborts (insufficient coins, host blocklist, write
  // contention), this set is rolled back — failed attempts do NOT
  // count toward the cap, matching the old query-the-audit-log
  // semantics.
  tx.set(counterRef, {
    senderUid,
    windowSecCount: secCount + units,
    windowSecResetAt: Timestamp.fromMillis(secResetAtMs),
    windowHourCount: hourCount + units,
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
