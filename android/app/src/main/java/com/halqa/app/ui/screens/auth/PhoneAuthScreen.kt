package com.halqa.app.ui.screens.auth

import android.app.Activity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.halqa.app.data.Countries
import com.halqa.app.data.PhoneAuthRepository
import com.halqa.app.data.PhoneAuthRepository.PhoneAuthFailure
import com.halqa.app.data.PhoneAuthRepository.VerificationResult
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.components.TextLinkButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch

/**
 * Phone-OTP sign-in / sign-up screen.
 *
 * Two-step UX:
 *  1. Phone-entry: country picker (defaults to +966) + local-number field.
 *     "إرسال رمز التحقق" calls
 *     [PhoneAuthRepository.requestVerification], which kicks off
 *     [com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber].
 *  2. OTP-entry: 6-digit code field. "تأكيد" calls
 *     [PhoneAuthRepository.signInWithCredentialAndBootstrap], which signs
 *     in **and** writes the `/users/{uid}` doc (role=user, uid, phoneNumber,
 *     createdAt) before resolving. Only after that resolves do we
 *     `navController.navigate(Routes.Main)`.
 *
 * Phantom-guest bug fix
 * ---------------------
 * The previous (deleted) version of this screen jumped straight from the
 * phone-entry button to `navController.navigate(Routes.Main)` without ever
 * calling Firebase Auth. The current version waits for both
 * `signInWithCredential` AND the `/users/{uid}` doc write to complete before
 * navigating, which closes the phantom-guest path documented in
 * [com.halqa.app.data.UserDocBootstrap].
 *
 * KYC gate
 * --------
 * KYC is enforced server-side by the broadcast / wallet endpoints. Allowing
 * Phone OTP sign-in does NOT bypass KYC for any privileged action; the user
 * lands on Main as a regular `role:'user'` and KYC is requested when they try
 * to broadcast or top up. Re-enablement of this entry point is gated on
 * Layla's T&S sign-off under `BYPASS_KYC_FOR_BETA=true` (see PR description).
 */
@Composable
fun PhoneAuthScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf<Step>(Step.Phone) }
    var phone by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(Countries.saudiArabia) }
    var pickerOpen by remember { mutableStateOf(false) }

    var code by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var e164 by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) {
                IconButton(onClick = {
                    if (step is Step.Code) {
                        step = Step.Phone
                        code = ""
                        errorText = null
                    } else {
                        navController.popBackStack()
                    }
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Back",
                        tint = HalqaColors.Text,
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            when (step) {
                Step.Phone -> {
                    Text(
                        "رقم الجوال",
                        color = HalqaColors.Text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "اختر الدولة ثم أدخل رقمك. سنرسل لك رمز تحقق مرة واحدة (OTP).",
                        color = HalqaColors.TextMuted,
                        fontSize = 14.sp,
                    )

                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(1.dp, HalqaColors.BorderStrong, RoundedCornerShape(16.dp))
                                .clickable(enabled = !loading) { pickerOpen = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(country.flag, fontSize = 22.sp)
                            Text(
                                "+${country.dial}",
                                color = HalqaColors.Text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "تغيير الدولة",
                                tint = HalqaColors.TextMuted,
                            )
                        }

                        HalqaTextField(
                            value = phone,
                            onValueChange = { input ->
                                val digits = input.filter { c -> c.isDigit() }
                                phone = digits.take(country.maxDigits)
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = "رقم الجوال",
                            keyboardType = KeyboardType.Phone,
                            enabled = !loading,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "${country.nameAr} • +${country.dial}",
                        color = HalqaColors.TextDim,
                        fontSize = 12.sp,
                    )

                    if (errorText != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(errorText!!, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    PrimaryButton(
                        text = if (loading) "جارِ الإرسال..." else "إرسال رمز التحقق",
                        enabled = !loading && phone.length >= minOf(7, country.maxDigits),
                        onClick = {
                            errorText = null
                            val activity = ctx as? Activity
                            if (activity == null) {
                                errorText = "تعذّر بدء التحقق. أعد فتح الشاشة وحاول مجدداً."
                                return@PrimaryButton
                            }
                            val number = "+${country.dial}${phone}"
                            e164 = number
                            loading = true
                            scope.launch {
                                val result = PhoneAuthRepository.requestVerification(
                                    activity = activity,
                                    e164PhoneNumber = number,
                                )
                                loading = false
                                when (result) {
                                    is VerificationResult.InstantVerification -> {
                                        // Auto-resolved by Play Services SMS
                                        // retriever — sign in directly, no
                                        // OTP entry needed.
                                        loading = true
                                        runCatching {
                                            PhoneAuthRepository.signInWithCredentialAndBootstrap(
                                                credential = result.credential,
                                                e164PhoneNumber = number,
                                            )
                                        }.onSuccess {
                                            navController.navigate(Routes.Main) {
                                                popUpTo(Routes.Auth) { inclusive = true }
                                            }
                                        }.onFailure { t ->
                                            loading = false
                                            errorText = mapPhoneError(t)
                                        }
                                    }
                                    is VerificationResult.CodeSent -> {
                                        verificationId = result.verificationId
                                        step = Step.Code
                                    }
                                    is VerificationResult.Failed -> {
                                        errorText = mapVerificationFailure(result.reason)
                                    }
                                }
                            }
                        },
                    )
                }

                Step.Code -> {
                    Text(
                        "رمز التحقق",
                        color = HalqaColors.Text,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "أرسلنا رمزاً مكوّناً من 6 أرقام إلى $e164.",
                        color = HalqaColors.TextMuted,
                        fontSize = 14.sp,
                    )

                    Spacer(Modifier.height(32.dp))

                    HalqaTextField(
                        value = code,
                        onValueChange = { input ->
                            code = input.filter { it.isDigit() }.take(6)
                        },
                        placeholder = "______",
                        keyboardType = KeyboardType.NumberPassword,
                        enabled = !loading,
                    )

                    if (errorText != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(errorText!!, color = Color(0xFFFF6B6B), fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(24.dp))

                    PrimaryButton(
                        text = if (loading) "جارِ التحقق..." else "تأكيد",
                        enabled = !loading && code.length == 6 && verificationId != null,
                        onClick = {
                            errorText = null
                            val vid = verificationId ?: return@PrimaryButton
                            loading = true
                            scope.launch {
                                runCatching {
                                    val credential = PhoneAuthRepository.buildCredential(vid, code)
                                    PhoneAuthRepository.signInWithCredentialAndBootstrap(
                                        credential = credential,
                                        e164PhoneNumber = e164,
                                    )
                                }.onSuccess {
                                    navController.navigate(Routes.Main) {
                                        popUpTo(Routes.Auth) { inclusive = true }
                                    }
                                }.onFailure { t ->
                                    loading = false
                                    errorText = mapPhoneError(t)
                                }
                            }
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    TextLinkButton(
                        text = "تغيير رقم الجوال",
                        onClick = {
                            step = Step.Phone
                            code = ""
                            errorText = null
                        },
                    )
                }
            }
        }
    }

    if (pickerOpen) {
        CountryPickerDialog(
            onDismiss = { pickerOpen = false },
            onPick = {
                country = it
                phone = ""
                pickerOpen = false
            },
        )
    }
}

private sealed class Step {
    data object Phone : Step()
    data object Code : Step()
}

private fun mapVerificationFailure(reason: PhoneAuthFailure): String = when (reason) {
    PhoneAuthFailure.InvalidPhoneNumber -> "رقم الجوال غير صالح. تأكد من الرمز الدولي."
    PhoneAuthFailure.QuotaExceeded -> "تم تجاوز الحد المسموح. حاول لاحقاً."
    PhoneAuthFailure.Network -> "تعذّر الاتصال بالخادم. تحقق من الإنترنت وحاول مجدداً."
    PhoneAuthFailure.InvalidCode -> "الرمز غير صحيح."
    PhoneAuthFailure.Unknown -> "تعذّر إرسال الرمز. حاول لاحقاً."
}

private fun mapPhoneError(t: Throwable): String = when (t) {
    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
        "الرمز غير صحيح أو منتهي الصلاحية."
    is com.google.firebase.FirebaseNetworkException ->
        "تعذّر الاتصال بالخادم. تحقق من الإنترنت."
    is com.google.firebase.FirebaseTooManyRequestsException ->
        "تم تجاوز الحد المسموح. حاول لاحقاً."
    else -> "تعذّر تسجيل الدخول. حاول لاحقاً."
}
