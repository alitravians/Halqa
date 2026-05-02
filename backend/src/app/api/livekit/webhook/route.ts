import { NextRequest } from "next/server";
import { WebhookReceiver } from "livekit-server-sdk";
import { FieldValue } from "firebase-admin/firestore";
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
        // Make sure the document exists and viewerCount starts at 0.
        // Don't overwrite existing fields — `livekit/token` already
        // populated ownerUid/title/etc when it was created.
        await ref.set(
          { viewerCount: 0, lastWebhookAt: new Date().toISOString() },
          { merge: true }
        );
        break;
      }

      case "room_finished": {
        await ref.set(
          {
            status: "ended",
            endTime: new Date().toISOString(),
            viewerCount: 0,
            lastWebhookAt: new Date().toISOString(),
          },
          { merge: true }
        );
        break;
      }

      case "participant_joined": {
        const identity = event.participant?.identity ?? "";
        if (identity === publisherUid) {
          // Publisher's own join — don't count as a viewer.
          break;
        }
        await ref.set(
          {
            viewerCount: FieldValue.increment(1),
            lastWebhookAt: new Date().toISOString(),
          },
          { merge: true }
        );
        break;
      }

      case "participant_left": {
        const identity = event.participant?.identity ?? "";
        if (identity === publisherUid) {
          break;
        }
        // Use a transaction so we can clamp at 0 — viewerCount must
        // never go negative even if LiveKit replays a stale `_left`.
        await db.runTransaction(async (tx) => {
          const snap = await tx.get(ref);
          const current = (snap.data()?.viewerCount as number | undefined) ?? 0;
          const next = Math.max(0, current - 1);
          tx.set(
            ref,
            {
              viewerCount: next,
              lastWebhookAt: new Date().toISOString(),
            },
            { merge: true }
          );
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
