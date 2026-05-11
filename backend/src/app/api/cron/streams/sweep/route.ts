import type { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Stale-stream watchdog. KHALID-004.
 *
 * The stream lifecycle has three end paths:
 *   1. Publisher tap → POST /api/streams/end → status='ended'.
 *   2. LiveKit empty-room timeout → webhook `room_finished` → status='ended'.
 *   3. (NEW) This sweep, for orphans where (1) never fired and (2) failed
 *      to deliver (LiveKit webhook timeouts, brief Vercel downtime, signed
 *      webhook signature drift, etc.).
 *
 * Without (3), a publisher who force-quits / loses network can leave a
 * stream `status='live'` indefinitely in Firestore. The discovery feed
 * surfaces it, the gift route still accepts gifts to it, senders waste
 * coins on a dead room.
 *
 * Force-end criteria (any one triggers):
 *   - `lastWebhookAt` older than STALE_THRESHOLD_MS. After the webhook
 *     handler was widened (see `api/livekit/webhook` top-of-route
 *     stamp), a healthy LiveKit room stamps this on every event type
 *     the room produces — `room_started`, `participant_*`,
 *     `track_*`, `egress_*`, `ingress_*`. The threshold is 60 minutes
 *     of total silence — the original 15-minute window was reaping
 *     legitimate solo broadcasts because LiveKit emits no periodic
 *     heartbeats, so a publisher with zero viewers and no track
 *     changes produced no webhook stamps for the full broadcast.
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
 * Vercel Cron schedule lives in vercel.json. The endpoint accepts
 * either GET (Vercel's default) or POST so manual `curl -X POST` from
 * staff with the CRON_SECRET still works for ad-hoc sweeps.
 *
 * Protection: CRON_SECRET env var. Vercel Cron automatically sends
 * `Authorization: Bearer ${CRON_SECRET}` when the env var is set. The
 * endpoint hard-fails if the env var is missing — better to break the
 * cron than to leave the force-end endpoint world-callable.
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

async function handle(req: NextRequest) {
  try {
    const expected = process.env.CRON_SECRET;
    if (!expected) {
      // Misconfiguration. Refuse to run so the force-end endpoint isn't
      // world-callable. Vercel Cron will retry on the next tick.
      throw new HttpError(503, "CRON_SECRET not configured.");
    }
    const auth = req.headers.get("authorization") || "";
    if (auth !== `Bearer ${expected}`) {
      throw new HttpError(401, "Unauthorized.");
    }

    const db = adminFirestore();
    const now = Date.now();

    // Snapshot of every still-`live` stream. This collection is small
    // in closed beta (≤ active broadcaster count). When it grows past
    // hundreds we'll need a paginated sweep + index; for closed beta
    // a single full scan every 5 minutes is well within Firestore's
    // free-tier read budget.
    const snap = await db
      .collection("streams")
      .where("status", "==", "live")
      .get();

    const ended: Array<{ streamId: string; ageMs: number; idleMs: number }> = [];

    for (const doc of snap.docs) {
      const data = doc.data();
      const startTimeMs = parseTime(data.startTime);
      const lastWebhookMs = parseTime(data.lastWebhookAt);

      // "idle" = how long since LiveKit last said anything about this
      // room. If no webhook ever fired, fall back to startTime so the
      // "publisher started, then crashed before any viewer joined"
      // case is still caught.
      const lastSignalMs = !Number.isNaN(lastWebhookMs) ? lastWebhookMs : startTimeMs;
      const idleMs = !Number.isNaN(lastSignalMs) ? now - lastSignalMs : 0;
      const ageMs = !Number.isNaN(startTimeMs) ? now - startTimeMs : 0;

      const stale = idleMs > STALE_THRESHOLD_MS || ageMs > HARD_MAX_AGE_MS;
      if (!stale) continue;

      const streamId = doc.id;
      const ref = db.collection("streams").doc(streamId);

      // Re-read inside the txn so we don't race with the publisher's
      // own `/api/streams/end` press or a late `room_finished` webhook.
      // Either of those wins, and we observe `status !== 'live'` on
      // the inside read and back out without another mutation. This
      // is the same idempotency-on-status pattern used by streams/end
      // and the room_finished webhook branch, so all four lifecycle
      // paths agree: "ended means ended — no late mutations".
      await db.runTransaction(async (tx) => {
        const fresh = await tx.get(ref);
        if (!fresh.exists) return;
        const fd = fresh.data() ?? {};
        if (fd.status !== "live") return;

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

    return asJson(200, {
      ok: true,
      checked: snap.size,
      endedCount: ended.length,
      ended,
    });
  } catch (err) {
    return asError(err);
  }
}

export async function GET(req: NextRequest) {
  return handle(req);
}

export async function POST(req: NextRequest) {
  return handle(req);
}
