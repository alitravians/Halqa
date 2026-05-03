package com.halqa.app.data

import com.google.firebase.firestore.FirebaseFirestore
import com.halqa.app.domain.AuthFailure
import com.halqa.app.domain.AuthResult
import com.halqa.app.domain.StaffAccount
import com.halqa.app.domain.StaffAction
import com.halqa.app.domain.StaffActionType
import com.halqa.app.domain.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _currentAccount = MutableStateFlow<StaffAccount?>(null)
    val currentAccount: StateFlow<StaffAccount?> = _currentAccount.asStateFlow()

    private val _auditLog = MutableStateFlow<List<StaffAction>>(emptyList())
    val auditLog: StateFlow<List<StaffAction>> = _auditLog.asStateFlow()

    val currentRole: UserRole
        get() = _currentAccount.value?.role ?: UserRole.Guest

    /**
     * App start hook. Loads the cached [StaffAccount] from prefs synchronously
     * so the UI does not flicker between Guest → Staff while we re-validate,
     * then re-validates the role from Firestore in the background.
     *
     * The synchronous-cache path is what previously made stale-role attacks
     * possible. PR #35 closed the email-seed escalation; this method closes
     * the persistence-of-elevated-role escalation:
     *
     *   1. Admin demotes a previously-staff user to "user" via the Admin
     *      Panel (Firestore /users/{uid}.role = "user").
     *   2. The user's device still has the old `role: "staff"` cached in
     *      AuthPrefs from their last sign-in.
     *   3. They open the app. `RoleGate(check = { it.hasStaffPower })`
     *      grants the staff UI based on the stale cache. The backend
     *      still rejects every privileged action (firestore.rules + the
     *      `isStaff()` checks in `requireUser`), but the staff/admin
     *      *surface* (audit-log tools, role-gated navigation entries,
     *      [StaffHomeScreen]) is exposed — information disclosure, same
     *      class of bug as the seed-map issue PR #35 fixed.
     *
     * Re-validation rules:
     *   - Firebase Auth currentUser is null OR its uid doesn't match the
     *     cached account → cache is dead (signed out elsewhere, token
     *     revoked, password reset, account disabled, account swapped) →
     *     drop it.
     *   - Firestore `/users/{uid}.role` is missing or differs from the
     *     cache → adopt the Firestore value (it's authoritative).
     */
    fun bootstrap() {
        val cached = AuthPrefs.loadAccount()
        _currentAccount.value = cached
        if (cached != null) {
            scope.launch { revalidateCachedAccount(cached) }
        }
    }

    private suspend fun revalidateCachedAccount(cached: StaffAccount) {
        val firebaseUid = FirebaseAuthRepository.currentUser?.uid
        if (firebaseUid == null || firebaseUid != cached.id) {
            _currentAccount.value = null
            AuthPrefs.clearAccount()
            return
        }
        val freshRole = resolveRoleForUid(cached.id)
        if (freshRole != cached.role) {
            val updated = cached.copy(role = freshRole)
            _currentAccount.value = updated
            AuthPrefs.saveAccount(updated)
        }
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
        val role = resolveRoleForUid(user.uid)
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
     * Role lookup. Strict server-side authority:
     *  - Try Firestore `/users/{uid}.role`. Whatever the backend wrote
     *    there is the truth.
     *  - If the doc is missing OR has no `role` field OR the read
     *    fails (offline, transient Firestore error, permission denied
     *    while rules propagate), default to [UserRole.User] — the
     *    least-privileged value.
     *
     * Why no email-based fallback:
     *
     * The previous implementation seeded roles client-side by matching
     * the signed-in email against a hardcoded map (admin@halqa.app →
     * Admin, etc.) whenever the Firestore role read returned nothing.
     * That was a privilege-confusion vector: Firebase Auth doesn't
     * verify email ownership at sign-up, and
     * `signInOrCreateWithEmail` auto-creates accounts the first time
     * they're seen. So anyone who could reach the staff sign-in
     * screen and type `admin@halqa.app` with any password would, on
     * the very first attempt (before the backend's `requireUser`
     * had a chance to write a `role: "user"` doc), get
     * `UserRole.Admin` on the client. The backend / firestore rules
     * reject the actual privileged actions, but the UI exposes the
     * admin surface (staff home, audit-log tools, role-gated
     * navigation entries) — which is information disclosure on its
     * own and ammunition for follow-on attacks.
     *
     * Staff bootstrap is a server-side concern. The intended flow is:
     *
     *   1. Real human creates the staff account in Firebase Console.
     *   2. Admin runs an Admin-Panel action (or `firestore` write
     *      with the Admin SDK) to set `/users/{uid}.role = "admin"`.
     *   3. Staff signs in. Their `/users/{uid}` doc already exists
     *      with the correct role.
     *
     * For closed-beta we accept that a freshly-created staff account
     * which has not yet been promoted will see the regular-user UI
     * — that's the safe default and the user-visible fix is "ask
     * an existing admin to promote you", not "type the magic email".
     */
    private suspend fun resolveRoleForUid(uid: String): UserRole {
        return try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users").document(uid).get().await()
            val key = snap.getString("role")
            if (key.isNullOrBlank()) {
                UserRole.User
            } else {
                UserRole.fromKey(key) ?: UserRole.User
            }
        } catch (_: Throwable) {
            UserRole.User
        }
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
