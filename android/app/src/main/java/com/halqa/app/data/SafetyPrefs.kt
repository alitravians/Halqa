package com.halqa.app.data

/**
 * Lightweight in-memory holder for the user's Go-Live age-gate acknowledgement.
 *
 * **This is intentionally not persisted to disk yet.** Phase B introduces the
 * real User repository (with DataStore + the eventual server-side User model)
 * at which point this object will be replaced by `UserRepository.hasAcceptedAgeGate`.
 *
 * Keeping this as a process-scoped flag during Phase F means:
 *   - The age-gate appears on every fresh app launch (good for safety).
 *   - We don't mutate user-visible storage before the proper schema exists,
 *     avoiding the data-loss patterns the user has explicitly warned about.
 */
object SafetyPrefs {
    @Volatile
    private var ageGateAccepted: Boolean = false

    fun hasAcceptedAgeGate(): Boolean = ageGateAccepted

    fun acceptAgeGate() {
        ageGateAccepted = true
    }

    /** For tests / debug builds — never call from production code paths. */
    fun resetForTesting() {
        ageGateAccepted = false
    }
}
