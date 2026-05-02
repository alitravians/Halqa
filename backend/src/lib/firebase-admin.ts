import { cert, getApps, initializeApp, type App } from "firebase-admin/app";
import { getAuth, type Auth } from "firebase-admin/auth";
import { getFirestore, type Firestore } from "firebase-admin/firestore";

let cachedApp: App | null = null;

function loadCredential() {
  const json = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!json) {
    throw new Error(
      "FIREBASE_SERVICE_ACCOUNT_JSON env var is not set. Add the service account JSON to Vercel env."
    );
  }
  let parsed: { project_id: string; client_email: string; private_key: string };
  try {
    parsed = JSON.parse(json);
  } catch {
    throw new Error("FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON.");
  }
  return cert({
    projectId: parsed.project_id,
    clientEmail: parsed.client_email,
    // Vercel env vars often escape newlines; normalize.
    privateKey: parsed.private_key.replace(/\\n/g, "\n"),
  });
}

export function adminApp(): App {
  if (cachedApp) return cachedApp;
  const existing = getApps()[0];
  if (existing) {
    cachedApp = existing;
    return existing;
  }
  cachedApp = initializeApp({ credential: loadCredential() });
  return cachedApp;
}

export function adminAuth(): Auth {
  return getAuth(adminApp());
}

export function adminFirestore(): Firestore {
  return getFirestore(adminApp());
}
