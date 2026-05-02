package com.halqa.app.data

import com.halqa.app.domain.StaffAccount
import com.halqa.app.domain.UserRole

/**
 * In-memory directory of staff accounts used until the real auth backend
 * (Khalid / TRA-7) is online. The passwords below are *demo* credentials
 * scoped to local debug builds — they are not real secrets and they will be
 * removed entirely once [AuthRepository] is pointed at the production
 * endpoint. Keeping them in plain code is a deliberate trade-off: the QA
 * matrix needs to be able to sign in as every role without out-of-band
 * coordination, and the alternative (env vars / build configs) just moves
 * the same string into a different file.
 *
 * Do NOT add real customer accounts here.
 */
internal data class MockStaffCredentials(
    val account: StaffAccount,
    val password: String,
)

internal object MockStaffDirectory {
    val accounts: List<MockStaffCredentials> = listOf(
        MockStaffCredentials(
            account = StaffAccount(
                id = "u_admin_001",
                email = "admin@halqa.app",
                displayName = "علي (المالك)",
                role = UserRole.Admin,
            ),
            password = "Admin#2025",
        ),
        MockStaffCredentials(
            account = StaffAccount(
                id = "u_staff_001",
                email = "staff@halqa.app",
                displayName = "موظف عمليات",
                role = UserRole.Staff,
            ),
            password = "Staff#2025",
        ),
        MockStaffCredentials(
            account = StaffAccount(
                id = "u_mod_001",
                email = "mod@halqa.app",
                displayName = "محمد (مراقب)",
                role = UserRole.Moderator,
            ),
            password = "Mod#2025",
        ),
        MockStaffCredentials(
            account = StaffAccount(
                id = "u_scout_001",
                email = "scout@halqa.app",
                displayName = "فيصل (صياد)",
                role = UserRole.Scout,
            ),
            password = "Scout#2025",
        ),
    )

    fun findByEmail(email: String): MockStaffCredentials? =
        accounts.firstOrNull { it.account.email.equals(email, ignoreCase = true) }
}
