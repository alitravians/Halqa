package com.halqa.app.ui.screens.inbox

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.SystemMessages
import com.halqa.app.domain.SystemMessage
import com.halqa.app.domain.SystemMessageKind
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

private enum class InboxFilter(val labelAr: String) {
    All(labelAr = "الكل"),
    Unread(labelAr = "غير مقروءة"),
    System(labelAr = "النظام"),
}

@Composable
fun InboxScreen(navController: NavController) {
    var filter by remember { mutableStateOf(InboxFilter.All) }
    val messages = remember { SystemMessages.seed }
    val visible = when (filter) {
        InboxFilter.All -> messages
        InboxFilter.Unread -> messages.filter { it.unread }
        InboxFilter.System -> messages.filter { it.kind != SystemMessageKind.Generic }
    }

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
            InboxFilter.entries.forEach { f ->
                FilterChip(
                    label = f.labelAr,
                    selected = f == filter,
                    onClick = { filter = f },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(visible, key = { it.id }) { item ->
                InboxRow(item = item, onClick = { onMessageTap(navController, item) })
            }
        }
    }
}

private fun onMessageTap(navController: NavController, item: SystemMessage) {
    when (item.kind) {
        SystemMessageKind.AutoReviewOpened -> navController.navigate(Routes.UnderReview)
        SystemMessageKind.AutoReviewResult -> navController.navigate(Routes.ReviewResult)
        SystemMessageKind.ModeratorAction,
        SystemMessageKind.Announcement,
        SystemMessageKind.Generic -> Unit
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else HalqaColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InboxRow(item: SystemMessage, onClick: () -> Unit) {
    val isSafety = item.kind == SystemMessageKind.AutoReviewOpened || item.kind == SystemMessageKind.AutoReviewResult
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isSafety) Brush.linearGradient(listOf(HalqaColors.Danger, HalqaColors.Pink))
                    else Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSafety) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            } else {
                Text(item.emoji, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.senderLabelAr,
                    color = HalqaColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isSafety) {
                    Spacer(Modifier.size(6.dp))
                    SafetyBadge()
                }
                Spacer(Modifier.weight(1f))
                Text(item.timeLabelAr, color = HalqaColors.TextDim, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                item.titleAr,
                color = HalqaColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.bodyAr,
                    color = HalqaColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                if (item.unread) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HalqaColors.Pink),
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(HalqaColors.Danger.copy(alpha = 0.18f))
            .border(1.dp, HalqaColors.Danger.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("سلامة", color = HalqaColors.Danger, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
