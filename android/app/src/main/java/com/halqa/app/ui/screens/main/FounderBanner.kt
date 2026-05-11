package com.halqa.app.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.halqa.app.data.OnboardingPrefs
import com.halqa.app.ui.theme.HalqaColors

/**
 * Lina — founder/VIP framing surfaced on the user's first reach of
 * `Main`. A single one-shot banner that says "you're one of the first
 * 5 founders shaping Halqa", anchored at the top of the feed.
 *
 * Lifecycle:
 *   - Reads [OnboardingPrefs.isFounderBannerShown] once at composition
 *     time; if `true`, the banner is not rendered at all (no flash).
 *   - The "إغلاق" button or tapping the banner body marks it shown
 *     via [OnboardingPrefs.markFounderBannerShown] so a re-launch
 *     does not re-surface it.
 *   - The flag is install-scoped (separate `halqa_onboarding_prefs`
 *     SharedPreferences file). A sign-out + sign-back-in on the same
 *     device does NOT re-show — that's intentional, the framing is
 *     about cohort membership, not session state.
 *
 * Copy: per Layla's review of the v0.1.23 string proposal — closed
 * beta is exactly 5 testers and the framing is "مؤسس" (founder)
 * rather than "VIP" because the latter implies a paid tier the closed
 * beta does not have. Layla approved the exact copy on 2026-05-02.
 */
@Composable
fun FounderBanner() {
    var dismissed by remember { mutableStateOf(OnboardingPrefs.isFounderBannerShown()) }

    if (dismissed) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        HalqaColors.Gold.copy(alpha = 0.20f),
                        HalqaColors.Brand.copy(alpha = 0.16f),
                    ),
                ),
            )
            .border(
                1.dp,
                HalqaColors.Gold.copy(alpha = 0.55f),
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(HalqaColors.Gold, HalqaColors.GoldLight),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("👑", fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "مرحباً أيها المؤسس",
                    color = HalqaColors.Gold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "أنت من ضمن أول 5 testers يشكلون Halqa. ملاحظاتك ترسم شكل التطبيق.",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable {
                        OnboardingPrefs.markFounderBannerShown()
                        dismissed = true
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "إغلاق",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
