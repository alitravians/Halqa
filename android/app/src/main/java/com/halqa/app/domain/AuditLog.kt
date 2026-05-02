package com.halqa.app.domain

/**
 * A single staff action recorded for the audit log. Every staff-power
 * decision (warn, suspend, restore, override AI verdict, change role …)
 * must produce one of these.
 *
 * The shape is intentionally narrow: a structured [action] enum + a free-form
 * [notes] field for the human-readable detail. The structured field lets the
 * Phase E admin panel filter without parsing strings.
 */
data class StaffAction(
    val id: String,
    val actorId: String,
    val actorRole: UserRole,
    val action: StaffActionType,
    val targetId: String?,
    val notes: String,
    val atEpochMs: Long,
)

enum class StaffActionType {
    SignIn,
    SignOut,
    WarnUser,
    SuspendUser,
    RestoreUser,
    ConfirmViolation,
    DismissViolation,
    EscalateToAdmin,
    OverrideAiVerdict,
    AssignRole,
    RevokeRole,
}
