package com.halqa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halqa.app.data.AuthRepository
import com.halqa.app.domain.UserRole
import com.halqa.app.ui.theme.HalqaColors

/**
 * Renders [content] only when the current session's role passes [check].
 *
 * Intentionally takes a predicate (rather than `minimumRole: UserRole`) so
 * callers can express more nuanced rules — e.g. "Scout *or* Moderator but
 * not Staff" — without the gate having to know the role hierarchy.
 *
 * When the check fails, a neutral "access denied" panel is shown instead of
 * navigating away. This keeps the user in their current navigation context
 * (so the bottom NavigationBar remains usable) and avoids the kind of
 * back-stack loops that plagued the AgeGate before PR #6.
 */
@Composable
fun RoleGate(
    check: (UserRole) -> Boolean,
    deniedTitle: String = "هذه الشاشة محجوزة للموظفين",
    deniedSubtitle: String = "هذا القسم متاح فقط للأدوار المخوّلة من الإدارة. إذا تعتقد أنك يجب أن تصل إلى هنا، تواصل مع الإدارة.",
    content: @Composable () -> Unit,
) {
    val account by AuthRepository.currentAccount.collectAsState()
    val role = account?.role ?: UserRole.Guest
    if (check(role)) {
        content()
    } else {
        AccessDeniedPanel(title = deniedTitle, subtitle = deniedSubtitle)
    }
}

/** Convenience wrapper: gate by minimum [UserRole.rank]. */
@Composable
fun RoleGateAtLeast(
    minimum: UserRole,
    content: @Composable () -> Unit,
) {
    RoleGate(check = { it.atLeast(minimum) }, content = content)
}

@Composable
private fun AccessDeniedPanel(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = HalqaColors.TextMuted,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            color = HalqaColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            color = HalqaColors.TextMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
