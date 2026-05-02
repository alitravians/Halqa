package com.halqa.app.ui.screens.arena

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.navigation.NavController
import com.halqa.app.data.MockData
import com.halqa.app.data.PkMode
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

@Composable
fun ArenaTabScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        ArenaHeader()
        Spacer(Modifier.height(20.dp))

        FeaturedArenaCard(onJoin = { navController.navigate(Routes.avatarBattle("demo")) })

        Spacer(Modifier.height(24.dp))

        Text(
            "أوضاع المعركة",
            color = HalqaColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        MockData.pkModes.forEach { mode ->
            PkModeRow(
                mode = mode,
                onClick = {
                    if (mode.available && mode.id == "avatar") {
                        navController.navigate(Routes.avatarBattle("demo"))
                    } else if (mode.available) {
                        navController.navigate(Routes.PkArena)
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(20.dp))
        FactionsBanner()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ArenaHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚔️", fontSize = 30.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                "ساحة Halqa",
                color = HalqaColors.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "تنافس مع المذيعين، اربح المكافآت، وارفع رتبتك في الفصيل.",
            color = HalqaColors.TextMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun FeaturedArenaCard(onJoin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF7C3AED),
                        Color(0xFFEC4899),
                        Color(0xFFF59E0B).copy(alpha = 0.85f),
                    ),
                ),
            )
            .padding(20.dp),
    ) {
        Column {
            Text("🎯 الميزة الجديدة", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "معركة Avatar 3D",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "أفاتاران 3D يتقاتلان على شاشتيكم. الجمهور يصوت على الهجمات، لا يحسم Whales المعركة في 30 ثانية.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatPill("⏱ 5 دقائق")
                Spacer(Modifier.size(8.dp))
                StatPill("🏆 XP +200")
                Spacer(Modifier.size(8.dp))
                StatPill("🎁 هدايا حصرية")
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = "ادخل المعركة", onClick = onJoin, fillMaxWidth = false)
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PkModeRow(mode: PkMode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(16.dp))
            .clickable(enabled = mode.available) { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (mode.available)
                            listOf(HalqaColors.Brand, HalqaColors.Pink)
                        else listOf(Color.Gray, Color.DarkGray),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(mode.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mode.title, color = HalqaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (!mode.available) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("قريباً", color = HalqaColors.TextDim, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(mode.subtitle, color = HalqaColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FactionsBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1F1144), Color(0xFF1A0F36))))
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("🔥 حرب الفصائل (قريباً)", color = HalqaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "انضم لفصيل (نار، ماء، أرض، هواء) وساعد فصيلك يفوز بمكافآت موسمية.",
                color = HalqaColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(-12.dp)) {
            listOf("🔥", "💧", "🌍", "🌬️").forEach { e ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(HalqaColors.BgSurface)
                        .border(2.dp, HalqaColors.Bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e, fontSize = 18.sp)
                }
            }
        }
    }
}
