package com.halqa.app.data

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
     */
    suspend fun ensureUserDoc(
        uid: String,
        phoneNumber: String?,
        email: String?,
    ) {
        val firestore = FirebaseFirestore.getInstance()
        val ref = firestore.collection("users").document(uid)
        val snap = try {
            ref.get().await()
        } catch (t: Throwable) {
            // Fail-open: if the read fails (offline, transient Firestore
            // error), the backend will eventually fill the doc on its
            // first authenticated call. We do NOT want to block sign-in
            // on a transient read.
            return
        }

        if (snap.exists()) {
            // Doc already exists — patch contact channels only. Never
            // touch role / createdAt / uid (the rules forbid it on the
            // self-update path, and we don't want to anyway). Use
            // `update` with the specific fields we care about; if those
            // fields were already set, this is effectively a no-op.
            val patch = mutableMapOf<String, Any>()
            if (!phoneNumber.isNullOrBlank() && (snap.getString("phoneNumber").isNullOrBlank())) {
                patch["phoneNumber"] = phoneNumber
            }
            if (!email.isNullOrBlank() && (snap.getString("email").isNullOrBlank())) {
                patch["email"] = email
            }
            if (patch.isNotEmpty()) {
                patch["updatedAt"] = FieldValue.serverTimestamp()
                try {
                    ref.update(patch).await()
                } catch (_: Throwable) {
                    // Patch is best-effort; on rule rejection or transient
                    // error the backend's /api/users/me update path is
                    // authoritative anyway.
                }
            }
            return
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
            "displayName" to "",
            "handle" to "",
            "bio" to "",
            "avatar" to "",
        )
        if (!phoneNumber.isNullOrBlank()) doc["phoneNumber"] = phoneNumber
        if (!email.isNullOrBlank()) doc["email"] = email

        try {
            ref.set(doc).await()
        } catch (_: Throwable) {
            // If the write fails (rule mismatch, offline, etc.) the
            // backend's lazy `requireUser` write will still create the
            // doc on the first authenticated REST call. We log nothing
            // and let the user proceed; worst case they hit the same
            // phantom-guest path the bug fix was designed to prevent,
            // but only on transient failures rather than every sign-in.
        }
    }
}
