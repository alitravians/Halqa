# Halqa Firebase project

This directory holds the Firebase project configuration deployed to
`halqa-prod`:

```
firebase/
├── firebase.json            # firebase CLI config
├── firestore.rules          # Firestore security rules
└── firestore.indexes.json   # Composite indexes
```

Run `firebase` CLI commands from inside this `firebase/` directory so
the relative paths in `firebase.json` resolve correctly.

## Firestore rules and indexes

Deploy independently:

```bash
cd firebase
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

## Stream watchdog cron

The stream-watchdog cron is **not** a Firebase Cloud Function — it
runs as a GitHub Actions scheduled workflow in
`.github/workflows/stream-watchdog.yml` that hits the Vercel route
`POST /api/cron/streams/sweep` with a Bearer `CRON_SECRET` every 5
minutes.

Cloud Functions for Firebase require the Blaze (pay-as-you-go) plan
even for the `every 5 minutes` Scheduled Function pattern — the
`halqa-prod` project is on Spark, so the watchdog cannot live there
until the project is upgraded.

See the workflow file for setup notes (required GitHub Actions
secrets, Vercel env vars).
