package com.halqa.app.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.MockData
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.theme.HalqaColors

@Composable
fun TopUpScreen(navController: NavController) {
    var selectedId by remember { mutableStateOf("p4") }
    var method by remember { mutableStateOf("googleplay") }

    val pkg = MockData.coinPackages.find { it.id == selectedId } ?: MockData.coinPackages.first()

    Column(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Back", tint = HalqaColors.Text)
            }
            Text("شحن الكوينز", color = HalqaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text("اختر الباقة", color = HalqaColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            MockData.coinPackages.forEach { p ->
                val sel = p.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) HalqaColors.Brand.copy(alpha = 0.12f) else HalqaColors.BgElevated)
                        .border(
                            if (sel) 1.5.dp else 1.dp,
                            if (sel) HalqaColors.BrandLight else HalqaColors.Border,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { selectedId = p.id }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💰", fontSize = 22.sp)
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${p.coins} كوين", color = HalqaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (p.bonusPercent > 0) {
                            Text("+${p.bonusPercent}% بونص", color = HalqaColors.Gold, fontSize = 11.sp)
                        }
                    }
                    Text("${p.priceSar} ر.س", color = HalqaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("طريقة الدفع", color = HalqaColors.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            PaymentMethod(
                id = "googleplay",
                title = "Google Play",
                subtitle = "موصى به للأمان",
                emoji = "🟢",
                selected = method == "googleplay",
                onClick = { method = it },
            )
            Spacer(Modifier.height(8.dp))
            PaymentMethod(
                id = "stcpay",
                title = "STC Pay",
                subtitle = "خصم مباشر",
                emoji = "💜",
                selected = method == "stcpay",
                onClick = { method = it },
            )
            Spacer(Modifier.height(8.dp))
            PaymentMethod(
                id = "mada",
                title = "بطاقة مدى / Visa",
                subtitle = "عبر بوابة آمنة",
                emoji = "💳",
                selected = method == "mada",
                onClick = { method = it },
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "بإتمام عملية الشراء فإنك توافق على شروط الاستخدام. الكوينز غير قابلة للاسترجاع.",
                color = HalqaColors.TextDim,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "ادفع ${pkg.priceSar} ر.س", onClick = { navController.popBackStack() })
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PaymentMethod(
    id: String,
    title: String,
    subtitle: String,
    emoji: String,
    selected: Boolean,
    onClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) HalqaColors.Brand.copy(alpha = 0.12f) else HalqaColors.BgElevated)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) HalqaColors.BrandLight else HalqaColors.Border,
                RoundedCornerShape(14.dp),
            )
            .clickable { onClick(id) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HalqaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = HalqaColors.TextMuted, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) HalqaColors.Brand else Color.Transparent)
                .border(2.dp, if (selected) HalqaColors.Brand else HalqaColors.Border, RoundedCornerShape(10.dp)),
        )
    }
}
