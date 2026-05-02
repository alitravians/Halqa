package com.halqa.app.ui.screens.inbox

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halqa.app.ui.theme.HalqaColors

private data class InboxItem(
    val name: String,
    val message: String,
    val time: String,
    val unread: Int = 0,
    val emoji: String,
)

private val items = listOf(
    InboxItem("نظام Halqa", "أهلاً بك في حلقة! اكمل ملفك الشخصي للحصول على هدية.", "الآن", 1, "🎁"),
    InboxItem("سعد القحطاني", "بثك أمس كان رهيب 🔥", "5د", 2, "🎤"),
    InboxItem("نوف الحربي", "متى البث القادم؟", "ساعتين", 0, "💬"),
    InboxItem("مذيعي المتابعون", "بدأ بث جديد", "3س", 4, "🔴"),
    InboxItem("فهد الدوسري", "PK اليوم؟", "أمس", 0, "⚔️"),
    InboxItem("Halqa Updates", "تحديث جديد: مينيقيمز للجمهور", "البارحة", 1, "✨"),
)

@Composable
fun InboxScreen() {
    Column(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "الرسائل",
                color = HalqaColors.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("الكل", true)
            FilterChip("غير مقروءة", false)
            FilterChip("النظام", false)
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { item -> InboxRow(item) }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) Color.White else HalqaColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InboxRow(item: InboxItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))),
            contentAlignment = Alignment.Center,
        ) {
            Text(item.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = HalqaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(item.time, color = HalqaColors.TextDim, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.message,
                    color = HalqaColors.TextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (item.unread > 0) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(HalqaColors.Pink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${item.unread}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(HalqaColors.Border),
    )
}
