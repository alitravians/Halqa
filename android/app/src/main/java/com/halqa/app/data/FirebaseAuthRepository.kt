package com.halqa.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Real Firebase Auth wrapper. Replaces [MockStaffDirectory] for staff sign-in
 * and adds regular-user phone/email sign-in for Halqa users.
 *
 * Outcomes:
 *  - sign-in success → [FirebaseUser] is the live currentUser, ID token is
 *    available for backend calls via [AuthInterceptor].
 *  - sign-in failure → throws (call site catches & maps to UI error string).
 */
object FirebaseAuthRepository {

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    /** Reactive Firebase Auth state — emits the current user (or null) on every change. */
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseUser {
        val res = auth.signInWithEmailAndPassword(email.trim(), password).await()
        return res.user ?: error("Firebase sign-in returned null user")
    }

    /**
     * Lazily creates a user with the supplied email/password if it does not
     * exist yet, then signs in. Used for the very first staff bootstrap so
     * Ali can ship without manually provisioning every account in the Firebase
     * Console.
     *
     * Modern Firebase SDKs (>= 22.x) collapse "user not found" and "wrong
     * password" into a single `FirebaseAuthInvalidCredentialsException` to
     * prevent user enumeration. We therefore probe with
     * `fetchSignInMethodsForEmail`: if there are no providers, the email is
     * unregistered and we create; if there are, we surface the original
     * sign-in failure (most likely a wrong password) untouched.
     */
    suspend fun signInOrCreateWithEmail(email: String, password: String): FirebaseUser {
        val trimmed = email.trim()
        return try {
            signInWithEmail(trimmed, password)
        } catch (
            t: com.google.firebase.auth.FirebaseAuthException,
        ) {
            val isUnknownUser = when (t) {
                is com.google.firebase.auth.FirebaseAuthInvalidUserException -> true
                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                    !accountExists(trimmed)
                else -> false
            }
            if (isUnknownUser) {
                val res = auth.createUserWithEmailAndPassword(trimmed, password).await()
                res.user ?: error("Firebase user creation returned null")
            } else {
                throw t
            }
        }
    }

    /**
     * Returns true when Firebase Auth has at least one provider record for
     * [email] (i.e. the account exists). Used to disambiguate the unified
     * INVALID_LOGIN_CREDENTIALS error.
     */
    private suspend fun accountExists(email: String): Boolean {
        return try {
            val res = auth.fetchSignInMethodsForEmail(email).await()
            !res.signInMethods.isNullOrEmpty()
        } catch (_: Throwable) {
            // Network or other failure — assume the user *might* exist; let
            // the original error bubble up rather than silently overwriting.
            true
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun freshIdToken(): String? =
        auth.currentUser?.getIdToken(true)?.await()?.token
}
