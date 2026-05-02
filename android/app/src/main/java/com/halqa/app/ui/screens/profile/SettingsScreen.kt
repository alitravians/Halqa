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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.halqa.app.data.remote.SettingsDto
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

    fun mutate(update: (SettingsDto) -> SettingsDto) {
        val next = update(working)
        working = next
        saveError = null
        scope.launch {
            try {
                UserRepository.updateSettings(next)
            } catch (t: Throwable) {
                saveError = "تعذّر مزامنة الإعدادات: ${t.message ?: "خطأ"}"
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
        SegmentedRow(
            options = listOf(
                "everyone" to "الجميع",
                "followers" to "متابعيني",
                "nobody" to "لا أحد",
            ),
            selected = working.privacyAllowMessages,
            onSelect = { v -> mutate { it.copy(privacyAllowMessages = v) } },
        )

        Spacer(Modifier.height(20.dp))
        saveError?.let {
            Text(it, color = HalqaColors.Pink, fontSize = 13.sp)
        }
        Spacer(Modifier.height(40.dp))
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
