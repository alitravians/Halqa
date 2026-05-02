package com.halqa.app.ui.screens.arena

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.ui.theme.HalqaColors

@Composable
fun PkArenaScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Back", tint = HalqaColors.Text)
            }
            Text("ساحة PK", color = HalqaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Text("🎮", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("ألعاب الجمهور", color = HalqaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "خلال معارك PK، تظهر للجمهور تحديات سريعة (10 ثوان):\n" +
                    "• تحدي النقر السريع\n" +
                    "• لعبة الذاكرة\n" +
                    "• أسئلة مفاجئة\n" +
                    "• التعرف على الإيماءات\n\n" +
                    "حتى بدون كوينز، كل مشاهد يقدر يساهم بنقاط مجانية لمذيعه المفضل!",
                color = HalqaColors.TextMuted,
                fontSize = 14.sp,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))
            Text("🎡 عجلة العقوبات", color = HalqaColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "الخاسر في PK يدور عجلة عقوبات يقترحها ويصوت عليها الجمهور:\n" +
                    "• الجمهور يقدم اقتراحات (فلتر AI ضد السبام)\n" +
                    "• التصويت المباشر على أفضل العقوبات\n" +
                    "• أفضل الاقتراحات تضاف للعجلة الأسبوعية\n" +
                    "• عقوبات مبتكرة ومتجددة دائماً!",
                color = HalqaColors.TextMuted,
                fontSize = 14.sp,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
