package com.halqa.app.ui.screens.safety

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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.SystemMessages
import com.halqa.app.domain.PenaltyTier
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.theme.HalqaColors

/**
 * Final verdict screen reached by tapping the [SystemMessageKind.AutoReviewResult]
 * inbox entry. Shows the determined violation category, the applied penalty,
 * and an "appeal" CTA which (in Phase E) opens an Admin-reviewable ticket.
 */
@Composable
fun ReviewResultScreen(navController: NavController) {
    val (category, penalty) = SystemMessages.demoVerdict

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
                    .background(HalqaColors.Danger.copy(alpha = 0.18f))
                    .border(1.dp, HalqaColors.Danger.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Gavel, contentDescription = null, tint = HalqaColors.Danger, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("نتيجة المراجعة", color = HalqaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("القرار النهائي صادر عن نظام المراقبة الذاتي", color = HalqaColors.TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        VerdictCard(
            title = "نوع المخالفة",
            value = category.labelAr,
        )
        Spacer(Modifier.height(10.dp))
        VerdictCard(
            title = "الإجراء المتخذ",
            value = penalty.labelAr,
            highlight = true,
        )
        Spacer(Modifier.height(10.dp))
        VerdictCard(
            title = "مدة الإجراء",
            value = when (penalty) {
                PenaltyTier.Warning -> "تحذير لا يترتب عليه حظر، لكنه يُسجَّل في ملفك."
                PenaltyTier.Ban24h -> "24 ساعة من تاريخ الإصدار."
                PenaltyTier.Ban7d -> "7 أيام من تاريخ الإصدار."
                PenaltyTier.Ban30d -> "30 يوم من تاريخ الإصدار."
                PenaltyTier.BanPermanent -> "حظر دائم، مع إحالة الحالة للجهات المختصة عند توفر شروط ذلك."
            },
        )

        Spacer(Modifier.height(20.dp))
        Text("هل ترى أن القرار غير دقيق؟", color = HalqaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "يحقّ لك تقديم اعتراض خلال 48 ساعة من إصدار القرار. سيراجع فريق الإدارة الاعتراض ويردّ خلال 72 ساعة.",
            color = HalqaColors.TextMuted,
            fontSize = 12.sp,
            lineHeight = 22.sp,
        )

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "تقديم اعتراض",
            onClick = { /* TODO Phase E: open appeal form */ },
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = "أقبل القرار وأرجع",
            onClick = { navController.popBackStack() },
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun VerdictCard(title: String, value: String, highlight: Boolean = false) {
    val border = if (highlight) HalqaColors.Danger.copy(alpha = 0.5f) else HalqaColors.Border
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (highlight) {
                Icon(Icons.Filled.Block, contentDescription = null, tint = HalqaColors.Danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
            }
            Text(title, color = HalqaColors.TextMuted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = if (highlight) HalqaColors.Danger else HalqaColors.Text,
            fontSize = if (highlight) 18.sp else 14.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            lineHeight = 24.sp,
        )
    }
}
