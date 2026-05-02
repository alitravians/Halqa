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
     */
    suspend fun signInOrCreateWithEmail(email: String, password: String): FirebaseUser {
        return try {
            signInWithEmail(email, password)
        } catch (_: com.google.firebase.auth.FirebaseAuthInvalidUserException) {
            val res = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            res.user ?: error("Firebase user creation returned null")
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun freshIdToken(): String? =
        auth.currentUser?.getIdToken(true)?.await()?.token
}
