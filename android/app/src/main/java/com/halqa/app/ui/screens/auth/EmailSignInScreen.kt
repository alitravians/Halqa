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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.AuthRepository
import com.halqa.app.domain.AuthFailure
import com.halqa.app.domain.AuthResult
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch

/**
 * Regular-user email + password sign-in / sign-up.
 *
 * Behavior matches the rest of the app: [AuthRepository.signInWithEmail]
 * delegates to Firebase Auth `signInOrCreateWithEmail`, so a brand-new
 * email creates the account and signs in atomically.
 *
 * If the resolved role has staff power (admin / staff / moderator / scout),
 * we route the user to the staff home; otherwise to the regular Main tab.
 */
@Composable
fun EmailSignInScreen(navController: NavController) {
    val account by AuthRepository.currentAccount.collectAsState()

    LaunchedEffect(account) {
        val role = account?.role
        if (role != null) {
            val target = if (role.hasStaffPower) Routes.StaffHome else Routes.Main
            navController.navigate(target) {
                popUpTo(Routes.Auth) { inclusive = true }
            }
        }
    }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (loading) return
        errorText = null
        if (email.isBlank() || password.length < 6) {
            errorText = "أدخل بريداً صالحاً وكلمة مرور لا تقل عن 6 أحرف."
            return
        }
        loading = true
        scope.launch {
            val result = AuthRepository.signInWithEmail(email, password)
            loading = false
            when (result) {
                is AuthResult.Success -> {
                    val target = if (result.account.role.hasStaffPower) {
                        Routes.StaffHome
                    } else {
                        Routes.Main
                    }
                    navController.navigate(target) {
                        popUpTo(Routes.Auth) { inclusive = true }
                    }
                }
                is AuthResult.Failure -> {
                    errorText = when (result.reason) {
                        AuthFailure.InvalidCredentials -> "البريد أو كلمة المرور غير صحيحة."
                        AuthFailure.AccountDisabled -> "هذا الحساب موقوف. تواصل مع الإدارة."
                        AuthFailure.Network -> "تعذّر الاتصال بالخادم. حاول لاحقاً."
                        AuthFailure.Unknown -> "حدث خطأ غير متوقع. حاول لاحقاً."
                    }
                }
            }
        }
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = HalqaColors.Text,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "الدخول بالبريد",
                    color = HalqaColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(HalqaColors.BgElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AlternateEmail,
                    contentDescription = null,
                    tint = HalqaColors.BrandLight,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "أهلاً بك في حلقة",
                color = HalqaColors.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "أدخل بريدك وكلمة المرور. إذا لم يكن لديك حساب نُنشئه لك تلقائياً.",
                color = HalqaColors.TextMuted,
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(28.dp))

            HalqaTextField(
                value = email,
                onValueChange = { email = it; errorText = null },
                placeholder = "البريد الإلكتروني",
                keyboardType = KeyboardType.Email,
                leadingIcon = {
                    Icon(Icons.Filled.AlternateEmail, contentDescription = null, tint = HalqaColors.TextMuted)
                },
                isError = errorText != null,
                enabled = !loading,
            )

            Spacer(Modifier.height(12.dp))

            HalqaTextField(
                value = password,
                onValueChange = { password = it; errorText = null },
                placeholder = "كلمة المرور",
                keyboardType = KeyboardType.Password,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                leadingIcon = {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = HalqaColors.TextMuted)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "إخفاء" else "إظهار",
                            tint = HalqaColors.TextMuted,
                        )
                    }
                },
                isError = errorText != null,
                enabled = !loading,
            )

            if (errorText != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    errorText!!,
                    color = HalqaColors.Danger,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = if (loading) "جاري التحقق…" else "متابعة",
                onClick = ::submit,
                enabled = !loading && email.isNotBlank() && password.length >= 6,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "بمتابعتك فإنك توافق على شروط الاستخدام وسياسة الخصوصية.",
                color = HalqaColors.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
