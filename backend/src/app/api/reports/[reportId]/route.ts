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

// PR-G — staff triage endpoint for a single report.
//
//   GET  /api/reports/{reportId}     staff read of one report
//   POST /api/reports/{reportId}     staff status update (open →
//                                    triaged → actioned | dismissed)
//
// Status transitions are linear forward only. A staff member cannot
// re-open a dismissed report or undo an actioned one through this
// endpoint — those are admin-level operations that should ship as a
// dedicated /api/admin/reports/{reportId}/reopen flow with its own
// audit row. Keeping the staff path append-only matches the same
// "no rewriting history" principle the audit_log itself uses.

const VALID_FORWARD_STATUSES = ["triaged", "actioned", "dismissed"] as const;
type ForwardStatus = (typeof VALID_FORWARD_STATUSES)[number];

const STATUS_ORDER: Record<string, number> = {
  open: 0,
  triaged: 1,
  actioned: 2,
  dismissed: 2,
};

interface RouteContext {
  params: Promise<{ reportId: string }>;
}

export async function GET(req: NextRequest, ctx: RouteContext) {
  try {
    const user = await requireUser(req);
    if (!isStaff(user)) {
      throw new HttpError(403, "Staff access required.");
    }
    const { reportId } = await ctx.params;
    if (!/^[a-zA-Z0-9_-]{8,128}$/.test(reportId)) {
      throw new HttpError(400, "Invalid reportId.");
    }
    const snap = await adminFirestore()
      .collection("reports")
      .doc(reportId)
      .get();
    if (!snap.exists) {
      throw new HttpError(404, "Report not found.");
    }
    return asJson(200, snap.data());
  } catch (err) {
    return asError(err);
  }
}

export async function POST(req: NextRequest, ctx: RouteContext) {
  try {
    const user = await requireUser(req);
    if (!isStaff(user)) {
      throw new HttpError(403, "Staff access required.");
    }

    const { reportId } = await ctx.params;
    if (!/^[a-zA-Z0-9_-]{8,128}$/.test(reportId)) {
      throw new HttpError(400, "Invalid reportId.");
    }

    let body: Record<string, unknown>;
    try {
      body = (await req.json()) as Record<string, unknown>;
    } catch {
      throw new HttpError(400, "Body must be valid JSON.");
    }

    const status = body.status;
    if (
      typeof status !== "string" ||
      !VALID_FORWARD_STATUSES.includes(status as ForwardStatus)
    ) {
      throw new HttpError(
        400,
        `status must be one of: ${VALID_FORWARD_STATUSES.join(", ")}.`
      );
    }
    let note: string | undefined;
    if (body.note !== undefined) {
      if (typeof body.note !== "string") {
        throw new HttpError(400, "note must be a string if provided.");
      }
      const trimmed = body.note.trim();
      if (trimmed.length > 1000) {
        throw new HttpError(400, "note too long (max 1000 chars).");
      }
      if (trimmed.length > 0) note = trimmed;
    }

    const nowIso = new Date().toISOString();
    const db = adminFirestore();
    const reportRef = db.collection("reports").doc(reportId);
    const auditRef = db.collection("audit_log").doc();

    await db.runTransaction(async (tx) => {
      const fresh = await tx.get(reportRef);
      if (!fresh.exists) {
        throw new HttpError(404, "Report not found.");
      }
      const data = fresh.data() ?? {};
      const currentStatus = (data.status as string) ?? "open";
      const currentRank = STATUS_ORDER[currentStatus] ?? -1;
      const targetRank = STATUS_ORDER[status] ?? -1;
      if (targetRank < currentRank) {
        // Linear forward-only: cannot regress to an earlier state.
        // (open=0 → triaged=1 → actioned/dismissed=2). Re-opening a
        // dismissed report must go through an admin reopen flow.
        throw new HttpError(
          409,
          `Cannot transition report from ${currentStatus} to ${status}.`
        );
      }
      if (targetRank === currentRank && currentStatus === status) {
        throw new HttpError(409, `Report is already ${status}.`);
      }

      const update: Record<string, unknown> = {
        status,
        updatedAt: nowIso,
        triagedBy: user.uid,
      };
      if (note) update.staffNote = note;

      tx.update(reportRef, update);
      tx.set(auditRef, {
        userId: user.uid,
        action: "report_status_update",
        timestamp: nowIso,
        metadata: {
          reportId,
          fromStatus: currentStatus,
          toStatus: status,
          hasNote: !!note,
        },
      });
    });

    const fresh = await reportRef.get();
    return asJson(200, fresh.data());
  } catch (err) {
    return asError(err);
  }
}
