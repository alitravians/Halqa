package com.halqa.app.ui.screens.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.FirebaseAuthRepository
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.AuditEntryDto
import com.halqa.app.data.remote.humanize
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Stream + battle history backed by `GET /api/audit/{uid}`.
 *
 * Shows the user's own audit-log entries (stream_start, stream_end,
 * pk_match, …) so they can verify what was recorded under their identity.
 * Staff/admin can also use this view on themselves before consulting the
 * Admin Panel.
 */
@Composable
fun StreamHistoryScreen(navController: NavController) {
    val firebaseUser by FirebaseAuthRepository.authStateFlow().collectAsState(initial = FirebaseAuthRepository.currentUser)
    val uid = firebaseUser?.uid

    if (uid == null) {
        SignInRequired(navController, title = "سجل البث والمعارك")
        return
    }

    var entries by remember { mutableStateOf<List<AuditEntryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        try {
            val resp = ApiClient.api.audit(uid)
            entries = resp.entries
            error = null
        } catch (t: Throwable) {
            entries = emptyList()
            error = "تعذّر جلب السجل: ${t.humanize()}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .padding(16.dp),
    ) {
        ProfileHeader(title = "سجل البث والمعارك", onBack = { navController.popBackStack() })

        Spacer(Modifier.height(8.dp))
        when {
            entries == null && error == null -> {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HalqaColors.Brand)
                }
            }
            error != null -> {
                Text(error.orEmpty(), color = HalqaColors.Pink, fontSize = 13.sp)
            }
            entries.isNullOrEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("📜", fontSize = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "لا يوجد سجل حتى الآن",
                        color = HalqaColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ستظهر هنا حلقاتك ومعاركك السابقة فور تسجيلها.",
                        color = HalqaColors.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries.orEmpty(), key = { it.id }) { entry ->
                        AuditCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditCard(entry: AuditEntryDto) {
    val (label, emoji) = labelFor(entry.action)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.height(0.dp))
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = HalqaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            val streamTitle = (entry.metadata as? JsonObject)?.get("title")?.let { it as? JsonPrimitive }?.contentOrNull
            if (!streamTitle.isNullOrBlank()) {
                Text(streamTitle, color = HalqaColors.TextMuted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(entry.timestamp, color = HalqaColors.TextDim, fontSize = 11.sp)
        }
    }
}

private fun labelFor(action: String): Pair<String, String> = when (action) {
    "stream_start" -> "بدأ البث المباشر" to "🎬"
    "stream_end" -> "انتهى البث" to "🏁"
    "kyc_submit" -> "إرسال طلب توثيق" to "🪪"
    "kyc_approved" -> "تم قبول التوثيق" to "✅"
    "kyc_rejected" -> "رُفض التوثيق" to "⚠️"
    "profile_update" -> "تحديث الملف الشخصي" to "✏️"
    "settings_update" -> "تحديث الإعدادات" to "⚙️"
    "sign_in" -> "تسجيل دخول" to "🔓"
    "sign_out" -> "تسجيل خروج" to "🔒"
    "pk_start", "battle_start" -> "بدأت معركة" to "⚔️"
    "pk_end", "battle_end" -> "انتهت المعركة" to "🏆"
    else -> action to "📌"
}
