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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
                text = "المتابعة بالبريد",
                onClick = { navController.navigate(Routes.EmailAuth) },
            )

            Spacer(Modifier.height(12.dp))

            // Phone OTP re-enabled. The phantom-guest bug that caused this
            // entry point to be removed in M0 is fixed in
            // [com.halqa.app.data.UserDocBootstrap]: every Phone-OTP
            // sign-in now writes /users/{uid} synchronously before the
            // screen navigates to Main. KYC is still enforced server-side
            // by broadcast / wallet endpoints; this entry only routes the
            // user to a regular role:'user' and KYC is requested when
            // they try to broadcast or top up.
            PrimaryButton(
                text = "المتابعة بالهاتف",
                onClick = { navController.navigate(Routes.PhoneAuth) },
            )

            // "المتابعة بـ Google" is shipped in a follow-up PR that wires
            // the GoogleSignInClient + intent flow. It depends on the
            // Google provider being enabled in Firebase Console and on
            // google-services.json containing an OAuth Web client
            // (oauth_client[].client_type == 3); see that PR for the
            // current blocker status.
            //
            // "متابعة كزائر" stays removed: it violates Layla's blocker B1
            // (anonymous viewers cannot bypass age-gate / community
            // guidelines). Revisit when guest read-only mode is designed.

            Spacer(Modifier.height(24.dp))

            Spacer(Modifier.weight(1f))

            Text(
                "بإنشائك حساباً، فأنت توافق على شروط الاستخدام وسياسة الخصوصية.",
                color = HalqaColors.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // Staff entry point. Lives at the bottom of the regular auth screen
            // so it's discoverable to fa9riq members but visually de-emphasised
            // for everyone else — staff still need a clear, signed-in path
            // (not a hidden trick) so we keep the link visible rather than
            // gating it behind a long-press easter egg.
            TextLinkButton(
                text = "تسجيل دخول الموظفين",
                onClick = { navController.navigate(Routes.StaffAuth) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
