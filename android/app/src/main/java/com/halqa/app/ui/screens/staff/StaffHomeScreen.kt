package com.halqa.app.ui.screens.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.AuthRepository
import com.halqa.app.domain.StaffAction
import com.halqa.app.domain.StaffActionType
import com.halqa.app.domain.UserRole
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.RoleGate
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Landing screen after staff sign-in. Shows the active session, the upcoming
 * staff-only entry points (Moderator queue, Scout capture, Admin panel —
 * each gated by role and routed to the corresponding Phase C/D/E screen
 * once it lands), and a live preview of the audit log.
 *
 * Wrapping the whole screen in [RoleGate] is defence in depth: even if a
 * regular user somehow lands on `Routes.StaffHome`, the gate refuses to
 * render the contents and shows the neutral access-denied panel instead.
 */
@Composable
fun StaffHomeScreen(navController: NavController) {
    RoleGate(check = { it.hasStaffPower }) {
        StaffHomeContent(navController)
    }
}

@Composable
private fun StaffHomeContent(navController: NavController) {
    val account by AuthRepository.currentAccount.collectAsState()
    val auditLog by AuthRepository.auditLog.collectAsState()
    val signedIn = account ?: return
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = HalqaColors.Text,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "لوحة الموظفين",
                color = HalqaColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    scope.launch {
                        AuthRepository.signOut()
                        navController.navigate(Routes.Auth) {
                            popUpTo(Routes.StaffHome) { inclusive = true }
                        }
                    }
                },
            ) {
                Icon(
                    Icons.Filled.Logout,
                    contentDescription = "تسجيل خروج",
                    tint = HalqaColors.TextMuted,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(HalqaColors.BrandDark, HalqaColors.Brand),
                    ),
                )
                .padding(20.dp),
        ) {
            Column {
                Text(
                    signedIn.displayName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    signedIn.email,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "الدور: ${signedIn.role.arabicLabel}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "الأقسام المتاحة لك",
            color = HalqaColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        StaffEntry(
            title = "طابور المراقبة",
            subtitle = "الحالات المُشتبه بها بانتظار قرار بشري.",
            visibleTo = { it == UserRole.Moderator || it == UserRole.Staff || it == UserRole.Admin },
            currentRole = signedIn.role,
            comingSoonLabel = "قريباً (Phase C)",
        )
        Spacer(Modifier.height(10.dp))
        StaffEntry(
            title = "أدوات الصيّاد",
            subtitle = "التقاط مقاطع مميّزة من البثوث الحيّة.",
            visibleTo = { it == UserRole.Scout || it == UserRole.Admin },
            currentRole = signedIn.role,
            comingSoonLabel = "قريباً (Phase D)",
        )
        Spacer(Modifier.height(10.dp))
        StaffEntry(
            title = "لوحة الإدارة",
            subtitle = "إدارة الأدوار، الإعدادات، وتسجيلات النظام.",
            visibleTo = { it == UserRole.Admin },
            currentRole = signedIn.role,
            comingSoonLabel = "قريباً (Phase E)",
        )

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.HistoryEdu,
                contentDescription = null,
                tint = HalqaColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "آخر الإجراءات",
                color = HalqaColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(8.dp))

        if (auditLog.isEmpty()) {
            Text(
                "لا توجد إجراءات مسجّلة بعد. كل قرار تقوم به سيظهر هنا فوراً.",
                color = HalqaColors.TextMuted,
                fontSize = 13.sp,
            )
        } else {
            // Show the most recent first; cap at 10 to keep this screen
            // overview-only — full history will live on a dedicated screen.
            val recent = auditLog.takeLast(10).reversed()
            recent.forEach { action ->
                AuditLogRow(action)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        GhostButton(
            text = "العودة إلى التطبيق",
            onClick = { navController.popBackStack() },
        )
    }
}

@Composable
private fun StaffEntry(
    title: String,
    subtitle: String,
    visibleTo: (UserRole) -> Boolean,
    currentRole: UserRole,
    comingSoonLabel: String,
) {
    val authorised = visibleTo(currentRole)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (authorised) HalqaColors.BrandDark else HalqaColors.BgSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (authorised) Color.White else HalqaColors.TextDim,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = HalqaColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        color = HalqaColors.TextMuted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (authorised) comingSoonLabel else "غير مخوّل",
                    color = if (authorised) HalqaColors.GoldLight else HalqaColors.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun AuditLogRow(action: StaffAction) {
    val timestamp = remember(action.atEpochMs) {
        SimpleDateFormat("HH:mm:ss · yyyy/MM/dd", Locale("ar")).format(Date(action.atEpochMs))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    arabicForActionType(action.action),
                    color = HalqaColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    action.actorRole.arabicLabel,
                    color = HalqaColors.BrandLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                action.notes,
                color = HalqaColors.TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                timestamp,
                color = HalqaColors.TextDim,
                fontSize = 11.sp,
            )
        }
    }
}

private fun arabicForActionType(type: StaffActionType): String = when (type) {
    StaffActionType.SignIn -> "تسجيل دخول"
    StaffActionType.SignOut -> "تسجيل خروج"
    StaffActionType.WarnUser -> "تحذير مستخدم"
    StaffActionType.SuspendUser -> "إيقاف مستخدم"
    StaffActionType.RestoreUser -> "استعادة مستخدم"
    StaffActionType.ConfirmViolation -> "تأكيد مخالفة"
    StaffActionType.DismissViolation -> "تجاوز مخالفة"
    StaffActionType.EscalateToAdmin -> "تصعيد للإدارة"
    StaffActionType.OverrideAiVerdict -> "تجاوز قرار النظام"
    StaffActionType.AssignRole -> "تعيين دور"
    StaffActionType.RevokeRole -> "سحب دور"
}
