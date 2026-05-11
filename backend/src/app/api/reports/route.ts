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

// PR-G (Faisal Critical) — user-reporting infrastructure.
//
// The Halqa app has had a "report" affordance in the chat UI and a
// long-press menu on profiles for several builds, but the wire-up
// terminated at a TODO: there was no /api/reports endpoint, so taps
// were swallowed by the client. From a Trust & Safety perspective
// that's the same as not having reporting at all — an Apple/Play
// reviewer attempting to file a test report would have observed
// "form posts, nothing happens" and flagged the build for
// non-compliance with platform UGC requirements.
//
// This route is the minimum viable reporting endpoint:
//
//   POST /api/reports
//     body: {
//       category: 'CSAE' | 'HARASSMENT' | 'IP' | 'SPAM' | 'OTHER',
//       reportedUid?: string,
//       streamId?: string,
//       chatMessageId?: string,
//       freeText?: string  // ≤500 chars
//     }
//
//   GET /api/reports?status=open|triaged|actioned|dismissed
//     staff-only; returns the recent N reports for the dashboard.
//
// Rate-limit: 10 reports / reporter / UTC-day, atomic inside the same
// txn as the create. Prevents a malicious user (or bot) from drowning
// the moderation queue with spam reports.
//
// Audit: every create writes a /audit_log row. Trust & Safety can
// scrub `/audit_log` for `action: "report_filed"` to reconstruct what
// arrived even if a report doc is later mutated / dismissed.

type ReportCategory = "CSAE" | "HARASSMENT" | "IP" | "SPAM" | "OTHER";

const VALID_CATEGORIES: readonly ReportCategory[] = [
  "CSAE",
  "HARASSMENT",
  "IP",
  "SPAM",
  "OTHER",
] as const;

const VALID_STATUSES = ["open", "triaged", "actioned", "dismissed"] as const;
type ReportStatus = (typeof VALID_STATUSES)[number];

const FREE_TEXT_MAX = 500;
const DAILY_REPORT_CAP = 10;

function utcDateBucket(d: Date): string {
  const y = d.getUTCFullYear().toString().padStart(4, "0");
  const m = (d.getUTCMonth() + 1).toString().padStart(2, "0");
  const day = d.getUTCDate().toString().padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function assertCategory(v: unknown): asserts v is ReportCategory {
  if (typeof v !== "string" || !VALID_CATEGORIES.includes(v as ReportCategory)) {
    throw new HttpError(
      400,
      `category must be one of: ${VALID_CATEGORIES.join(", ")}.`
    );
  }
}

function assertOptionalNonEmptyString(
  name: string,
  v: unknown,
  maxLen: number
): string | undefined {
  if (v === undefined || v === null) return undefined;
  if (typeof v !== "string") {
    throw new HttpError(400, `${name} must be a string if provided.`);
  }
  const trimmed = v.trim();
  if (trimmed.length === 0) {
    throw new HttpError(400, `${name} must not be empty if provided.`);
  }
  if (trimmed.length > maxLen) {
    throw new HttpError(
      400,
      `${name} too long (max ${maxLen} chars).`
    );
  }
  return trimmed;
}

export async function POST(req: NextRequest) {
  try {
    const user = await requireUser(req);

    let body: Record<string, unknown>;
    try {
      body = (await req.json()) as Record<string, unknown>;
    } catch {
      throw new HttpError(400, "Body must be valid JSON.");
    }

    assertCategory(body.category);
    const category = body.category as ReportCategory;

    const reportedUid = assertOptionalNonEmptyString(
      "reportedUid",
      body.reportedUid,
      128
    );
    const streamId = assertOptionalNonEmptyString("streamId", body.streamId, 128);
    const chatMessageId = assertOptionalNonEmptyString(
      "chatMessageId",
      body.chatMessageId,
      128
    );
    const freeText = assertOptionalNonEmptyString(
      "freeText",
      body.freeText,
      FREE_TEXT_MAX
    );

    // At least one of reportedUid / streamId / chatMessageId must be
    // present, otherwise we can't anchor the report to anything
    // actionable and the moderation queue fills with content-less
    // rows. Free-text alone is not sufficient — staff need at least
    // one referent to investigate. CSAE category bypasses this
    // (preserve everything; investigators triage from the freeText
    // pointer and signal).
    if (
      category !== "CSAE" &&
      !reportedUid &&
      !streamId &&
      !chatMessageId
    ) {
      throw new HttpError(
        400,
        "Report must include at least one of reportedUid, streamId, or chatMessageId."
      );
    }

    if (reportedUid && reportedUid === user.uid) {
      throw new HttpError(400, "Cannot file a report against yourself.");
    }

    const nowIso = new Date().toISOString();
    const dateBucket = utcDateBucket(new Date());

    const db = adminFirestore();
    const reportRef = db.collection("reports").doc();
    const counterRef = db
      .collection("reportRateCounters")
      .doc(`${user.uid}_${dateBucket}`);
    const auditRef = db.collection("audit_log").doc();

    // Single txn: rate-limit check + report create + counter bump +
    // audit_log row. If any step throws, none commit. The counter
    // doc is read INSIDE the txn so two concurrent 10th-report
    // requests can't both pass the cap check (Firestore optimistic
    // concurrency forces one to retry and observe count=10).
    const created = await db.runTransaction(async (tx) => {
      const counterSnap = await tx.get(counterRef);
      const prevCount = counterSnap.exists
        ? Number(counterSnap.data()?.count ?? 0)
        : 0;
      if (prevCount >= DAILY_REPORT_CAP) {
        throw new HttpError(
          429,
          `Daily report limit reached (${DAILY_REPORT_CAP}). Try again tomorrow.`
        );
      }

      const reportDoc: Record<string, unknown> = {
        reportId: reportRef.id,
        reporterUid: user.uid,
        category,
        status: "open" as ReportStatus,
        createdAt: nowIso,
      };
      if (reportedUid) reportDoc.reportedUid = reportedUid;
      if (streamId) reportDoc.streamId = streamId;
      if (chatMessageId) reportDoc.chatMessageId = chatMessageId;
      if (freeText) reportDoc.freeText = freeText;

      tx.set(reportRef, reportDoc);
      tx.set(
        counterRef,
        {
          reporterUid: user.uid,
          dateBucket,
          count: prevCount + 1,
          lastReportAt: nowIso,
        },
        { merge: true }
      );
      tx.set(auditRef, {
        userId: user.uid,
        action: "report_filed",
        timestamp: nowIso,
        metadata: {
          reportId: reportRef.id,
          category,
          reportedUid: reportedUid ?? null,
          streamId: streamId ?? null,
          chatMessageId: chatMessageId ?? null,
          // freeText is NOT mirrored into audit_log on purpose —
          // /audit_log is staff-readable from the dashboard and
          // copies of arbitrary user free text would duplicate
          // PDPL Article 4 storage. The full report doc carries
          // the text and is staff-readable.
        },
      });

      return reportDoc;
    });

    return asJson(201, created);
  } catch (err) {
    return asError(err);
  }
}

export async function GET(req: NextRequest) {
  try {
    const user = await requireUser(req);
    if (!isStaff(user)) {
      throw new HttpError(403, "Staff access required.");
    }

    const url = new URL(req.url);
    const rawStatus = url.searchParams.get("status");
    let statusFilter: ReportStatus | null = null;
    if (rawStatus !== null) {
      if (!VALID_STATUSES.includes(rawStatus as ReportStatus)) {
        throw new HttpError(
          400,
          `status must be one of: ${VALID_STATUSES.join(", ")}.`
        );
      }
      statusFilter = rawStatus as ReportStatus;
    }
    const rawLimit = url.searchParams.get("limit");
    let limit = 50;
    if (rawLimit !== null) {
      const parsed = Number(rawLimit);
      if (!Number.isFinite(parsed) || parsed < 1 || parsed > 200) {
        throw new HttpError(400, "limit must be an integer between 1 and 200.");
      }
      limit = Math.floor(parsed);
    }

    const db = adminFirestore();
    let query: FirebaseFirestore.Query = db
      .collection("reports")
      .orderBy("createdAt", "desc")
      .limit(limit);
    if (statusFilter) {
      query = db
        .collection("reports")
        .where("status", "==", statusFilter)
        .orderBy("createdAt", "desc")
        .limit(limit);
    }
    const snap = await query.get();
    const items = snap.docs.map((d) => d.data());
    return asJson(200, { items, count: items.length });
  } catch (err) {
    return asError(err);
  }
}
