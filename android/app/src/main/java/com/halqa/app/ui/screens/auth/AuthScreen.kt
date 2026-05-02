package com.halqa.app.ui.screens.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
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

@Composable
fun AuthScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF180D2C), HalqaColors.Bg),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                HalqaLogo(size = 64, textSize = 28)
            }

            Spacer(Modifier.height(40.dp))

            Text(
                "أهلاً بك في حلقة",
                color = HalqaColors.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "أنشئ حساباً جديداً أو سجّل دخولك للمتابعة.",
                color = HalqaColors.TextMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(40.dp))

            PrimaryButton(
                text = "المتابعة برقم الجوال",
                onClick = { navController.navigate(Routes.PhoneAuth) },
            )
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = "المتابعة بـ Google",
                onClick = { navController.navigate(Routes.Main) { popUpTo(Routes.Auth) { inclusive = true } } },
            )
            Spacer(Modifier.height(12.dp))
            GhostButton(
                text = "المتابعة بالبريد",
                onClick = { navController.navigate(Routes.Main) { popUpTo(Routes.Auth) { inclusive = true } } },
            )

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(HalqaColors.Border),
                )
                Text(
                    "أو",
                    color = HalqaColors.TextDim,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontSize = 13.sp,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(HalqaColors.Border),
                )
            }

            Spacer(Modifier.height(24.dp))

            TextLinkButton(
                text = "متابعة كزائر",
                onClick = { navController.navigate(Routes.Main) { popUpTo(Routes.Auth) { inclusive = true } } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            Text(
                "بإنشائك حساباً، فأنت توافق على شروط الاستخدام وسياسة الخصوصية.",
                color = HalqaColors.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
