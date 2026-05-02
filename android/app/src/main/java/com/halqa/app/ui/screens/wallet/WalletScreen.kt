package com.halqa.app.ui.screens.wallet

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.halqa.app.data.CoinPackage
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

@Composable
fun WalletScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Back", tint = HalqaColors.Text)
            }
            Text("المحفظة", color = HalqaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { BalanceCard(onTopUp = { navController.navigate(Routes.TopUp) }) }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("باقات الكوينز", color = HalqaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("عرض الكل ›", color = HalqaColors.BrandLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            items(MockData.coinPackages) { pkg ->
                CoinPackageCard(pkg = pkg)
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                EarningsSection()
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun BalanceCard(onTopUp: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF7C3AED), Color(0xFFEC4899)),
                ),
            )
            .padding(20.dp),
    ) {
        Column {
            Text("رصيدك", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💰", fontSize = 28.sp)
                Spacer(Modifier.size(8.dp))
                Text("12,480", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.size(8.dp))
                Text("كوين", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Diamonds للسحب: 240 (≈90 ريال)",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton(text = "اشحن الآن", onClick = onTopUp, fillMaxWidth = false, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("سحب الأرباح", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CoinPackageCard(pkg: CoinPackage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (pkg.highlight) Brush.linearGradient(listOf(Color(0xFF1F1144), Color(0xFF2A1856))) else Brush.linearGradient(listOf(HalqaColors.BgElevated, HalqaColors.BgElevated)))
            .border(
                if (pkg.highlight) 1.5.dp else 1.dp,
                if (pkg.highlight) HalqaColors.Gold else HalqaColors.Border,
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(HalqaColors.Gold, HalqaColors.GoldLight))),
            contentAlignment = Alignment.Center,
        ) {
            Text("💰", fontSize = 26.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pkg.name, color = HalqaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (pkg.highlight) {
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HalqaColors.Gold)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("الأكثر شراءً", color = Color(0xFF111111), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${pkg.coins} كوين", color = HalqaColors.TextMuted, fontSize = 13.sp)
                if (pkg.bonusPercent > 0) {
                    Spacer(Modifier.size(6.dp))
                    Text("+${pkg.bonusPercent}% بونص", color = HalqaColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("${pkg.priceSar} ر.س", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EarningsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text("📊 ملخص الأرباح", color = HalqaColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        EarningsRow("الأسبوع الحالي", "84 ريال")
        EarningsRow("الشهر الحالي", "312 ريال")
        EarningsRow("القابل للسحب", "240 Diamond ≈ 90 ريال")
        Spacer(Modifier.height(8.dp))
        Text(
            "الحد الأدنى للسحب 375 ريال. أكمل KYC للتأهل عند 500 ريال.",
            color = HalqaColors.TextDim,
            fontSize = 11.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun EarningsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = HalqaColors.TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = HalqaColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
