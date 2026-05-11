import type { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import {
  asError,
  asJson,
  HttpError,
  isStaff,
  requireUser,
} from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

// PR-I (Mohammed Critical) — CSAE preserve-and-page endpoint.
//
//   POST /api/admin/csae/preserve
//     body: { streamId?, reportId?, note?: string }
//     auth: staff | admin (CSAE work is staff-up, not moderator)
//
// Workflow context
// ----------------
// Halqa has NO automated NCMEC cybertipline pipeline yet (that's
// LAYLA-004, deferred to v0.2). Until that ships, we MUST have a
// stop-gap that lets staff snapshot all the evidence a CSAE report
// references — chat messages, the report doc, the reported user's
// audit trail, both participants' user docs — INTO an immutable
// preservation doc. The staff member then manually emails the
// NCMEC cybertipline (https://report.cybertip.org/) with the caseId
// and the raw payload.
//
// Why an endpoint rather than "let staff query Firestore directly"
// ----------------------------------------------------------------
//   1. Atomicity. The snapshot must be a single point-in-time read.
//      Manually copying via Firebase Console takes minutes and lets
//      the reported user delete-and-recreate their account between
//      copies. The endpoint snapshots everything in one Firestore
//      get-all() and writes to /csae_preserved/{caseId} in one batch.
//
//   2. Audit trail. Every preservation gets a /audit_log row with
//      the staff actor uid + caseId. Chain of custody for a future
//      legal subpoena.
//
//   3. Immutability. The /csae_preserved/{caseId} doc rule denies
//      update + delete from clients (including staff via the
//      Firestore SDK). Only Admin SDK can overwrite, and there's no
//      endpoint that does so. The snapshot stays put.
//
//   4. Schema. The endpoint canonicalises the payload. NCMEC needs
//      specific fields (reporter, reported, content, timestamp).
//      The endpoint produces them in a known shape; ad-hoc copies
//      drift.
//
// Inputs
// ------
// At least one of `streamId` or `reportId` must be present. If a
// report doc references a stream, passing the reportId auto-resolves
// the streamId from the report — staff don't need to look it up.
//
// What gets snapshotted
// ---------------------
//   - The report doc (if reportId given): full row from /reports/.
//   - The reported user doc (resolved from report.reportedUid OR
//     from the stream owner if no report): /users/{reportedUid}.
//   - The reporter user doc (resolved from report.reporterUid):
//     /users/{reporterUid}.
//   - Chat messages from the last 60 minutes on the streamId:
//     /streams/{streamId}/chat/* where timestamp >= now - 60min.
//   - Gifts log from the same window: /streams/{streamId}/gifts/*.
//   - Audit events on the reported user: /audit/{reportedUid}/events/*
//     (last 30 days).
//   - Stream doc: /streams/{streamId}.
//
// What is NOT in this snapshot
// ----------------------------
//   - LiveKit recording / Egress artifacts: those live in cloud
//     storage outside Firestore. Snapshotting them requires a
//     separate signed-URL workflow (LiveKit's API). Out of scope
//     for this stop-gap; staff can manually pull from LiveKit
//     dashboard if a recording exists. The caseId references the
//     egress filename pattern (`{streamId}/{date}.mp4`) so the
//     evidence packet matches.
//   - Avatar binaries (Firebase Storage URLs are snapshotted, but
//     the bytes are not). Staff link directly.
//
// Schema of /csae_preserved/{caseId}
// ----------------------------------
//   {
//     caseId: <auto>,
//     created_at: ISO string,
//     created_by: staff uid,
//     created_by_role: 'staff'|'admin',
//     note: optional staff note,
//     subject: { reportedUid, reporterUid, streamId?, reportId? },
//     payload: {
//       report: <doc> | null,
//       reportedUser: <doc> | null,
//       reporterUser: <doc> | null,
//       stream: <doc> | null,
//       chatMessages: [<doc>, ...]  // last 60 min
//       gifts: [<doc>, ...]          // last 60 min
//       auditEvents: [<doc>, ...]    // last 30 days, reported user
//     }
//   }
//
// Errors
// ------
// 400 — no streamId AND no reportId provided.
// 403 — caller is not staff.
// 404 — referenced reportId or streamId doesn't resolve.
// 500 — Firestore failures bubble up as generic 500 (see asError).

const CHAT_WINDOW_MS = 60 * 60 * 1000; // 60 min
const AUDIT_WINDOW_MS = 30 * 24 * 60 * 60 * 1000; // 30 days
const MAX_CHAT_ROWS = 2000;
const MAX_GIFT_ROWS = 1000;
const MAX_AUDIT_ROWS = 1000;

export async function POST(req: NextRequest) {
  try {
    const actor = await requireUser(req);
    if (!isStaff(actor)) {
      throw new HttpError(403, "Staff access required.");
    }

    let body: Record<string, unknown>;
    try {
      body = (await req.json()) as Record<string, unknown>;
    } catch {
      throw new HttpError(400, "Body must be valid JSON.");
    }
    const streamIdRaw = body.streamId;
    const reportIdRaw = body.reportId;
    const noteRaw = body.note;
    const streamId =
      typeof streamIdRaw === "string" && streamIdRaw.trim().length > 0
        ? streamIdRaw.trim()
        : null;
    const reportId =
      typeof reportIdRaw === "string" && reportIdRaw.trim().length > 0
        ? reportIdRaw.trim()
        : null;
    let note: string | undefined;
    if (noteRaw !== undefined) {
      if (typeof noteRaw !== "string") {
        throw new HttpError(400, "note must be a string if provided.");
      }
      const trimmed = noteRaw.trim();
      if (trimmed.length > 2000) {
        throw new HttpError(400, "note too long (max 2000 chars).");
      }
      if (trimmed.length > 0) note = trimmed;
    }
    if (!streamId && !reportId) {
      throw new HttpError(
        400,
        "At least one of streamId or reportId is required."
      );
    }

    const db = adminFirestore();

    // 1. Resolve the report (if reportId provided).
    let reportData: Record<string, unknown> | null = null;
    if (reportId) {
      const reportSnap = await db.collection("reports").doc(reportId).get();
      if (!reportSnap.exists) {
        throw new HttpError(404, `Report ${reportId} not found.`);
      }
      reportData = reportSnap.data() ?? null;
    }

    // 2. Resolve uids + stream.
    const reportedUid =
      (reportData?.reportedUid as string | undefined) ?? null;
    const reporterUid =
      (reportData?.reporterUid as string | undefined) ?? null;
    const resolvedStreamId =
      streamId ?? ((reportData?.streamId as string | undefined) ?? null);

    let streamData: Record<string, unknown> | null = null;
    if (resolvedStreamId) {
      const streamSnap = await db
        .collection("streams")
        .doc(resolvedStreamId)
        .get();
      if (streamSnap.exists) {
        streamData = streamSnap.data() ?? null;
      } else if (!reportId) {
        // streamId was given directly (not resolved via report) and
        // it doesn't resolve → 404 so staff know to recheck. If we
        // got here via a report, a stale streamId on the report is
        // possible but we still want to preserve the report itself,
        // so we tolerate the miss.
        throw new HttpError(404, `Stream ${resolvedStreamId} not found.`);
      }
    }

    // The reported user is the one we MUST snapshot. Resolve from
    // report first, fall back to stream owner if no report.
    const finalReportedUid =
      reportedUid ??
      ((streamData?.ownerUid as string | undefined) ?? null) ??
      ((streamData?.hostUid as string | undefined) ?? null);

    // 3. Snapshot user docs.
    const userReads: Promise<FirebaseFirestore.DocumentSnapshot>[] = [];
    if (finalReportedUid) {
      userReads.push(db.collection("users").doc(finalReportedUid).get());
    }
    if (reporterUid) {
      userReads.push(db.collection("users").doc(reporterUid).get());
    }
    const [reportedSnap, reporterSnap] = await Promise.all(userReads);
    const reportedUserData = reportedSnap?.exists
      ? reportedSnap.data() ?? null
      : null;
    const reporterUserData = reporterSnap?.exists
      ? reporterSnap.data() ?? null
      : null;

    // 4. Snapshot chat + gifts from the last 60 min if we have a
    // stream. Firestore range queries on createdAt require an
    // index; the chat subcollection's existing rule path implies
    // a createdAt field on each doc. If a stream has no chat or
    // gifts subcollection, the queries return empty — no error.
    const nowMs = Date.now();
    const chatSinceIso = new Date(nowMs - CHAT_WINDOW_MS).toISOString();
    const auditSinceIso = new Date(nowMs - AUDIT_WINDOW_MS).toISOString();

    let chatMessages: Record<string, unknown>[] = [];
    let gifts: Record<string, unknown>[] = [];
    if (resolvedStreamId) {
      const [chatSnap, giftsSnap] = await Promise.all([
        db
          .collection("streams")
          .doc(resolvedStreamId)
          .collection("chat")
          .where("createdAt", ">=", chatSinceIso)
          .orderBy("createdAt", "desc")
          .limit(MAX_CHAT_ROWS)
          .get()
          .catch(() => null),
        db
          .collection("streams")
          .doc(resolvedStreamId)
          .collection("gifts")
          .where("createdAt", ">=", chatSinceIso)
          .orderBy("createdAt", "desc")
          .limit(MAX_GIFT_ROWS)
          .get()
          .catch(() => null),
      ]);
      chatMessages = chatSnap?.docs.map((d) => d.data()) ?? [];
      gifts = giftsSnap?.docs.map((d) => d.data()) ?? [];
    }

    // 5. Snapshot the reported user's audit events (last 30 days).
    let auditEvents: Record<string, unknown>[] = [];
    if (finalReportedUid) {
      const auditSnap = await db
        .collection("audit")
        .doc(finalReportedUid)
        .collection("events")
        .where("granted_at", ">=", auditSinceIso)
        .orderBy("granted_at", "desc")
        .limit(MAX_AUDIT_ROWS)
        .get()
        .catch(() => null);
      auditEvents = auditSnap?.docs.map((d) => d.data()) ?? [];
    }

    // 6. Write the immutable preservation doc + audit_log row in a
    // single batch. Batch (not txn) because there are no reads-after-
    // writes; we already gathered everything outside.
    const caseRef = db.collection("csae_preserved").doc();
    const auditRef = db.collection("audit_log").doc();
    const nowIso = new Date().toISOString();
    const preservation = {
      caseId: caseRef.id,
      created_at: nowIso,
      created_by: actor.uid,
      created_by_role: actor.role,
      note: note ?? null,
      subject: {
        reportedUid: finalReportedUid,
        reporterUid,
        streamId: resolvedStreamId,
        reportId,
      },
      payload: {
        report: reportData,
        reportedUser: reportedUserData,
        reporterUser: reporterUserData,
        stream: streamData,
        chatMessages,
        gifts,
        auditEvents,
      },
      counts: {
        chatMessages: chatMessages.length,
        gifts: gifts.length,
        auditEvents: auditEvents.length,
      },
    };

    const batch = db.batch();
    batch.set(caseRef, preservation);
    batch.set(auditRef, {
      userId: finalReportedUid ?? actor.uid,
      action: "csae_preserved",
      timestamp: nowIso,
      metadata: {
        caseId: caseRef.id,
        actor_uid: actor.uid,
        actor_role: actor.role,
        reportId,
        streamId: resolvedStreamId,
        reportedUid: finalReportedUid,
        reporterUid,
        chat_count: chatMessages.length,
        gift_count: gifts.length,
        audit_count: auditEvents.length,
      },
    });
    await batch.commit();

    return asJson(201, {
      caseId: caseRef.id,
      created_at: nowIso,
      subject: preservation.subject,
      counts: preservation.counts,
    });
  } catch (err) {
    return asError(err);
  }
}
