# Halqa Firebase project

This directory holds the Firebase project configuration deployed to
`halqa-prod`:

```
firebase/
├── firebase.json            # firebase CLI config (rules, indexes, functions)
├── firestore.rules          # Firestore security rules (deployed via `firebase deploy --only firestore:rules`)
├── firestore.indexes.json   # Composite indexes (deployed via `firebase deploy --only firestore:indexes`)
└── functions/               # Cloud Functions source (Gen 2, Node 20, TypeScript)
    ├── package.json
    ├── tsconfig.json
    └── src/
        ├── index.ts         # Exports every function for `firebase deploy`
        ├── firebase-admin.ts
        └── streamSweep.ts   # Scheduled Function: stale-stream watchdog
```

Run all `firebase` CLI commands from inside this `firebase/` directory
so the relative paths in `firebase.json` resolve correctly.

## Cloud Functions

### streamSweep — stream-watchdog Scheduled Function (PR-N)

Replaces the prior Vercel cron at `backend/src/app/api/cron/streams/sweep`
(decommissioned in PR #114 because Vercel Hobby caps cron at one run
per day; the watchdog must run every 5 minutes for stream-end
correctness and leaderboard fairness).

- **Schedule:** `every 5 minutes` (Cloud Scheduler → Pub/Sub trigger).
- **Region:** `asia-south1` (co-located with Firestore multi-region).
- **Runtime:** Node 20, Gen 2 functions.
- **Behaviour:** identical to the prior Vercel route — scans
  `streams` where `status=='live'`, force-ends those with
  `lastWebhookAt` older than 60 min or `startTime` older than 6 hr,
  with the PR #94 stale-recheck inside the txn so a reconnecting
  publisher is never reaped.
- **Auth:** Scheduler → Function is internal to the GCP project; no
  `CRON_SECRET` needed (unlike the Vercel route).
- **Audit:** writes `/audit_log` row with `action="stream_end"`,
  `metadata.endedBy="watchdog_sweep"` — same shape as before so
  Trust & Safety dashboards keep working.

#### First-time setup

The first deploy provisions Cloud Scheduler and a Pub/Sub topic
automatically. Required Google Cloud APIs (most will auto-enable on
first deploy, but enabling explicitly avoids first-deploy slowness):

- Cloud Functions API (`cloudfunctions.googleapis.com`)
- Cloud Build API (`cloudbuild.googleapis.com`)
- Artifact Registry API (`artifactregistry.googleapis.com`)
- Cloud Run API (`run.googleapis.com`)
- Cloud Scheduler API (`cloudscheduler.googleapis.com`)
- Pub/Sub API (`pubsub.googleapis.com`)
- Eventarc API (`eventarc.googleapis.com`)

The Cloud Functions runtime service account (default:
`<project-number>-compute@developer.gserviceaccount.com` for Gen 2)
needs no extra IAM grants beyond what `firebase deploy` configures —
Firestore Admin SDK access is granted automatically by Firebase.

#### Deploy

```bash
cd firebase/functions
npm install                 # first time only
cd ..
firebase deploy --only functions:streamSweep
```

After the first deploy the function will appear at:

```
https://console.cloud.google.com/cloudscheduler?project=halqa-prod
https://console.cloud.google.com/functions/list?project=halqa-prod
```

Cloud Scheduler will start invoking it on the `every 5 minutes`
schedule immediately.

#### Logs

```bash
cd firebase
firebase functions:log --only streamSweep
```

Each run logs a structured entry:

- `stream_sweep_done` with `{checked, endedCount, ended[…]}` on success.
- `stream_sweep_failed` with `{message, stack}` on transaction failure.

Use the Cloud Logging filter `resource.labels.function_name="streamSweep"`
in the GCP console to chart sweep counts over time.

#### Local emulator dry-run

```bash
cd firebase/functions
npm install
npm run build
cd ..
firebase emulators:start --only functions,firestore
```

The Scheduled Function does NOT auto-fire in the emulator. To test
the sweep logic locally either:

- Invoke `runStreamSweep()` directly from `firebase functions:shell`:
  ```
  > const { runStreamSweep } = require('./lib/streamSweep')
  > await runStreamSweep()
  ```
- Or trigger the Pub/Sub topic manually via the emulator UI:
  http://localhost:4000/pubsub

#### Rollback

If the Function misbehaves, disable Cloud Scheduler to halt the cron
without redeploying:

```bash
gcloud scheduler jobs pause firebase-schedule-streamSweep-asia-south1 \
  --location=asia-south1 --project=halqa-prod
```

Re-enable with `gcloud scheduler jobs resume <name>`. The Vercel
endpoint at `/api/cron/streams/sweep` now returns 410 Gone and cannot
be re-enabled as a fallback (the route is intentionally stubbed).

## Firestore rules and indexes

Deploy independently:

```bash
cd firebase
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

## Env vars

Cloud Functions in `halqa-prod` use Application Default Credentials at
runtime — no `FIREBASE_SERVICE_ACCOUNT_JSON` needed (unlike the Vercel
backend, which still requires it for Admin SDK access). The project ID
is implicit (`halqa-prod`) and the region is pinned per function in
code (`asia-south1` for `streamSweep`).

If a future function needs project secrets (e.g. LiveKit API key),
manage them with `firebase functions:secrets:set` and reference via
`defineSecret(...)` in code — do NOT put them in `package.json` or
plaintext env files.
