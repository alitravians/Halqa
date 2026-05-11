package com.halqa.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.halqa.app.data.FirebaseAuthRepository
import com.halqa.app.data.UserRepository
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.SettingsDto
import com.halqa.app.data.remote.humanize
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch

/**
 * General app settings — language, theme, notifications, and privacy.
 *
 * Reads use a Firestore listener so a setting toggled on one device shows up
 * instantly on every other device. Writes go through the backend so the
 * audit log stays correct.
 */
@Composable
fun SettingsScreen(navController: NavController) {
    val firebaseUser by FirebaseAuthRepository.authStateFlow().collectAsState(initial = FirebaseAuthRepository.currentUser)
    val uid = firebaseUser?.uid

    if (uid == null) {
        SignInRequired(navController, title = "الإعدادات العامة")
        return
    }

    val live by UserRepository.observeSettings(uid).collectAsStateWithLifecycle(initialValue = SettingsDto())
    var working by remember { mutableStateOf(live) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(live) { working = live }

    // Reem — account-deletion dialog state hoisted to the screen
    // composable so a recomposition doesn't dismiss a confirmation
    // mid-flight. `isDeleting` blocks dismissal while the DELETE
    // call is in flight so the user can't double-tap-cancel into a
    // half-deleted state.
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun mutate(update: (SettingsDto) -> SettingsDto) {
        val next = update(working)
        working = next
        saveError = null
        scope.launch {
            try {
                UserRepository.updateSettings(next)
            } catch (t: Throwable) {
                saveError = "تعذّر مزامنة الإعدادات: ${t.humanize()}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ProfileHeader(title = "الإعدادات العامة", onBack = { navController.popBackStack() })

        Spacer(Modifier.height(8.dp))
        SectionLabel("اللغة")
        SegmentedRow(
            options = listOf("ar" to "العربية", "en" to "English"),
            selected = working.language,
            onSelect = { v -> mutate { it.copy(language = v) } },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("المظهر")
        SegmentedRow(
            options = listOf("auto" to "تلقائي", "dark" to "داكن", "light" to "فاتح"),
            selected = working.theme,
            onSelect = { v -> mutate { it.copy(theme = v) } },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("الإشعارات")
        ToggleRow(
            label = "إشعارات Push",
            checked = working.notificationsPush,
            onChange = { v -> mutate { it.copy(notificationsPush = v) } },
        )
        ToggleRow(
            label = "إشعارات البريد",
            checked = working.notificationsEmail,
            onChange = { v -> mutate { it.copy(notificationsEmail = v) } },
        )

        Spacer(Modifier.height(20.dp))
        SectionLabel("الخصوصية")
        ToggleRow(
            label = "إظهار حالة الاتصال",
            checked = working.privacyShowOnline,
            onChange = { v -> mutate { it.copy(privacyShowOnline = v) } },
        )
        SectionLabel("من يستطيع مراسلتي")
        // The backend's authoritative allow-list in `settings/route.ts:77`
        // is `["everyone", "followers", "none"]`. The Android UI was
        // sending the key "nobody" for "لا أحد", which the backend
        // rejected as `400 "privacyAllowMessages invalid."` — every user
        // who picked "لا أحد" silently failed to save with an English
        // error surfaced via `humanize()`. Use "none" to match the
        // backend.
        SegmentedRow(
            options = listOf(
                "everyone" to "الجميع",
                "followers" to "متابعيني",
                "none" to "لا أحد",
            ),
            selected = working.privacyAllowMessages,
            onSelect = { v -> mutate { it.copy(privacyAllowMessages = v) } },
        )

        Spacer(Modifier.height(20.dp))
        saveError?.let {
            Text(it, color = HalqaColors.Pink, fontSize = 13.sp)
        }

        // Reem — Play 2024 in-app account deletion. Placed at the very
        // bottom under منطقة خطرة (Danger Zone) so users don't
        // mistake it for a settings toggle. The Play 2024 policy
        // requires a path to deletion (and data downgrade) that's
        // reachable from inside the app without contacting support,
        // and console reviewers explicitly look for this surface
        // during the privacy review.
        Spacer(Modifier.height(28.dp))
        SectionLabel("منطقة خطرة")
        DangerZone(
            onDeleteRequested = { showDeleteDialog = true },
        )
        Spacer(Modifier.height(40.dp))
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            isLoading = isDeleting,
            error = deleteError,
            onConfirm = {
                deleteError = null
                isDeleting = true
                scope.launch {
                    val result = runCatching { ApiClient.api.deleteMe() }
                    isDeleting = false
                    result.onSuccess {
                        showDeleteDialog = false
                        // Sign out locally so the next composition of
                        // [AuthScreen] doesn't see a stale Firebase
                        // currentUser. The server has already revoked
                        // the session via Admin SDK so the cached ID
                        // token would 401 on its next refresh anyway,
                        // but explicit sign-out is cheaper than
                        // bouncing through 401 + AuthAuthenticator.
                        runCatching { FirebaseAuthRepository.signOut() }
                        // Pop everything back to AuthScreen. `inclusive=true`
                        // on the *current* destination ensures Settings
                        // is also dropped — a stale Settings under
                        // AuthScreen would briefly recompose with the
                        // post-sign-out null uid and flash the
                        // SignInRequired screen.
                        navController.navigate(Routes.Auth) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }.onFailure { t ->
                        deleteError = t.humanize(fallback = "تعذّر حذف الحساب. جرّب لاحقاً.")
                    }
                }
            },
            onDismiss = {
                if (!isDeleting) {
                    showDeleteDialog = false
                    deleteError = null
                }
            },
        )
    }
}

@Composable
private fun DangerZone(onDeleteRequested: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Pink.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onDeleteRequested)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                "حذف الحساب",
                color = HalqaColors.Pink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "سيتم حذف حسابك وبياناتك بشكل دائم. لا يمكن التراجع عن هذا الإجراء.",
                color = HalqaColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    isLoading: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "تأكيد حذف الحساب",
                color = HalqaColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column {
                Text(
                    "سيتم حذف البيانات التالية بشكل دائم:",
                    color = HalqaColors.TextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                BulletLine("الملف الشخصي والصورة")
                BulletLine("المحفظة ورصيد الكوينز")
                BulletLine("سجل البثوثات والهدايا")
                BulletLine("سجل المحادثات والرسائل")
                Spacer(Modifier.height(10.dp))
                Text(
                    "لا يمكن التراجع عن هذا الإجراء بعد إتمامه.",
                    color = HalqaColors.Pink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = HalqaColors.Pink, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = HalqaColors.Pink,
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = onConfirm) {
                    Text(
                        "حذف حسابي نهائياً",
                        color = HalqaColors.Pink,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("إلغاء", color = HalqaColors.TextMuted)
            }
        },
        containerColor = HalqaColors.BgElevated,
    )
}

@Composable
private fun BulletLine(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("•", color = HalqaColors.TextDim, fontSize = 14.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, color = HalqaColors.Text, fontSize = 13.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = HalqaColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp, top = 4.dp),
    )
}

@Composable
private fun SegmentedRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val isSelected = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) HalqaColors.Brand else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else HalqaColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HalqaColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HalqaColors.Brand,
                uncheckedThumbColor = HalqaColors.TextMuted,
                uncheckedTrackColor = HalqaColors.Border,
            ),
        )
    }
}
