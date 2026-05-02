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
            val msg = t.message.orEmpty().lowercase()
            val reason = when {
                "wrong" in msg || "invalid" in msg || "password" in msg -> AuthFailure.InvalidCredentials
                "disabled" in msg -> AuthFailure.AccountDisabled
                "network" in msg || "timeout" in msg -> AuthFailure.Network
                else -> AuthFailure.Unknown
            }
            return AuthResult.Failure(reason)
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
     *  - If that fails (offline first launch), fall back to the well-known
     *    seed emails so the original mock accounts keep working post-migration.
     *  - Otherwise default to UserRole.User.
     */
    private suspend fun resolveRoleForUid(uid: String, fallbackEmail: String): UserRole {
        return try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users").document(uid).get().await()
            val role = (snap.getString("role") ?: "user")
            UserRole.fromKey(role) ?: UserRole.User
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
