package com.halqa.app.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.android.gms.common.api.ApiException
import com.halqa.app.data.GoogleAuthRepository
import com.halqa.app.data.SignupCapReachedException
import com.halqa.app.ui.components.HalqaLogo
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.components.TextLinkButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }

    // Activity-result launcher for the Google chooser. The intent comes
    // from [GoogleAuthRepository.client]; the result is unwrapped to a
    // [GoogleSignInAccount] (or [ApiException] on cancel / failure).
    // On success we hand the idToken to Firebase + bootstrap /users/{uid}
    // synchronously inside [GoogleAuthRepository.signInWithIdTokenAndBootstrap]
    // — the screen does NOT navigate to Main until that suspend resolves,
    // which is the same phantom-guest fix used by the Phone OTP path.
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            googleError = null
            try {
                val account = GoogleAuthRepository.accountFromIntent(result.data)
                GoogleAuthRepository.signInWithIdTokenAndBootstrap(account)
                navController.navigate(Routes.Main) {
                    popUpTo(Routes.Auth) { inclusive = true }
                }
            } catch (e: ApiException) {
                googleError = mapGoogleApiError(e)
            } catch (_: SignupCapReachedException) {
                // Layla's GR5: closed-beta daily signup cap was hit
                // server-side. The /users/{uid} doc was already created
                // by ensureUserDoc (it had to be — Created is the
                // trigger), but we MUST NOT navigate the user to Main
                // because that would silently soft-launch beyond the
                // 20/day cap. The orphaned doc is harmless: when the
                // user retries after staff unlocks, the existing-doc
                // branch of UserDocBootstrap takes over and patches
                // anything missing.
                googleError = "تم بلوغ السقف اليومي للتسجيل في النسخة التجريبية. حاول غداً."
            } catch (t: Throwable) {
                googleError = "تعذّر تسجيل الدخول عبر Google. حاول لاحقاً."
            } finally {
                googleLoading = false
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

            // Google is the discoverable default for new users — Saudi
            // Android users overwhelmingly already have a Google account
            // signed into the device, so this is the lowest-friction path.
            // Email + Phone are the secondary routes underneath.
            //
            // The bootstrap call inside
            // [GoogleAuthRepository.signInWithIdTokenAndBootstrap] writes
            // /users/{uid} (role='user', uid, email, displayName, avatar,
            // createdAt) synchronously before the launcher callback
            // navigates to Main, closing the phantom-guest path.
            PrimaryButton(
                text = if (googleLoading) "جارِ التحقق..." else "المتابعة بـ Google",
                enabled = !googleLoading,
                onClick = {
                    googleError = null
                    googleLoading = true
                    val client = GoogleAuthRepository.client(ctx)
                    // Sign out first so the chooser appears every time
                    // instead of silently re-using a previous selection —
                    // critical for multi-account devices.
                    client.signOut().addOnCompleteListener {
                        googleLauncher.launch(client.signInIntent)
                    }
                },
            )

            if (googleError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    googleError!!,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(12.dp))

            PrimaryButton(
                text = "المتابعة بالبريد",
                onClick = { navController.navigate(Routes.EmailAuth) },
            )

            Spacer(Modifier.height(12.dp))

            // Phone OTP re-enabled in PR #78. The phantom-guest bug that
            // caused this entry point to be removed in M0 is fixed in
            // [com.halqa.app.data.UserDocBootstrap]: every Phone-OTP
            // sign-in writes /users/{uid} synchronously before the
            // screen navigates to Main. KYC is still enforced
            // server-side by broadcast / wallet endpoints; this entry
            // only routes the user to a regular role:'user' and KYC is
            // requested when they try to broadcast or top up.
            PrimaryButton(
                text = "المتابعة بالهاتف",
                onClick = { navController.navigate(Routes.PhoneAuth) },
            )

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

/**
 * Map a Google [ApiException] to an Arabic user-facing string. Codes are
 * from [com.google.android.gms.common.api.CommonStatusCodes] +
 * [com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes]. The
 * cancelled / disabled / no-account cases are common enough that they
 * deserve their own messages; everything else falls through to a generic
 * "try again later".
 */
private fun mapGoogleApiError(e: ApiException): String = when (e.statusCode) {
    com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED ->
        "لم يتم اختيار حساب Google. حاول مجدداً."
    com.google.android.gms.common.api.CommonStatusCodes.CANCELED,
    com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED ->
        "تم إلغاء تسجيل الدخول."
    com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR ->
        "تعذّر الاتصال بالخادم. تحقق من الإنترنت وحاول مجدداً."
    com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_FAILED ->
        "فشل تسجيل الدخول عبر Google. حاول مجدداً."
    else -> "تعذّر تسجيل الدخول عبر Google. حاول لاحقاً."
}
