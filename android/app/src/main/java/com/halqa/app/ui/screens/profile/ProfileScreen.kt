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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

@Composable
fun ProfileScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF1F1144), Color(0xFF0A0A1A)))),
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp).offset()) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)))
                        .border(3.dp, HalqaColors.Bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("ع", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.padding(top = 40.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("علي", color = HalqaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = HalqaColors.BrandLight, modifier = Modifier.size(20.dp))
                    }
                    Text("@ali_traveler", color = HalqaColors.TextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "حلقتك تبدأ هنا 🎤 — أحب البث، السفر، والتقنية.",
                color = HalqaColors.Text,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("12.4K", "متابع")
                Stat("180", "متابع")
                Stat("LV 14", "المستوى")
                Stat("🔥 27", "حلقة")
            }

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = "تعديل الملف",
                    onClick = { /* edit */ },
                    fillMaxWidth = false,
                    modifier = Modifier.weight(1f),
                )
                GoldButton(
                    text = "💎 المحفظة",
                    onClick = { navController.navigate(Routes.Wallet) },
                    fillMaxWidth = false,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("لوحة الإنجازات")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Achievement("⭐", "نجم صاعد")
                Achievement("⚔️", "محارب PK")
                Achievement("🎤", "صوت ذهبي")
                Achievement("🎁", "عاشق الهدايا")
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("الإعدادات")
            MenuRow(Icons.Filled.AccountBalanceWallet, "محفظة الكوينز والأرباح") { navController.navigate(Routes.Wallet) }
            MenuRow(Icons.Filled.History, "سجل البث والمعارك") {}
            MenuRow(Icons.Filled.Star, "تطوير الـ Avatar") { navController.navigate(Routes.PkArena) }
            MenuRow(Icons.Filled.Shield, "التحقق من الهوية (KYC)") {}
            MenuRow(Icons.Filled.SettingsSuggest, "الإعدادات العامة") {}

            Spacer(Modifier.height(20.dp))
            SectionTitle("القانوني")
            MenuRow(Icons.Filled.Lock, "شروط الاستخدام") { navController.navigate(Routes.Terms) }
            MenuRow(Icons.Filled.Lock, "سياسة الخصوصية") { navController.navigate(Routes.Privacy) }
            MenuRow(Icons.Filled.Lock, "إرشادات المجتمع") { navController.navigate(Routes.Community) }

            Spacer(Modifier.height(20.dp))
            MenuRow(Icons.Filled.Logout, "تسجيل الخروج", danger = true) { /* logout */ }

            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun Modifier.offset(): Modifier = this.padding(top = 0.dp).then(Modifier).also { /* keep simple */ }

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = HalqaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(label, color = HalqaColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Achievement(emoji: String, label: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(label, color = HalqaColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = HalqaColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Color(0xFFEF4444) else HalqaColors.TextMuted)
        Spacer(Modifier.size(12.dp))
        Text(
            label,
            color = if (danger) Color(0xFFEF4444) else HalqaColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = HalqaColors.TextDim, fontSize = 18.sp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(HalqaColors.Border),
    )
}
