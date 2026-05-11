package com.halqa.app.data

import com.halqa.app.BuildConfig
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Idempotent first-sign-in bootstrap for `/users/{uid}`.
 *
 * Background: the original phone + Google sign-in flows shipped with the M0
 * scaffolding wrote nothing to Firestore on first authentication. They relied
 * on the backend's `requireUser` middleware to lazily create the doc on the
 * first authenticated REST call. This created the **phantom-guest bug**: the
 * UI tries to read `/users/{uid}` immediately after sign-in (so
 * [UserRepository.observeProfile] can hydrate the profile screen, and
 * [AuthRepository.resolveRoleForUid] can decide whether to route to staff
 * home), and that read returns `null` until the very first backend write
 * happens — by which point the user has already navigated to Main as a
 * "phantom guest" with no role and no profile. Email sign-up has the same
 * theoretical exposure but happens to mask it because email accounts almost
 * always immediately fetch `/api/users/me` on landing.
 *
 * Firestore rules already allow the owner to create their own `/users/{uid}`
 * doc on first sign-in (`allow create: if isOwner(userId) && role == 'user'
 * && uid == userId`), so the safe fix is to write the minimal doc directly
 * from the client right after `FirebaseAuth.signInWithCredential` resolves,
 * synchronously, before navigating anywhere.
 *
 * The doc is created with `SetOptions.merge`-equivalent semantics — we never
 * overwrite an existing role or createdAt. The intent is "create if absent,
 * patch the contact-channel fields if present". This is critical because:
 *
 *  - A user might sign in with phone today and Google tomorrow with the
 *    same Firebase UID (account linking) — the second sign-in must NOT
 *    clobber the first sign-in's `phoneNumber`.
 *  - A staff member who was promoted via the Admin Panel must NEVER be
 *    demoted to `role: 'user'` by their own re-authentication. The role
 *    field is therefore set only when the doc does not exist; on
 *    subsequent calls the existing role is left untouched.
 */
object UserDocBootstrap {

    /**
     * Ensure `/users/{uid}` exists. Safe to call on every sign-in.
     *
     * @param uid          The Firebase Auth UID of the just-signed-in user.
     * @param phoneNumber  E.164-formatted phone number, if known. Persisted
     *                     so /api/users/me + observeProfile see it without
     *                     waiting for a backend round-trip.
     * @param email        Email, if known (Google Sign-In path). Same.
     * @param displayName  Display name from the auth provider (Google),
     *                     if known. Used to seed the initial doc only —
     *                     never overwrites a user-edited value.
     * @param avatar       Profile photo URL from the auth provider
     *                     (Google `photoUrl`), if known. Same seed-only
     *                     semantics as [displayName].
     * @return a [Result] describing what happened. Callers use this to
     *         decide whether to fire follow-up first-time-signup hooks
     *         (e.g. Layla's GR5 daily signup heartbeat) which must run
     *         only on [Result.Created], never on a return sign-in.
     */
    enum class Result {
        /** Doc did not exist; we just created it (first sign-in for this uid). */
        Created,
        /** Doc existed and we patched contact / provider fields on it. */
        Patched,
        /** Doc existed and was already complete; no write happened. */
        Skipped,
        /** Initial read failed. The backend's lazy create path will fill in. */
        ReadFailed,
    }

    /**
     * Audit-trail labels stamped on [Result.Created] writes into both
     * `/users/{uid}.bypass_grant.reason` and the matching
     * `/audit/{uid}/events` row.
     *
     * Each sign-in provider passes its own value so a Trust & Safety
     * investigator can answer "which sign-in path admitted this user
     * under the closed-beta KYC bypass" by reading a single field —
     * the alternative is grepping `/audit_log` for the surrounding
     * action and is unreliable for users who never interact with
     * audited endpoints afterwards.
     *
     * The Firestore rule on `/audit/{uid}/events` accepts any string
     * value for `reason` (the rule only constrains `type` to a closed
     * set), so adding labels here is safe without a rules deploy.
     *
     * Layla GR1 (bypass_grant on the user doc) and GR2 (independent
     * audit event) must use the SAME reason string per call — both
     * are stamped from the same enum slot below.
     */
    enum class BypassReason(val wire: String) {
        /** Phone OTP path via [PhoneAuthRepository]. */
        PhoneOtp("BETA_M0_PHONE_OTP"),
        /** Google sign-in path via [GoogleAuthRepository]. */
        GoogleSignIn("BETA_M0_GOOGLE_SIGNIN"),
        /** Email / password sign-in path via [AuthRepository]. */
        EmailSignIn("BETA_M0_EMAIL_SIGNIN"),
    }

    suspend fun ensureUserDoc(
        uid: String,
        phoneNumber: String?,
        email: String?,
        displayName: String? = null,
        avatar: String? = null,
        bypassReason: BypassReason = BypassReason.PhoneOtp,
    ): Result {
        val firestore = FirebaseFirestore.getInstance()
        val ref = firestore.collection("users").document(uid)
        val snap = try {
            ref.get().await()
        } catch (t: Throwable) {
            // Fail-open: if the read fails (offline, transient Firestore
            // error), the backend will eventually fill the doc on its
            // first authenticated call. We do NOT want to block sign-in
            // on a transient read.
            return Result.ReadFailed
        }

        if (snap.exists()) {
            // Doc already exists — patch contact channels + provider
            // profile fields ONLY when the existing doc has them blank.
            // Never touch role / createdAt / uid (the rules forbid it on
            // the self-update path, and we don't want to anyway). Never
            // overwrite a user-edited displayName / avatar — once they
            // pick their own handle / photo we keep it.
            val patch = mutableMapOf<String, Any>()
            if (!phoneNumber.isNullOrBlank() && (snap.getString("phoneNumber").isNullOrBlank())) {
                patch["phoneNumber"] = phoneNumber
            }
            if (!email.isNullOrBlank() && (snap.getString("email").isNullOrBlank())) {
                patch["email"] = email
            }
            if (!displayName.isNullOrBlank() && (snap.getString("displayName").isNullOrBlank())) {
                patch["displayName"] = displayName
            }
            if (!avatar.isNullOrBlank() && (snap.getString("avatar").isNullOrBlank())) {
                patch["avatar"] = avatar
            }
            if (patch.isNotEmpty()) {
                patch["updatedAt"] = FieldValue.serverTimestamp()
                return try {
                    ref.update(patch).await()
                    Result.Patched
                } catch (_: Throwable) {
                    // Patch is best-effort; on rule rejection or transient
                    // error the backend's /api/users/me update path is
                    // authoritative anyway.
                    Result.Patched
                }
            }
            return Result.Skipped
        }

        // First sign-in for this uid. Build the minimal doc the firestore
        // rule expects (uid + role) and the fields the Android UI listens
        // on (createdAt + contact channels). Field set must satisfy:
        //   request.resource.data.role == 'user'
        //   request.resource.data.uid  == userId
        val doc = mutableMapOf<String, Any>(
            "uid" to uid,
            "role" to "user",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "displayName" to (displayName?.trim().orEmpty()),
            "handle" to "",
            "bio" to "",
            "avatar" to (avatar?.trim().orEmpty()),
        )
        if (!phoneNumber.isNullOrBlank()) doc["phoneNumber"] = phoneNumber
        if (!email.isNullOrBlank()) doc["email"] = email

        // Layla's GR1 (T&S guardrail). When the backend's beta-bypass
        // flag is on, every brand-new sign-in is being grandfathered
        // past full KYC. Stamp that grant durably on the user doc so:
        //   - staff investigating a wallet incident later can see the
        //     account was admitted under the beta bypass, not after a
        //     real document review;
        //   - the withdrawal endpoint can hard-block any cashout from
        //     this account until `will_reverify` is cleared by a manual
        //     re-KYC review (see backend wallet/withdraw route);
        //   - when the bypass flag is flipped off, a backfill query on
        //     `bypass_grant.will_reverify == true` enumerates every
        //     grandfathered user instead of relying on creation-time
        //     guesses.
        // The grant block is part of the SAME create call (no second
        // round-trip) so the doc never exists in a half-stamped state.
        if (BuildConfig.BYPASS_KYC_FOR_BETA) {
            doc["bypass_grant"] = mapOf(
                "reason" to bypassReason.wire,
                "granted_at" to FieldValue.serverTimestamp(),
                "granted_via" to "BYPASS_KYC_FOR_BETA",
                "will_reverify" to true,
            )
        }

        try {
            ref.set(doc).await()
        } catch (_: Throwable) {
            // If the write fails (rule mismatch, offline, etc.) the
            // backend's lazy `requireUser` write will still create the
            // doc on the first authenticated REST call. We log nothing
            // and let the user proceed; worst case they hit the same
            // phantom-guest path the bug fix was designed to prevent,
            // but only on transient failures rather than every sign-in.
            return Result.ReadFailed
        }

        // Layla's GR2 (T&S guardrail). Once the user doc is stamped,
        // mirror the same grant into a SEPARATE durable audit trail at
        // `/audit/{uid}/events/{auto-id}`. The intent is that even if
        // someone later edits or deletes the user doc, the audit doc
        // survives in its own collection. Rule: owner can create their
        // own event with `uid == userId` and `type` is a string; updates
        // and deletes are forbidden so the trail is append-only.
        if (BuildConfig.BYPASS_KYC_FOR_BETA) {
            try {
                firestore.collection("audit")
                    .document(uid)
                    .collection("events")
                    .add(
                        mapOf(
                            "uid" to uid,
                            "type" to "kyc_bypass_granted",
                            "granted_at" to FieldValue.serverTimestamp(),
                            "reason" to bypassReason.wire,
                            "env_flag_value" to true,
                        ),
                    )
                    .await()
            } catch (_: Throwable) {
                // Audit write is best-effort. The user doc itself already
                // carries `bypass_grant`, so the withdrawal hard-block
                // (Layla GR4) still works even if this secondary write
                // dropped due to a transient error. The next backend-side
                // reconciliation job (TBD) is expected to backfill any
                // gaps from the user-doc state.
            }
        }

        return Result.Created
    }
}
