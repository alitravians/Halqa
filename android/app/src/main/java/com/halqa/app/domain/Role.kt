package com.halqa.app.domain

/**
 * Authorisation roles for the Halqa platform. Each role has a numeric [rank]
 * so callers can express minimum-rank requirements with [atLeast] without
 * hard-coding role lists at every call site.
 *
 * Hierarchy (low → high):
 *   Guest < User < Scout ≈ Moderator < Staff < Admin
 *
 * Scout and Moderator share the same numeric rank because they are *parallel*
 * staff functions (one curates content, the other enforces policy); neither
 * is a superset of the other. Use [hasStaffPower] when a screen should be
 * visible to *any* staff-level role rather than checking ranks directly.
 *
 * The role is intentionally separate from [Badge] (visual presentation) —
 * a user can have many badges but exactly one effective role at a time.
 */
enum class UserRole(val rank: Int, val arabicLabel: String) {
    Guest(0, "زائر"),
    User(10, "مستخدم"),
    Scout(20, "صياد"),
    Moderator(20, "مراقب"),
    Staff(30, "موظف"),
    Admin(100, "مالك"),
    ;

    /** True when this role's [rank] is greater than or equal to [other]'s. */
    fun atLeast(other: UserRole): Boolean = this.rank >= other.rank

    /** True when this role can access *any* staff-restricted screen. */
    val hasStaffPower: Boolean
        get() = this == Scout || this == Moderator || this == Staff || this == Admin

    companion object
}
