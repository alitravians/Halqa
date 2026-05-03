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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
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
 * Email + password sign-in for staff (Scout / Moderator / Staff / Admin).
 *
 * Lives on its own route so the regular user-facing [AuthScreen] stays
 * unchanged. The owner explicitly required staff sign-in to be email +
 * password (not a four-digit code), so that's the only auth method here.
 */
@Composable
fun StaffSignInScreen(navController: NavController) {
    val account by AuthRepository.currentAccount.collectAsState()

    // If the user is already signed in as staff, jump straight to the staff
    // home — re-prompting for credentials on every visit is exactly the
    // friction users complained about in earlier sessions.
    LaunchedEffect(account) {
        if (account?.role?.hasStaffPower == true) {
            navController.navigate(Routes.StaffHome) {
                popUpTo(Routes.StaffAuth) { inclusive = true }
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
        loading = true
        scope.launch {
            // Strict sign-in: do NOT auto-create the Firebase Auth user
            // here even if the email is unknown. The staff sign-in
            // surface is reachable from the public auth screen, and
            // auto-create on a typo'd email would (a) silently mint a
            // throwaway Firebase Auth account on every attempt — Auth
            // user counts have quota + billing implications — and (b)
            // give the typist a working `user`-role session they did
            // not actually intend to create. Staff accounts must be
            // provisioned by an admin via the Firebase Console + a
            // `/users/{uid}.role` write; if the email is unknown the
            // sign-in must fail closed.
            val result = AuthRepository.signInWithEmailStrict(email, password)
            loading = false
            when (result) {
                is AuthResult.Success -> {
                    if (result.account.role.hasStaffPower) {
                        navController.navigate(Routes.StaffHome) {
                            popUpTo(Routes.StaffAuth) { inclusive = true }
                        }
                    } else {
                        // The credentials were valid but not staff-level —
                        // bounce them out and clear the persisted session.
                        AuthRepository.signOut()
                        errorText = "هذا الحساب لا يملك صلاحيات الموظفين."
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = HalqaColors.Text,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "تسجيل دخول الموظفين",
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
                    Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = HalqaColors.BrandLight,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "أهلاً بك",
                color = HalqaColors.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "هذه الشاشة مخصّصة لفريق حلقة (موظفين، مراقبين، صيّادين، مالكين). " +
                    "استخدم بريدك العملي وكلمة المرور التي زوّدتك بها الإدارة.",
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
                    Icon(Icons.Filled.Email, contentDescription = null, tint = HalqaColors.TextMuted)
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

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = if (loading) "" else "دخول",
                    onClick = ::submit,
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "لا تشارك بيانات دخولك مع أحد. كل إجراء تقوم به يُسجَّل في سجل الإجراءات لمراجعة الإدارة.",
                color = HalqaColors.TextDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
