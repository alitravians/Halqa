package com.halqa.app.ui.screens.safety

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.SafetyPrefs
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.theme.HalqaColors

/**
 * One-time age + safety acknowledgement gate the user sees the very first time
 * they tap "Go Live". They cannot proceed unless **all three** affirmations
 * are checked — there is no "skip" path. After acceptance the result is
 * stored in [SafetyPrefs] and the user is forwarded to [GoLivePrepScreen].
 *
 * Per ali's policy decision: real KYC age verification (Phase E) will *also*
 * be enforced server-side; this gate is the explicit, in-product
 * acknowledgement that complements it.
 */
@Composable
fun AgeGateScreen(navController: NavController) {
    var c1 by remember { mutableStateOf(false) }
    var c2 by remember { mutableStateOf(false) }
    var c3 by remember { mutableStateOf(false) }
    val canProceed = c1 && c2 && c3

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
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
                Icon(Icons.Filled.Warning, contentDescription = null, tint = HalqaColors.Danger, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("سياسة البث الآمن", color = HalqaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("اقرأها قبل أول بث لك", color = HalqaColors.TextMuted, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        WarningCard(
            icon = Icons.Filled.ChildCare,
            title = "ممنوع منعاً باتاً البث لمن هم دون 18",
            body = "البث المباشر متاح فقط لمن أتم الثامنة عشرة. أي بثٍّ يُكشف فيه أن صاحبه قاصر سيُعلَّق الحساب فوراً ويُحال للجهات المختصة عند الضرورة.",
        )
        Spacer(Modifier.height(10.dp))
        WarningCard(
            icon = Icons.Filled.Shield,
            title = "ممنوع ظهور الأطفال في البث",
            body = "بمجرد ظهور قاصر (دون 18) في إطار الكاميرا — ولو كان من أهل المذيع — يقوم نظام المراقبة الذاتي بإيقاف البث وفتح مراجعة لمدة 10 دقائق، ويُتخذ القرار المناسب بعدها.",
        )
        Spacer(Modifier.height(10.dp))
        WarningCard(
            icon = Icons.Filled.Gavel,
            title = "العقوبات تتدرّج حسب نوع المخالفة",
            body = "تحذير → حظر 24 ساعة → 7 أيام → 30 يوم → حظر دائم وإحالة قانونية. حالات ظهور القاصرين تُعامَل بأعلى مستوى مباشرةً.",
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "أتعهّد بأن:",
            color = HalqaColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        ConfirmRow(
            checked = c1,
            onToggle = { c1 = !c1 },
            label = "عمري 18 سنة فأكثر، وأملك صلاحية فتح بث مباشر.",
        )
        ConfirmRow(
            checked = c2,
            onToggle = { c2 = !c2 },
            label = "لن أسمح بظهور أي قاصر (دون 18) في بثي تحت أي ظرف.",
        )
        ConfirmRow(
            checked = c3,
            onToggle = { c3 = !c3 },
            label = "أوافق على أن نظام المراقبة الذاتي قد يفتح مراجعة لمدة 10 دقائق ثم يُصدر العقوبة المناسبة عند رصد مخالفة.",
        )

        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = if (canProceed) "أتعهّد و أكمل" else "أكمل تأكيد التعهّدات",
            enabled = canProceed,
            onClick = {
                SafetyPrefs.acceptAgeGate()
                navController.popBackStack()
            },
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = "إلغاء و الرجوع",
            onClick = { navController.popBackStack() },
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun WarningCard(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Danger.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(HalqaColors.Danger.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = HalqaColors.Danger, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HalqaColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = HalqaColors.TextMuted, fontSize = 12.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun ConfirmRow(checked: Boolean, onToggle: () -> Unit, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.06f))))
                .border(1.dp, if (checked) Color.Transparent else HalqaColors.Border, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            label,
            color = HalqaColors.Text,
            fontSize = 13.sp,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
}
