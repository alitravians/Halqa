import { NextRequest } from "next/server";
import { AccessToken } from "livekit-server-sdk";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

interface TokenBody {
  roomName: string;
  /** "publisher" lets the user broadcast; "viewer" subscribes only. */
  role: "publisher" | "viewer";
  /** Optional title for stream metadata when starting a publisher session. */
  streamTitle?: string;
}

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);
    const body = (await req.json()) as Partial<TokenBody>;

    if (!body.roomName || typeof body.roomName !== "string") {
      throw new HttpError(400, "roomName is required.");
    }
    const role = body.role === "publisher" ? "publisher" : "viewer";

    const apiKey = process.env.LIVEKIT_API_KEY;
    const apiSecret = process.env.LIVEKIT_API_SECRET;
    const wsUrl = process.env.LIVEKIT_URL;
    if (!apiKey || !apiSecret || !wsUrl) {
      throw new HttpError(
        500,
        "LiveKit env vars are not configured (LIVEKIT_API_KEY/SECRET/URL)."
      );
    }

    const at = new AccessToken(apiKey, apiSecret, {
      identity: user.uid,
      name: user.email || user.phoneNumber || user.uid,
      ttl: 60 * 60, // 1 hour
    });
    at.addGrant({
      room: body.roomName,
      roomJoin: true,
      canPublish: role === "publisher",
      canSubscribe: true,
      canPublishData: true,
    });
    const token = await at.toJwt();

    if (role === "publisher") {
      const db = adminFirestore();
      const streamRef = db.collection("streams").doc(body.roomName);
      const existing = await streamRef.get();
      if (!existing.exists) {
        await streamRef.set({
          streamId: body.roomName,
          ownerUid: user.uid,
          title: body.streamTitle?.trim() || "بث جديد",
          status: "live",
          startTime: new Date().toISOString(),
          endTime: null,
          viewerCount: 0,
          roomName: body.roomName,
        });
        await db.collection("audit_log").add({
          userId: user.uid,
          action: "stream_start",
          timestamp: new Date().toISOString(),
          metadata: { streamId: body.roomName, title: body.streamTitle ?? null },
        });
      }
    }

    return asJson(200, { token, url: wsUrl });
  } catch (err) {
    return asError(err);
  }
}
