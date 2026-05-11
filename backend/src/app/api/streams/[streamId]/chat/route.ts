import type { NextRequest } from "next/server";
import { FieldValue } from "firebase-admin/firestore";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, requireUser } from "@/lib/auth";
import { assertNotBanned } from "@/lib/bans";
import { classifyText } from "@/lib/word-filter";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// PR-J — chat message POST endpoint.
//
//   POST /api/streams/{streamId}/chat
//     body: { text: string (1..500 chars) }
//     auth: requireUser, requires the user not be banned
//
// Before this endpoint, the /streams/{streamId}/chat subcollection was
// deny-write — there was no server path to write a message and the
// Android UI's ChatRepository.observe() always emitted an empty list
// (rules.firebase.rules:248-250 comment documents this). This endpoint
// is the first writer; the firestore.rules deny-from-client posture
// stays unchanged (Admin SDK bypasses rules).
//
// Pipeline
// --------
//   1. requireUser (ban-gate via PR-H; allowBanned NOT set, so banned
//      users 403 here regardless of /lib/bans assertNotBanned below).
//   2. assertNotBanned (defence-in-depth; covers the case where
//      requireUser is called with allowBanned=true elsewhere by mistake
//      — chat must NEVER allow banned users).
//   3. Validate streamId + body.text. text length 1..500. No control
//      characters.
//   4. Verify the stream exists AND is live. Chat into a non-live or
//      missing stream is rejected (404 / 409).
//   5. word-filter classifyText on the message:
//        - HARD hit -> 400 reject + /audit_log row with the matched
//          terms so T&S can pattern-match repeat offenders.
//        - SOFT hit -> allow but log soft hit to /audit_log (no user-
//          facing rejection — closed beta tolerates soft hits; users
//          can report).
//        - clean -> proceed.
//   6. Atomic write: /streams/{streamId}/chat/{messageId} document +
//      a /audit_log row (only on hard reject OR soft hit; clean
//      messages skip audit to control write volume).
//
// Schema of /streams/{streamId}/chat/{messageId}
// ----------------------------------------------
//   {
//     messageId: <auto>,
//     streamId,
//     senderUid,
//     senderHandle: optional snapshot,
//     senderDisplayName: optional snapshot,
//     text,
//     createdAt: ISO string,
//     softFlag: boolean (true if classifier returned 'soft'),
//     softFlagTerms?: string[]  (only if softFlag=true)
//   }
//
// The senderHandle / senderDisplayName fields are snapshotted at send
// time so the chat history doesn't break if the sender changes their
// handle later — past messages keep showing the historic identity.
// They're optional because not every user has set them.

const MAX_TEXT = 500;
const MIN_TEXT = 1;

function isValidText(t: string): boolean {
  // Reject control chars (newlines / tabs are fine — keep \n \r \t).
  return !/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/.test(t);
}

export async function POST(
  req: NextRequest,
  ctx: { params: Promise<{ streamId: string }> }
) {
  try {
    const user = await requireUser(req);
    await assertNotBanned(user.uid);

    const params = await ctx.params;
    const streamId = params.streamId;
    if (typeof streamId !== "string" || streamId.length === 0) {
      throw new HttpError(400, "streamId required.");
    }

    let body: Record<string, unknown>;
    try {
      body = (await req.json()) as Record<string, unknown>;
    } catch {
      throw new HttpError(400, "Body must be valid JSON.");
    }
    const textRaw = body.text;
    if (typeof textRaw !== "string") {
      throw new HttpError(400, "text must be a string.");
    }
    const text = textRaw.trim();
    if (text.length < MIN_TEXT) {
      throw new HttpError(400, "text must not be empty.");
    }
    if (text.length > MAX_TEXT) {
      throw new HttpError(400, `text too long (max ${MAX_TEXT} chars).`);
    }
    if (!isValidText(text)) {
      throw new HttpError(400, "text contains disallowed control characters.");
    }

    const db = adminFirestore();
    const streamRef = db.collection("streams").doc(streamId);
    const streamSnap = await streamRef.get();
    if (!streamSnap.exists) {
      throw new HttpError(404, "Stream not found.");
    }
    const streamData = streamSnap.data() ?? {};
    const status = streamData.status as string | undefined;
    if (status !== "live") {
      throw new HttpError(
        409,
        "Stream is not live; cannot post chat messages."
      );
    }

    // Block-list check: host may have blocked this sender via the
    // host-owned giftBlocklist subcollection. We piggy-back on that
    // list for chat too — if a host blocks someone from sending gifts,
    // they almost certainly want to block them from chat as well.
    const hostUid =
      (streamData.ownerUid as string | undefined) ??
      (streamData.hostUid as string | undefined);
    if (hostUid && hostUid !== user.uid) {
      const blockSnap = await db
        .collection("users")
        .doc(hostUid)
        .collection("giftBlocklist")
        .doc(user.uid)
        .get();
      if (blockSnap.exists) {
        throw new HttpError(403, "You are blocked by the host of this stream.");
      }
    }

    // Word-filter pass.
    const filterResult = classifyText(text);
    if (filterResult.classification === "hard") {
      // Log the rejection so T&S can pattern-match repeat offenders.
      const auditRef = db.collection("audit_log").doc();
      const nowIso = new Date().toISOString();
      await auditRef.set({
        userId: user.uid,
        action: "chat_message_blocked",
        timestamp: nowIso,
        metadata: {
          streamId,
          blockedTerms: filterResult.blockedTerms,
          textLength: text.length,
        },
      });
      throw new HttpError(
        400,
        "Message contains disallowed words. Please rephrase."
      );
    }

    const nowIso = new Date().toISOString();
    const chatRef = streamRef.collection("chat").doc();
    const messageDoc: Record<string, unknown> = {
      messageId: chatRef.id,
      streamId,
      senderUid: user.uid,
      text,
      createdAt: nowIso,
      createdAtServer: FieldValue.serverTimestamp(),
    };
    if (user.handle) messageDoc.senderHandle = user.handle;
    if (user.displayName) messageDoc.senderDisplayName = user.displayName;
    if (filterResult.classification === "soft") {
      messageDoc.softFlag = true;
      messageDoc.softFlagTerms = filterResult.blockedTerms;
    } else {
      messageDoc.softFlag = false;
    }

    if (filterResult.classification === "soft") {
      // Soft hit: write the message AND a /audit_log row so T&S can
      // detect a user racking up many soft hits and escalate. Batch
      // (not txn) because there are no reads-after-writes.
      const auditRef = db.collection("audit_log").doc();
      const batch = db.batch();
      batch.set(chatRef, messageDoc);
      batch.set(auditRef, {
        userId: user.uid,
        action: "chat_message_soft_flag",
        timestamp: nowIso,
        metadata: {
          streamId,
          messageId: chatRef.id,
          softFlagTerms: filterResult.blockedTerms,
        },
      });
      await batch.commit();
    } else {
      // Clean: just write the message. Skip /audit_log on clean
      // messages to keep write volume bounded.
      await chatRef.set(messageDoc);
    }

    return asJson(201, {
      messageId: chatRef.id,
      streamId,
      createdAt: nowIso,
      softFlag: filterResult.classification === "soft",
    });
  } catch (err) {
    return asError(err);
  }
}
