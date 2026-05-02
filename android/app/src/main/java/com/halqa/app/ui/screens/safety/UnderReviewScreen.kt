package com.halqa.app.ui.screens.safety

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.halqa.app.domain.AUTO_REVIEW_WINDOW_MS
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.delay

/**
 * Shown to the user whose stream was auto-flagged. The 10-minute review
 * window is rendered as a count-down timer + progress bar so the wait is
 * transparent. After the window closes, the user receives a second inbox
 * message routing them to [ReviewResultScreen].
 */
@Composable
fun UnderReviewScreen(navController: NavController) {
    // `SystemMessages.demoOpenReview` is now a `get`-property that captures
    // `System.currentTimeMillis()` at every read, so we MUST cache it through
    // `remember` here. Otherwise the per-second `now` state update below would
    // re-run this composable, fetch a brand-new review whose
    // `autoCloseAtEpochMs` is again 10 minutes in the future, and freeze the
    // countdown at ~10:00 forever.
    val review = remember { SystemMessages.demoOpenReview }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (now < review.autoCloseAtEpochMs) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val remainingMs = (review.autoCloseAtEpochMs - now).coerceAtLeast(0)
    val progress = 1f - (remainingMs.toFloat() / AUTO_REVIEW_WINDOW_MS.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "review-progress",
    )

    val mins = (remainingMs / 1000) / 60
    val secs = (remainingMs / 1000) % 60
    val mmss = "%02d:%02d".format(mins, secs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(HalqaColors.Warning.copy(alpha = 0.18f))
                    .border(1.dp, HalqaColors.Warning.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = HalqaColors.Warning, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("بثك تحت المراجعة", color = HalqaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("نظام المراقبة الذاتي يحلّل البث الآن", color = HalqaColors.TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(28.dp))

        // Big mm:ss countdown
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1F1144), Color(0xFF0A0A1A))))
                .border(1.dp, HalqaColors.Border, RoundedCornerShape(20.dp))
                .padding(vertical = 28.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.HourglassBottom, contentDescription = null, tint = HalqaColors.Gold, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(10.dp))
                Text("الوقت المتبقي للمراجعة", color = HalqaColors.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(mmss, color = HalqaColors.Text, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(14.dp))
                ProgressBar(progress = animatedProgress)
            }
        }

        Spacer(Modifier.height(20.dp))

        DetailCard(label = "المخالفة المُشتبه بها", value = review.suspectedCategory.labelAr)
        Spacer(Modifier.height(8.dp))
        DetailCard(label = "ملاحظات النظام", value = review.notesAr)
        Spacer(Modifier.height(8.dp))
        DetailCard(label = "حالة البث", value = "مُعلَّق مؤقتاً حتى انتهاء المراجعة")

        Spacer(Modifier.height(24.dp))
        Text(
            "ماذا يحدث الآن؟",
            color = HalqaColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "يقوم النظام بتحليل عيّنات من بثك خلال 10 دقائق، ويحدد نوع المخالفة وشدّتها بالاستناد إلى السياسة. بعد انتهاء المراجعة ستصلك رسالة في صندوق الوارد بالقرار النهائي ولك حق الاعتراض.",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.weight(1f))

        GhostButton(
            text = "تواصل مع الدعم",
            onClick = { /* TODO Phase E: open support ticket */ },
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(8.dp)
                .background(Brush.horizontalGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))),
        )
    }
}

@Composable
private fun DetailCard(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = HalqaColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = HalqaColors.Text, fontSize = 13.sp, lineHeight = 22.sp)
    }
}
