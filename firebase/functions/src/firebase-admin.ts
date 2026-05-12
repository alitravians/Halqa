import { getApps, initializeApp, type App } from "firebase-admin/app";
import { getFirestore, type Firestore } from "firebase-admin/firestore";

/**
 * Admin SDK singleton for Cloud Functions runtime.
 *
 * Unlike the backend Vercel route (which loads service-account JSON
 * from `FIREBASE_SERVICE_ACCOUNT_JSON`), Cloud Functions auto-detects
 * Application Default Credentials from its runtime service account.
 * No env var is required.
 */
let cachedApp: App | null = null;

export function adminApp(): App {
  if (cachedApp) return cachedApp;
  const existing = getApps()[0];
  if (existing) {
    cachedApp = existing;
    return existing;
  }
  cachedApp = initializeApp();
  return cachedApp;
}

export function adminFirestore(): Firestore {
  return getFirestore(adminApp());
}
