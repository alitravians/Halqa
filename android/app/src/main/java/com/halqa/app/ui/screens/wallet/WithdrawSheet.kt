package com.halqa.app.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.WithdrawRequest
import com.halqa.app.data.remote.humanize
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Sara's C1 + C2 (Critical UX) — surface the Withdraw cashout path for
 * hosts so the "سحب الأرباح" button on the wallet card actually does
 * something. Pre-PR-B, that button was a dead text label inside a
 * passive Box (see [WalletScreen.BalanceCard] line ~142-151).
 *
 * Form
 * ----
 *   - `amountDiamonds`: integer, must be ≤ host's current diamonds
 *     balance. Below the 1000-diamond minimum (≈ 375 SAR per Yasser's
 *     M2 economy spec) the submit button stays disabled. The cap
 *     check uses the wallet snapshot passed in by [WalletScreen] so
 *     it survives a Firestore listener hiccup without making the
 *     sheet feel laggy.
 *   - `iban`: 22-or-24-char IBAN. Saudi IBANs are 24 chars (`SA` +
 *     22 digits), but we accept 22-26 to absorb whitespace / region
 *     variants. The submit button stays disabled until the trimmed
 *     value is at least 22 chars.
 *
 * Failure modes mapped to UI (the sheet NEVER surfaces raw error
 * codes — Layla's spec).
 * --------------------------------------------------------------
 *   - HTTP 403 with `KYC_BYPASS_REVERIFY_REQUIRED` in the body:
 *     yellow [HalqaColors.Warning] banner reading
 *     "تحتاج إعادة التحقق من الهوية لإتمام السحب" + an "ابدأ
 *     التحقق" CTA that navigates to [com.halqa.app.ui.navigation.Routes.Kyc]
 *     and dismisses the sheet. This is the GR4 grandfather cohort.
 *   - HTTP 503 with `WITHDRAW_NOT_AVAILABLE` in the body:
 *     informational banner (TextDim) reading "السحب غير متاح حالياً
 *     في الإصدار التجريبي. سنُشعرك فور تفعيله." No CTA.
 *   - Anything else: the standard [Throwable.humanize] catalogue
 *     fires (network, 401 token expiry, 5xx, etc.) and shows
 *     inside the sheet as an inline error.
 *
 * The sheet uses [AlertDialog] (with a custom layout body) rather
 * than `ModalBottomSheet` because the rest of the codebase
 * consistently uses dialogs for transient prompts and the M3
 * BottomSheet API is still experimental (would force
 * @OptIn(ExperimentalMaterial3Api) on every caller).
 */
@Composable
fun WithdrawSheet(
    currentDiamonds: Long,
    onDismiss: () -> Unit,
    onStartKyc: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var amountText by remember { mutableStateOf("") }
    var iban by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    // Three mutually-exclusive outcome states. Only one is non-null
    // at a time; the body branches on whichever fires last.
    var needsReverify by remember { mutableStateOf(false) }
    var notAvailable by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var successAmount by remember { mutableStateOf<Long?>(null) }

    // Diamonds → SAR estimate. Same conversion factor used on the
    // wallet card so the user sees a consistent number across both
    // surfaces.
    val parsedAmount = amountText.toLongOrNull() ?: 0L
    val amountValid = parsedAmount in 1000..currentDiamonds
    val ibanTrimmed = iban.trim()
    val ibanValid = ibanTrimmed.length in 22..34 &&
        ibanTrimmed.all { it.isLetterOrDigit() }
    val submitEnabled = !loading && amountValid && ibanValid

    AlertDialog(
        onDismissRequest = {
            if (!loading) onDismiss()
        },
        title = {
            Text(
                "سحب الأرباح",
                color = HalqaColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column {
                if (needsReverify) {
                    ReverifyBanner(onStartKyc = onStartKyc)
                    Spacer(Modifier.height(12.dp))
                }
                if (notAvailable) {
                    NotAvailableBanner()
                    Spacer(Modifier.height(12.dp))
                }
                if (successAmount != null) {
                    SuccessBanner(amountDiamonds = successAmount ?: 0L)
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    "رصيدك الحالي: ${currentDiamonds} Diamond",
                    color = HalqaColors.TextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))

                Text("مبلغ السحب (Diamond)", color = HalqaColors.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                HalqaTextField(
                    value = amountText,
                    onValueChange = { raw ->
                        amountText = raw.filter { it.isDigit() }.take(7)
                    },
                    placeholder = "1000 كحدّ أدنى",
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "الحد الأدنى 1000 Diamond (≈ 375 ريال).",
                    color = HalqaColors.TextDim,
                    fontSize = 11.sp,
                )

                Spacer(Modifier.height(12.dp))
                Text("الآيبان (IBAN)", color = HalqaColors.TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                HalqaTextField(
                    value = iban,
                    onValueChange = { raw ->
                        iban = raw.uppercase().filter { it.isLetterOrDigit() }.take(34)
                    },
                    placeholder = "SA0380000000608010167519",
                    keyboardType = KeyboardType.Ascii,
                )

                inlineError?.let { msg ->
                    Spacer(Modifier.height(10.dp))
                    Text(msg, color = HalqaColors.Pink, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (loading) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = HalqaColors.BrandLight,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                GoldButton(
                    text = "تأكيد السحب",
                    enabled = submitEnabled,
                    onClick = {
                        // Reset prior outcomes so the next attempt
                        // doesn't blend banners — the user can retry
                        // without first dismissing the sheet.
                        needsReverify = false
                        notAvailable = false
                        inlineError = null
                        successAmount = null

                        loading = true
                        scope.launch {
                            try {
                                val resp = ApiClient.api.withdrawWallet(
                                    WithdrawRequest(
                                        amountDiamonds = parsedAmount,
                                        iban = ibanTrimmed,
                                    ),
                                )
                                successAmount = resp.amountDiamonds.takeIf { it > 0 }
                                    ?: parsedAmount
                            } catch (httpErr: HttpException) {
                                val code = httpErr.code()
                                val body = readErrorBody(httpErr)
                                when {
                                    code == 403 && body.contains("KYC_BYPASS_REVERIFY_REQUIRED") ->
                                        needsReverify = true
                                    code == 503 && body.contains("WITHDRAW_NOT_AVAILABLE") ->
                                        notAvailable = true
                                    else ->
                                        inlineError = httpErr.humanize()
                                }
                            } catch (t: Throwable) {
                                inlineError = t.humanize()
                            } finally {
                                loading = false
                            }
                        }
                    },
                    fillMaxWidth = false,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!loading) onDismiss() },
                enabled = !loading,
            ) {
                Text(
                    "إلغاء",
                    color = if (loading) HalqaColors.TextDim else HalqaColors.TextMuted,
                )
            }
        },
        containerColor = HalqaColors.BgElevated,
    )
}

@Composable
private fun ReverifyBanner(onStartKyc: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HalqaColors.Warning.copy(alpha = 0.12f))
            .border(1.dp, HalqaColors.Warning.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🛡️", fontSize = 18.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                "تحتاج إعادة التحقق من الهوية لإتمام السحب",
                color = HalqaColors.Warning,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "حسابك انضم خلال النسخة التجريبية بدون توثيق هوية كامل. لإتمام السحب يلزم إكمال التحقق.",
            color = HalqaColors.TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onStartKyc) {
                Text(
                    "ابدأ التحقق",
                    color = HalqaColors.Warning,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NotAvailableBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HalqaColors.BgElevated)
            .border(1.dp, HalqaColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            "السحب غير متاح حالياً في الإصدار التجريبي. سنُشعرك فور تفعيله.",
            color = HalqaColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SuccessBanner(amountDiamonds: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HalqaColors.BrandLight.copy(alpha = 0.12f))
            .border(1.dp, HalqaColors.BrandLight.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            "تم استلام طلبك بنجاح (${amountDiamonds} Diamond). ستتم مراجعته خلال 1-3 أيام عمل.",
            color = HalqaColors.BrandLight,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Pulls the response error body out of a [HttpException] for the
 * `KYC_BYPASS_REVERIFY_REQUIRED` / `WITHDRAW_NOT_AVAILABLE` substring
 * match. We can't use the central `humanize()` helper for the
 * substring check because that function already converts the body
 * to a localised Arabic string, losing the stable English error code.
 *
 * The body is buffered by Retrofit so reading it once here is safe;
 * humanize() will re-read it in the fallback branch via its own
 * cached read.
 */
private fun readErrorBody(httpErr: HttpException): String {
    return try {
        httpErr.response()?.errorBody()?.string().orEmpty()
    } catch (_: Throwable) {
        ""
    }
}
