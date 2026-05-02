package com.halqa.app.data

import com.halqa.app.domain.AuthFailure
import com.halqa.app.domain.AuthResult
import com.halqa.app.domain.StaffAccount
import com.halqa.app.domain.StaffAction
import com.halqa.app.domain.StaffActionType
import com.halqa.app.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the authenticated staff session. The repository
 * owns:
 *
 *  1. The reactive [currentAccount] StateFlow so any composable can observe
 *     sign-in / sign-out without a singleton-shaped global.
 *  2. Persistence (delegated to [AuthPrefs]) so the session survives cold
 *     starts.
 *  3. An in-memory append-only [auditLog]. In production this will be a
 *     mirror of the server log; for now it lets Phase C/D/E screens be built
 *     against the final shape before the backend lands.
 *
 * Call [bootstrap] exactly once on app start (from `HalqaApplication`) to
 * rehydrate the session from disk.
 */
object AuthRepository {

    private val _currentAccount = MutableStateFlow<StaffAccount?>(null)
    val currentAccount: StateFlow<StaffAccount?> = _currentAccount.asStateFlow()

    private val _auditLog = MutableStateFlow<List<StaffAction>>(emptyList())
    val auditLog: StateFlow<List<StaffAction>> = _auditLog.asStateFlow()

    /** Effective role for the current session, defaulting to [UserRole.Guest]. */
    val currentRole: UserRole
        get() = _currentAccount.value?.role ?: UserRole.Guest

    fun bootstrap() {
        _currentAccount.value = AuthPrefs.loadAccount()
    }

    /**
     * Email + password sign-in. Today this checks against [MockStaffDirectory];
     * the public signature already matches what the backend call will look
     * like so the call sites do not change when the implementation flips.
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult {
        val trimmed = email.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            return AuthResult.Failure(AuthFailure.InvalidCredentials)
        }
        val match = MockStaffDirectory.findByEmail(trimmed)
            ?: return AuthResult.Failure(AuthFailure.InvalidCredentials)
        if (match.password != password) {
            return AuthResult.Failure(AuthFailure.InvalidCredentials)
        }
        adoptSession(match.account)
        recordAction(
            type = StaffActionType.SignIn,
            targetId = null,
            notes = "تسجيل دخول من شاشة الموظفين",
        )
        return AuthResult.Success(match.account)
    }

    fun signOut() {
        val previous = _currentAccount.value ?: return
        recordAction(
            actor = previous,
            type = StaffActionType.SignOut,
            targetId = null,
            notes = "تسجيل خروج",
        )
        _currentAccount.value = null
        AuthPrefs.clearAccount()
    }

    /**
     * Public hook so domain code (Mod queue decisions, Admin role grants, …)
     * can append to the audit log without re-implementing the bookkeeping.
     */
    fun recordAction(
        type: StaffActionType,
        targetId: String?,
        notes: String,
    ) {
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
}
