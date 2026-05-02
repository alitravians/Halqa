package com.halqa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.halqa.app.domain.AuthFailure
import com.halqa.app.domain.AuthResult
import com.halqa.app.domain.StaffAccount
import com.halqa.app.domain.StaffAction
import com.halqa.app.domain.StaffActionType
import com.halqa.app.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Real Firebase-backed staff session.
 *
 *  - Authenticates against Firebase Auth (Email + Password).
 *  - Looks up the user's role from /users/{uid}.role in Firestore.
 *  - Caches the resolved [StaffAccount] in [AuthPrefs] so cold-start is instant
 *    and offline-friendly.
 *
 * The MockStaffDirectory has been retired; staff accounts must be provisioned
 * through the Firebase Console (or the auto-bootstrap on first sign-in below).
 */
object AuthRepository {

    private val _currentAccount = MutableStateFlow<StaffAccount?>(null)
    val currentAccount: StateFlow<StaffAccount?> = _currentAccount.asStateFlow()

    private val _auditLog = MutableStateFlow<List<StaffAction>>(emptyList())
    val auditLog: StateFlow<List<StaffAction>> = _auditLog.asStateFlow()

    val currentRole: UserRole
        get() = _currentAccount.value?.role ?: UserRole.Guest

    fun bootstrap() {
        _currentAccount.value = AuthPrefs.loadAccount()
    }

    /**
     * Email + password sign-in. The first staff sign-in for a brand-new
     * deployment auto-creates the Firebase Auth user; subsequent sign-ins
     * just verify the password.
     *
     * Role assignment lives in Firestore at /users/{uid}.role. If the user
     * has no Firestore record yet, they get role=user — staff/admin roles
     * must be promoted from the Admin Panel (or via Firestore directly).
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val trimmed = email.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            return AuthResult.Failure(AuthFailure.InvalidCredentials)
        }
        val user = try {
            FirebaseAuthRepository.signInOrCreateWithEmail(trimmed, password)
        } catch (t: Throwable) {
            return AuthResult.Failure(mapAuthFailure(t))
        }
        val role = resolveRoleForUid(user.uid, fallbackEmail = trimmed)
        val account = StaffAccount(
            id = user.uid,
            email = user.email ?: trimmed,
            displayName = (user.displayName ?: trimmed.substringBefore('@'))
                .ifBlank { trimmed.substringBefore('@') },
            role = role,
        )
        adoptSession(account)
        recordAction(
            type = StaffActionType.SignIn,
            targetId = null,
            notes = "تسجيل دخول من شاشة الموظفين",
        )
        return AuthResult.Success(account)
    }

    suspend fun signOut() {
        val previous = _currentAccount.value
        if (previous != null) {
            recordAction(
                actor = previous,
                type = StaffActionType.SignOut,
                targetId = null,
                notes = "تسجيل خروج",
            )
        }
        _currentAccount.value = null
        _auditLog.value = emptyList()
        AuthPrefs.clearAccount()
        try {
            FirebaseAuthRepository.signOut()
        } catch (_: Throwable) {
            // Already signed out.
        }
    }

    fun recordAction(type: StaffActionType, targetId: String?, notes: String) {
        val actor = _currentAccount.value ?: return
        recordAction(actor = actor, type = type, targetId = targetId, notes = notes)
    }

    private fun recordAction(
        actor: StaffAccount,
        type: StaffActionType,
        targetId: String?,
        notes: String,
    ) {
        val entry = StaffAction(
            id = "act_" + System.currentTimeMillis() + "_" + (1000..9999).random(),
            actorId = actor.id,
            actorRole = actor.role,
            action = type,
            targetId = targetId,
            notes = notes,
            atEpochMs = System.currentTimeMillis(),
        )
        _auditLog.value = _auditLog.value + entry
    }

    private fun adoptSession(account: StaffAccount) {
        _currentAccount.value = account
        AuthPrefs.saveAccount(account)
    }

    /**
     * Role lookup with a small offline grace policy:
     *  - Try Firestore /users/{uid}.role first.
     *  - If the doc is missing OR has no `role` field, fall back to the
     *    well-known seed emails (admin@halqa.app etc.) so first-time staff
     *    sign-in works without manually pre-creating Firestore docs.
     *  - On Firestore I/O failure, also fall back to seed-by-email.
     *  - Default: UserRole.User.
     */
    private suspend fun resolveRoleForUid(uid: String, fallbackEmail: String): UserRole {
        return try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users").document(uid).get().await()
            val key = snap.getString("role")
            if (key.isNullOrBlank()) {
                // No role doc yet — first sign-in. Use the seed map so the
                // ali-provided staff bootstrap accounts get the correct role
                // without admin-panel intervention.
                seedRoleForEmail(fallbackEmail)
            } else {
                UserRole.fromKey(key) ?: UserRole.User
            }
        } catch (_: Throwable) {
            seedRoleForEmail(fallbackEmail)
        }
    }

    private fun seedRoleForEmail(email: String): UserRole = when (email.lowercase()) {
        "admin@halqa.app" -> UserRole.Admin
        "staff@halqa.app" -> UserRole.Staff
        "mod@halqa.app" -> UserRole.Moderator
        "scout@halqa.app" -> UserRole.Scout
        else -> UserRole.User
    }

    /**
     * Maps a Firebase Auth (or transport-layer) exception into the small set
     * of [AuthFailure] reasons the UI knows how to render. Kept exception-type
     * driven rather than message-string driven — Firebase localises messages
     * by device locale, so substring matching is fragile.
     */
    private fun mapAuthFailure(t: Throwable): AuthFailure {
        return when (t) {
            is com.google.firebase.auth.FirebaseAuthInvalidUserException,
            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException,
            is com.google.firebase.auth.FirebaseAuthWeakPasswordException,
            is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                AuthFailure.InvalidCredentials
            is com.google.firebase.FirebaseNetworkException ->
                AuthFailure.Network
            is com.google.firebase.FirebaseTooManyRequestsException ->
                AuthFailure.Network
            else -> {
                // Last-resort string sniff so genuinely opaque Firebase errors
                // still get a human-readable mapping when we can.
                val msg = t.message.orEmpty().lowercase()
                when {
                    "disabled" in msg -> AuthFailure.AccountDisabled
                    "network" in msg || "timeout" in msg -> AuthFailure.Network
                    "wrong" in msg || "password" in msg || "credential" in msg ->
                        AuthFailure.InvalidCredentials
                    else -> AuthFailure.Unknown
                }
            }
        }
    }
}

/** Helper used in this file (and by debug tooling) to map back-end role keys. */
private fun UserRole.Companion.fromKey(key: String): UserRole? = when (key.lowercase()) {
    "guest" -> UserRole.Guest
    "user" -> UserRole.User
    "scout" -> UserRole.Scout
    "moderator" -> UserRole.Moderator
    "staff" -> UserRole.Staff
    "admin" -> UserRole.Admin
    else -> null
}
