/**
 * Halqa Firebase Cloud Functions entry point.
 *
 * Exports every Cloud Function the project ships. Firebase deploy
 * picks these up by name and provisions each as a separate function
 * resource on Cloud Functions for Firebase (Gen 2).
 */
export { streamSweep } from "./streamSweep";
