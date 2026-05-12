import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions/v2";
import { adminFirestore } from "./firebase-admin";

/**
 * Stale-stream watchdog — KHALID PR-N (Firebase Scheduled Function rebuild).
 *
 * MIGRATION CONTEXT
 * -----------------
 * This function is a 1:1 port of the prior Vercel cron route at
 * `backend/src/app/api/cron/streams/sweep/route.ts`. The Vercel cron was
 * removed in PR #114 because Vercel Hobby plan caps cron jobs at one run
 * per day, while the watchdog must run every 5 minutes for stream-end
 * correctness and leaderboard fairness. Firebase Scheduled Functions can
 * run every minute on the free tier without plan-imposed throttling.
 *
 * The Vercel route remains at the same URL as a 410 Gone stub so any
 * stale external caller (Vercel internal cron retry, manual curl from
 * staff with CRON_SECRET, etc.) is told explicitly that the endpoint
 * has moved rather than silently 404-ing.
 *
 *
 * WATCHDOG LOGIC (preserved verbatim from the Vercel route)
 * ---------------------------------------------------------
 * The stream lifecycle has three end paths:
 *   1. Publisher tap → POST /api/streams/end → status='ended'.
 *   2. LiveKit empty-room timeout → webhook `room_finished` → status='ended'.
 *   3. (This sweep) for orphans where (1) never fired and (2) failed to
 *      deliver (LiveKit webhook timeouts, brief platform downtime, signed
 *      webhook signature drift, etc.).
 *
 * Without (3), a publisher who force-quits / loses network can leave a
 * stream `status='live'` indefinitely in Firestore. The discovery feed
 * surfaces it, the gift route still accepts gifts to it, senders waste
 * coins on a dead room.
 *
 * Force-end criteria (any one triggers):
 *   - `lastWebhookAt` older than STALE_THRESHOLD_MS. After the webhook
 *     handler was widened, a healthy LiveKit room stamps this on every
 *     event type the room produces — `room_started`, `participant_*`,
 *     `track_*`, `egress_*`, `ingress_*`. The threshold is 60 minutes
 *     of total silence — the original 15-minute window was reaping
 *     legitimate solo broadcasts (PR #96) because LiveKit emits no
 *     periodic heartbeats, so a publisher with zero viewers and no
 *     track changes produced no webhook stamps for the full broadcast.
 *   - No `lastWebhookAt` ever stamped AND `startTime` older than
 *     STALE_THRESHOLD_MS. This catches the "started, never had a viewer,
 *     publisher crashed" case where no webhook ever lands.
 *   - `startTime` older than HARD_MAX_AGE_MS regardless of webhook
 *     activity. No legitimate beta broadcast lasts 6 hours; anything
 *     above this is almost certainly stuck.
 *
 * The end-stream transaction mirrors the room_finished branch in
 * livekit/webhook so the three paths leave Firestore in identical
 * shape (status=ended, viewerCount=0, endTime stamped, audit row in
 * /audit_log with endedBy distinguishing the path). The audit row's
 * `endedBy: "watchdog_sweep"` lets Trust & Safety distinguish a
 * publisher-initiated end from a webhook-initiated end from a sweep-
 * initiated end during incident review.
 *
 * KHALID-R2-001 stale-recheck (PR #94) is preserved: the txn re-derives
 * idle/age from the txn snapshot, so a reconnecting publisher between
 * the outer scan and the txn commit is NOT reaped.
 *
 *
 * AUTH / PROTECTION
 * -----------------
 * Scheduled Functions are invoked by Cloud Scheduler with platform-
 * managed credentials; there is no externally-callable URL. The
 * `CRON_SECRET` env var the Vercel route required is therefore not
 * needed here — Cloud Scheduler → Pub/Sub → Function is internal to
 * the GCP project.
 *
 *
 * REGION
 * ------
 * Pinned to `asia-south1` to co-locate with the Halqa Firestore
 * multi-region (asia-south1 — Mumbai). Cloud Functions and Firestore in
 * the same region minimise read/write latency for the sweep and avoid
 * cross-region egress charges. Change here if Firestore is ever
 * migrated.
 */

const STALE_THRESHOLD_MS = 60 * 60 * 1000; // 60 minutes
const HARD_MAX_AGE_MS = 6 * 60 * 60 * 1000; // 6 hours

function parseTime(v: unknown): number {
  if (typeof v === "string") {
    const t = Date.parse(v);
    return Number.isNaN(t) ? NaN : t;
  }
  if (v && typeof (v as { toMillis?: () => number }).toMillis === "function") {
    return (v as { toMillis: () => number }).toMillis();
  }
  return NaN;
}

interface SweepResult {
  checked: number;
  endedCount: number;
  ended: Array<{ streamId: string; ageMs: number; idleMs: number }>;
}

export async function runStreamSweep(): Promise<SweepResult> {
  const db = adminFirestore();
  const now = Date.now();

  // Snapshot of every still-`live` stream. This collection is small in
  // closed beta (≤ active broadcaster count). When it grows past
  // hundreds we'll need a paginated sweep + index; for closed beta a
  // single full scan every 5 minutes is well within Firestore's free-
  // tier read budget.
  const snap = await db.collection("streams").where("status", "==", "live").get();

  const ended: SweepResult["ended"] = [];

  for (const doc of snap.docs) {
    const data = doc.data();
    const startTimeMs = parseTime(data.startTime);
    const lastWebhookMs = parseTime(data.lastWebhookAt);

    // "idle" = how long since LiveKit last said anything about this
    // room. If no webhook ever fired, fall back to startTime so the
    // "publisher started, then crashed before any viewer joined" case
    // is still caught.
    const lastSignalMs = !Number.isNaN(lastWebhookMs) ? lastWebhookMs : startTimeMs;
    const idleMs = !Number.isNaN(lastSignalMs) ? now - lastSignalMs : 0;
    const ageMs = !Number.isNaN(startTimeMs) ? now - startTimeMs : 0;

    const stale = idleMs > STALE_THRESHOLD_MS || ageMs > HARD_MAX_AGE_MS;
    if (!stale) continue;

    const streamId = doc.id;
    const ref = db.collection("streams").doc(streamId);

    // Re-read inside the txn so we don't race with the publisher's own
    // `/api/streams/end` press or a late `room_finished` webhook. Either
    // of those wins, and we observe `status !== 'live'` on the inside
    // read and back out without another mutation. This is the same
    // idempotency-on-status pattern used by streams/end and the
    // room_finished webhook branch, so all four lifecycle paths agree:
    // "ended means ended — no late mutations".
    //
    // KHALID-R2-001 (PR #94) — re-check staleness inside the txn, NOT
    // just status. Without the inner staleness check, this race ends a
    // legitimate stream:
    //
    //   t=0:00   outer scan reads lastWebhookAt = 14m old (stale).
    //   t=0:02   publisher's app reconnects, LiveKit fires
    //            participant_joined; webhook handler updates
    //            lastWebhookAt = now (fresh).
    //   t=0:03   txn fires; inner read shows status="live" (still
    //            true) so the idempotency gate passes, even though
    //            lastWebhookAt is now 1s old, not 14m. Old code
    //            force-ends the freshly-resumed stream.
    //
    // The inner predicate re-derives idle/age from the txn snapshot
    // (committed view) so the decision to end uses the SAME data the
    // commit replaces. If the stream reconnected between the outer
    // scan and the txn, the txn snapshot's lastWebhookAt is already
    // fresh and the predicate short-circuits — we observe it via
    // Firestore's optimistic concurrency, which retries the txn when
    // the doc was touched between read and commit.
    await db.runTransaction(async (tx) => {
      const fresh = await tx.get(ref);
      if (!fresh.exists) return;
      const fd = fresh.data() ?? {};
      if (fd.status !== "live") return;

      const freshStartMs = parseTime(fd.startTime);
      const freshWebhookMs = parseTime(fd.lastWebhookAt);
      const freshLastSignalMs = !Number.isNaN(freshWebhookMs)
        ? freshWebhookMs
        : freshStartMs;
      const freshIdleMs = !Number.isNaN(freshLastSignalMs)
        ? Date.now() - freshLastSignalMs
        : 0;
      const freshAgeMs = !Number.isNaN(freshStartMs) ? Date.now() - freshStartMs : 0;
      const stillStale =
        freshIdleMs > STALE_THRESHOLD_MS || freshAgeMs > HARD_MAX_AGE_MS;
      if (!stillStale) return;

      const endTime = new Date().toISOString();
      tx.update(ref, {
        status: "ended",
        endTime,
        viewerCount: 0,
        lastWebhookAt: endTime,
      });

      const auditRef = db.collection("audit_log").doc();
      tx.set(auditRef, {
        userId: fd.ownerUid ?? null,
        action: "stream_end",
        timestamp: endTime,
        metadata: {
          streamId,
          endedBy: "watchdog_sweep",
          startTime: fd.startTime ?? null,
          lastWebhookAt: fd.lastWebhookAt ?? null,
          staleIdleMs: idleMs,
          staleAgeMs: ageMs,
        },
      });
    });

    ended.push({ streamId, ageMs, idleMs });
  }

  return { checked: snap.size, endedCount: ended.length, ended };
}

export const streamSweep = onSchedule(
  {
    schedule: "every 5 minutes",
    region: "asia-south1",
    timeoutSeconds: 540,
    memory: "256MiB",
    retryCount: 0,
  },
  async () => {
    try {
      const result = await runStreamSweep();
      logger.info("stream_sweep_done", result);
    } catch (err) {
      logger.error("stream_sweep_failed", {
        message: err instanceof Error ? err.message : String(err),
        stack: err instanceof Error ? err.stack : undefined,
      });
      throw err;
    }
  },
);
