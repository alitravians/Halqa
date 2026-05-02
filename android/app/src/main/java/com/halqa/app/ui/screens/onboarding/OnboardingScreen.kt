package com.halqa.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.HalqaLogo
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.components.TextLinkButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String,
    val gradient: List<Color>,
)

@Composable
fun OnboardingScreen(navController: NavController) {
    val pages = listOf(
        OnboardingPage(
            "🎤",
            "حلقتك تبدأ هنا",
            "انضم لمجتمع البث المباشر العربي الأول. ابثّ، شاهد، وكوّن حلقتك الخاصة.",
            listOf(Color(0xFF7C3AED), Color(0xFFEC4899)),
        ),
        OnboardingPage(
            "⚔️",
            "PK Arena مبتكرة",
            "تنافس في معارك Avatar 3D، ألعاب صغيرة للجمهور، وعجلة عقوبات يصنعها المجتمع.",
            listOf(Color(0xFFEC4899), Color(0xFFF59E0B)),
        ),
        OnboardingPage(
            "💎",
            "اربح من جمهورك",
            "استلم الهدايا، تحوّل لـ Diamonds، واسحب أرباحك بكل أمان.",
            listOf(Color(0xFFF59E0B), Color(0xFF10B981)),
        ),
    )
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 32.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HalqaLogo(size = 32, textSize = 18)
                TextLinkButton(
                    text = "تخطي",
                    onClick = { navController.navigate(Routes.Auth) { popUpTo(Routes.Onboarding) { inclusive = true } } },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) { idx ->
                OnboardingPageContent(pages[idx])
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.forEachIndexed { i, _ ->
                    val width = if (i == pagerState.currentPage) 28.dp else 8.dp
                    val color = if (i == pagerState.currentPage) HalqaColors.BrandLight else HalqaColors.Border
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PrimaryButton(
                    text = if (pagerState.currentPage == pages.size - 1) "ابدأ الآن" else "متابعة",
                    onClick = {
                        if (pagerState.currentPage == pages.size - 1) {
                            navController.navigate(Routes.Auth) { popUpTo(Routes.Onboarding) { inclusive = true } }
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                )
                GhostButton(
                    text = "لدي حساب — تسجيل دخول",
                    onClick = { navController.navigate(Routes.Auth) },
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(page.gradient.map { it.copy(alpha = 0.4f) })),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(page.gradient)),
                contentAlignment = Alignment.Center,
            ) {
                Text(page.emoji, fontSize = 76.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
        Text(
            page.title,
            color = HalqaColors.Text,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.body,
            color = HalqaColors.TextMuted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp,
        )
    }
}
