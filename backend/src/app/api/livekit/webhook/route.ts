import { NextRequest } from "next/server";
import { WebhookReceiver } from "livekit-server-sdk";
import {
  FieldValue,
  type DocumentReference,
  type UpdateData,
} from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * LiveKit → Halqa webhook receiver.
 *
 * LiveKit Cloud is configured to POST signed webhook events to this
 * route every time a participant joins, leaves, a room starts/ends, or
 * a track is (un)published. We use those events to maintain an
 * authoritative `viewerCount` on each `streams/{streamId}` Firestore
 * document — without it, the broadcaster client would see whatever
 * stale participant set LiveKit's signalling layer happens to report,
 * which manifested in v0.1.11 as the "60 fake bots" Ali observed.
 *
 * Behaviour:
 *   - `participant_joined` ↔ +1, but only if the participant is NOT
 *     the publisher (publisher identity == the prefix of the room
 *     name, see `u_<uid>_*` convention enforced in `livekit/token`).
 *   - `participant_left`   ↔ -1 (clamped at 0 with `Math.max`).
 *   - `room_started`       ↔ ensures `viewerCount` is initialised to 0.
 *   - `room_finished`      ↔ marks the stream `ended` + zeroes the count.
 *
 * Configuration (one-time, in LiveKit Cloud → Project Settings →
 * Webhooks): set the URL to
 *   https://halqa-backend.vercel.app/api/livekit/webhook
 * and use the same API key/secret as the AccessToken signer. Auth is
 * enforced by `WebhookReceiver` via the `Authorize` header that LiveKit
 * signs with the API secret.
 */
export async function POST(req: NextRequest) {
  try {
    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    if (!apiKey || !apiSecret) {
      throw new HttpError(500, "LiveKit env vars are not configured.");
    }
    const authHeader = req.headers.get("Authorize") ?? req.headers.get("authorization") ?? "";
    const body = await req.text();
    const receiver = new WebhookReceiver(apiKey, apiSecret);
    const event = await receiver.receive(body, authHeader);

    const roomName = event.room?.name ?? "";
    if (!roomName) {
      return asJson(200, { ok: true, ignored: "no_room_name" });
    }
    const db = adminFirestore();
    const ref = db.collection("streams").doc(roomName);

    // Publisher identity convention: room is `u_<publisherUid>_<...>`.
    // Extract `<publisherUid>` so we can ignore the publisher's own
    // join/leave events when adjusting the viewer count.
    const publisherUid = extractPublisherUid(roomName);

    switch (event.event) {
      case "room_started": {
        // Track the event timestamp for observability. Do NOT touch
        // viewerCount — the doc was already created by `livekit/token`
        // with viewerCount=0, and LiveKit Cloud does NOT guarantee
        // ordered delivery of webhooks. A `room_started` arriving
        // after an early `participant_joined` (e.g. when the publisher
        // and the first viewer connect within the same Vercel cold-
        // start window) used to clobber the increment back to 0,
        // making the broadcaster see a stuck "0 watching" overlay
        // even though the audience was real.
        await tryWebhookUpdate(ref, {
          lastWebhookAt: new Date().toISOString(),
        });
        break;
      }

      case "room_finished": {
        // Doc *should* exist (token route created it); if it doesn't,
        // the stream was never properly started and there's nothing
        // for us to flip to "ended". Refusing to upsert here prevents
        // partial-doc creation that the Android client treats as a
        // broken stream (no ownerUid → WatchSession aborts).
        //
        // Three invariants enforced inside one transaction:
        //
        //   1. Idempotency. If the stream is already `status:"ended"`
        //      (because the publisher hit POST /api/streams/end first
        //      or because a previous `room_finished` already arrived),
        //      we MUST NOT overwrite `endTime`. Re-running the update
        //      from the unconditional path bumped endTime to the
        //      *webhook* timestamp, which can be 5+ minutes after the
        //      real publisher-initiated end (LiveKit waits for the
        //      empty-room timeout before firing room_finished),
        //      silently corrupting the duration analytics. The check
        //      below is the same idempotency pattern POST /streams/end
        //      uses (#32) so the two paths agree on which timestamp wins.
        //
        //   2. Audit completeness. Every stream lifecycle end must
        //      land an `audit_log` entry. The publisher path covers
        //      itself in /api/streams/end. The webhook-driven path
        //      (publisher app crashed, lost network, force-quit) had
        //      NO audit_log entry — Trust & Safety could see a stream
        //      that started and never ended. We now write one with
        //      `endedBy: "livekit_room_finished"` so investigators
        //      can distinguish webhook-completion from user-initiated.
        //
        //   3. Partial-doc race. NOT_FOUND from the read still drops
        //      the event silently (LiveKit beat /livekit/token in a
        //      cold-start race) — same trade-off as the unconditional
        //      path used to make.
        await db.runTransaction(async (tx) => {
          const snap = await tx.get(ref);
          if (!snap.exists) return;
          const data = snap.data() ?? {};
          if (data.status === "ended") {
            // Already ended (publisher path or earlier webhook).
            // Just mark observability and return — keep original
            // endTime intact.
            tx.update(ref, { lastWebhookAt: new Date().toISOString() });
            return;
          }
          const endTime = new Date().toISOString();
          tx.update(ref, {
            status: "ended",
            endTime,
            viewerCount: 0,
            lastWebhookAt: endTime,
          });
          const auditRef = db.collection("audit_log").doc();
          tx.set(auditRef, {
            userId: data.ownerUid ?? null,
            action: "stream_end",
            timestamp: endTime,
            metadata: {
              streamId: roomName,
              endedBy: "livekit_room_finished",
              startTime: data.startTime ?? null,
            },
          });
        });
        break;
      }

      case "participant_joined": {
        const identity = event.participant?.identity ?? "";
        if (identity === publisherUid) {
          // Publisher's own join — don't count as a viewer.
          break;
        }
        await tryWebhookUpdate(ref, {
          viewerCount: FieldValue.increment(1),
          lastWebhookAt: new Date().toISOString(),
        });
        break;
      }

      case "participant_left": {
        const identity = event.participant?.identity ?? "";
        if (identity === publisherUid) {
          break;
        }
        // Use a transaction so we can clamp at 0 — viewerCount must
        // never go negative even if LiveKit replays a stale `_left`.
        // We also bail if the doc doesn't exist (out-of-order replay
        // arriving after `room_finished` already cleared it).
        await db.runTransaction(async (tx) => {
          const snap = await tx.get(ref);
          if (!snap.exists) return;
          const current = (snap.data()?.viewerCount as number | undefined) ?? 0;
          const next = Math.max(0, current - 1);
          tx.update(ref, {
            viewerCount: next,
            lastWebhookAt: new Date().toISOString(),
          });
        });
        break;
      }

      default:
        // Other events (track_published, egress_*, etc.) don't affect
        // the viewer count — silently acknowledge so LiveKit doesn't
        // retry.
        break;
    }

    return asJson(200, { ok: true, event: event.event });
  } catch (err) {
    return asError(err);
  }
}

/**
 * `update()` against a non-existent doc throws NOT_FOUND. We treat
 * that as the doc-not-yet-created race (LiveKit webhook beat the
 * `livekit/token` create on a cold-start Vercel function) and silently
 * drop the event — recreating a partial doc with only webhook fields
 * would leave the Android client with a broken stream record (no
 * ownerUid/title), which is strictly worse than missing one viewer
 * count tick that the next event will replace.
 *
 * Any other Firestore error is re-thrown so the route returns 5xx and
 * LiveKit's at-least-once delivery retries it.
 */
async function tryWebhookUpdate(
  ref: DocumentReference,
  data: UpdateData<Record<string, unknown>>
): Promise<void> {
  try {
    await ref.update(data);
  } catch (err) {
    const code = (err as { code?: number | string }).code;
    // NOT_FOUND is gRPC code 5; firestore-admin sometimes surfaces it
    // as the string literal "not-found" too.
    if (code === 5 || code === "not-found") return;
    throw err;
  }
}

/**
 * Extract the publisher UID from a room name following the
 * `u_<uid>_<timestamp>` convention. Returns null if the prefix
 * doesn't match (custom / admin-issued room).
 */
function extractPublisherUid(roomName: string): string | null {
  if (!roomName.startsWith("u_")) return null;
  const rest = roomName.slice(2);
  const sep = rest.indexOf("_");
  if (sep <= 0) return null;
  return rest.slice(0, sep);
}
