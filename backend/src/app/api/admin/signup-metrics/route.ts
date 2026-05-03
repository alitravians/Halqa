import type { NextRequest } from "next/server";
import { adminFirestore } from "@/lib/firebase-admin";
import { asError, asJson, HttpError, isStaff, requireUser } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * Date-bucket parser. Accepts the same `YYYY-MM-DD` shape the heartbeat
 * route writes; rejects anything else with HTTP 400 so a malformed
 * query can't end up reading some other Firestore path. The regex is
 * deliberately strict (no leading + sign, no whitespace, no time
 * component).
 */
function parseDate(raw: string | null): string | null {
  if (raw === null) return null;
  const m = raw.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return null;
  return `${m[1]}-${m[2]}-${m[3]}`;
}

/**
 * GET /api/admin/signup-metrics?date=YYYY-MM-DD
 *
 * Layla's GR5 admin surface. Returns a single day's signup metrics
 * doc (count, carrier breakdown, lock state) for the staff dashboard.
 * Staff-only — the doc carries low-PII counters but the admin surface
 * is gated to keep enumeration of other days' data out of regular
 * users' reach.
 *
 * The route is read-only; toggling `signup_locked` (manual unlock)
 * goes through the Admin SDK / Firebase Console for now. A future
 * `POST /api/admin/signup-metrics/unlock` endpoint can shadow this
 * one when staff workflows demand it; until then, the throughput
 * is low enough that Console flips are operationally fine.
 *
 * Response shape:
 *   200 {
 *     date: "YYYY-MM-DD",
 *     count: number,
 *     carriers: { "+966": number, "+20": number, "unknown": number, … },
 *     locked: boolean,
 *     locked_at: ISO string | null,
 *     created_at: ISO string | null,
 *     updated_at: ISO string | null
 *   }
 *   404 — date param valid but no signups have happened on that day.
 *         (Front-end can render an empty-state.)
 *   400 — date param missing or malformed.
 *   401 / 403 — auth / not staff.
 */
export async function GET(req: NextRequest) {
  try {
    const user = await requireUser(req);
    if (!isStaff(user)) {
      throw new HttpError(403, "Staff access required.");
    }

    const url = new URL(req.url);
    const date = parseDate(url.searchParams.get("date"));
    if (date === null) {
      throw new HttpError(
        400,
        "Missing or malformed `date` query param. Expected YYYY-MM-DD."
      );
    }

    const snap = await adminFirestore()
      .collection("metrics")
      .doc("signups")
      .collection("days")
      .doc(date)
      .get();

    if (!snap.exists) {
      throw new HttpError(404, `No signup metrics for ${date}.`);
    }

    const data = snap.data() ?? {};
    // Coerce timestamps into ISO strings so the response is JSON-pure.
    // Firestore Timestamp serialisation isn't stable across Admin SDK
    // versions and Next.js runtime selection, so we normalise here.
    type FsTime = { toDate: () => Date };
    const tsToIso = (v: unknown): string | null => {
      if (typeof v === "string") return v;
      if (v && typeof (v as FsTime).toDate === "function") {
        return (v as FsTime).toDate().toISOString();
      }
      return null;
    };

    return asJson(200, {
      date,
      count: Number(data.count ?? 0),
      carriers: (data.carriers ?? {}) as Record<string, number>,
      locked: data.signup_locked === true,
      locked_at: tsToIso(data.locked_at),
      created_at: tsToIso(data.createdAt),
      updated_at: tsToIso(data.updatedAt),
    });
  } catch (err) {
    return asError(err);
  }
}
