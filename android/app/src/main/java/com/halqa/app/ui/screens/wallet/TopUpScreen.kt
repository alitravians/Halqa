package com.halqa.app.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.MockData
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.humanize
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch

@Composable
fun TopUpScreen(navController: NavController) {
    var selectedId by remember { mutableStateOf("p4") }
    var working by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

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

            // Closed-beta payment story:
            //   - The pack catalogue above (`MockData.coinPackages`) is a
            //     preview of v0.2's Google Play Billing catalogue — the
            //     selected pack does NOT determine what the backend
            //     credits today. The only flow `POST /api/wallet/topup`
            //     supports right now is the single `BETA_TOPUP_PACK`
            //     (1000 coins, free, one redemption per 24h).
            //   - Per Google Play "Payments" policy (and Layla T&S
            //     Blocker B1): when v0.2 ships paid IAPs, they MUST go
            //     through Google Play Billing exclusively. STC Pay,
            //     Mada, and Visa rails are intentionally NOT offered here
            //     — exposing them would risk immediate app removal from
            //     the Play Store.
            //   - Until then this screen is honest about being a beta
            //     redemption flow, not a payment flow. The previous
            //     button labelled "ادفع X ر.س عبر Google Play" was a
            //     UX-lying button — it ran `navController.popBackStack()`
            //     with zero API calls and zero feedback to the user.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HalqaColors.Brand.copy(alpha = 0.12f))
                    .border(1.5.dp, HalqaColors.BrandLight, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🎁", fontSize = 22.sp)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "باقة البيتا المجانية",
                        color = HalqaColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "الباقات المدفوعة عبر Google Play ستصل في v0.2.",
                        color = HalqaColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "خلال فترة البيتا يمكنك الحصول على باقة بداية مجانية مرة كل 24 ساعة. الباقات الأخرى أعلاه للعرض فقط وستصبح قابلة للشراء في v0.2.",
                color = HalqaColors.TextDim,
                fontSize = 11.sp,
                lineHeight = 18.sp,
            )
            feedback?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = HalqaColors.Pink, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = if (working) "جارٍ ..." else "احصل على باقة البيتا (مجاناً)",
                onClick = {
                    if (working) return@PrimaryButton
                    working = true
                    feedback = null
                    scope.launch {
                        try {
                            val res = ApiClient.api.topupWallet()
                            working = false
                            // The Firestore listener on `wallets/{uid}`
                            // (WalletRepository.observe) will surface the
                            // new balance to WalletScreen automatically;
                            // no need to thread the `res.balance` value
                            // back through navigation state.
                            val granted = res.pack?.coins ?: 0
                            feedback = if (res.ok && granted > 0) {
                                "تم إيداع $granted كوين في محفظتك."
                            } else {
                                "تعذّر إتمام العملية."
                            }
                        } catch (t: Throwable) {
                            working = false
                            feedback = "تعذّر إتمام العملية: ${t.humanize()}"
                        }
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
