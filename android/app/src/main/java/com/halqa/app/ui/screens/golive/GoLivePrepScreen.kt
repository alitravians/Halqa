package com.halqa.app.ui.screens.golive

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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsSuggest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.theme.HalqaColors

private val categories = listOf("ترفيه", "موسيقى", "ألعاب", "دردشة", "تعليم", "طبخ", "رياضة", "ثقافة")

@Composable
fun GoLivePrepScreen(navController: NavController) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.first()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            "ابدأ حلقتك",
            color = HalqaColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "اضبط بثك المباشر قبل الانطلاق.",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.radialGradient(listOf(Color(0xFF1F1144), Color(0xFF0A0A1A))))
                .border(1.dp, HalqaColors.Border, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Camera, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "معاينة الكاميرا",
                    color = HalqaColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("سيتم تفعيل الكاميرا عند البدء", color = HalqaColors.TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        HalqaTextField(
            value = title,
            onValueChange = { title = it },
            label = "عنوان البث",
            placeholder = "مثال: حلقة الترفيه المسائية",
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "التصنيف",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(HalqaColors.BgElevated)
                    .border(1.dp, HalqaColors.Border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Column {
                    categories.chunked(4).forEach { row ->
                        Row {
                            row.forEach { c ->
                                val sel = c == category
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))))
                                        .clickable { category = c }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(c, color = if (sel) Color.White else HalqaColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HalqaColors.BgElevated)
                .border(1.dp, HalqaColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            ToggleRow("بث الصوت فقط", icon = Icons.Filled.Mic)
            Spacer(Modifier.height(8.dp))
            ToggleRow("السماح بدخول PK", icon = Icons.Filled.SettingsSuggest)
            Spacer(Modifier.height(8.dp))
            ToggleRow("الفلاتر التلقائية للشات", icon = Icons.Filled.SettingsSuggest)
        }

        Spacer(Modifier.height(20.dp))

        PrimaryButton(text = "ابدأ البث الآن", onClick = { /* navigate to broadcaster */ })
        Spacer(Modifier.height(12.dp))
        GoldButton(text = "جدولة بث", onClick = { /* schedule */ })

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ToggleRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    var checked by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked },
    ) {
        Icon(icon, contentDescription = null, tint = HalqaColors.TextMuted)
        Spacer(Modifier.size(12.dp))
        Text(label, color = HalqaColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) HalqaColors.Brand else Color.White.copy(alpha = 0.12f)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
