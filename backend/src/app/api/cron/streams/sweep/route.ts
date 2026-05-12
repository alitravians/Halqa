import { asJson } from "@/lib/auth";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

/**
 * 410 Gone stub — KHALID PR-N.
 *
 * The stale-stream watchdog moved out of Vercel cron into a Firebase
 * Scheduled Cloud Function (`firebase/functions/src/streamSweep.ts`)
 * because Vercel Hobby plan caps cron jobs at one run per day, while
 * the watchdog must run every 5 minutes for stream-end correctness
 * and leaderboard fairness. The Firebase Functions free tier allows
 * `every 5 minutes` without plan-imposed throttling.
 *
 * The endpoint is kept (rather than deleted) so any stale caller —
 * Vercel-internal cron retry, staff curl with the old CRON_SECRET,
 * external monitor — is told explicitly that the work has moved
 * rather than silently 404-ing.
 *
 * Deploy / monitor the replacement via:
 *   cd firebase && firebase deploy --only functions:streamSweep
 *   firebase --config firebase.json functions:log --only streamSweep
 *
 * See `firebase/README.md` for full operational notes.
 */
function gone() {
  return asJson(
    410,
    {
      ok: false,
      gone: true,
      message:
        "Watchdog moved to Firebase Scheduled Function (firebase/functions/streamSweep). This Vercel cron endpoint is decommissioned.",
      replacement: "firebase/functions/src/streamSweep.ts",
      schedule: "every 5 minutes",
    },
  );
}

export async function GET() {
  return gone();
}

export async function POST() {
  return gone();
}
