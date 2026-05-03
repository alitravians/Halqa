package com.halqa.app.ui.screens.profile

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.halqa.app.data.FirebaseAuthRepository
import com.halqa.app.data.remote.ApiClient
import com.halqa.app.data.remote.KycSubmitRequest
import com.halqa.app.data.remote.KycStatusDto
import com.halqa.app.data.remote.humanize
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * KYC submission + status screen.
 *
 * Reads the current submission from Firestore (`kyc_submissions/{uid}`) in
 * real time so the user sees `pending → approved/rejected` updates from the
 * Admin Panel without having to refresh.
 *
 * Submissions go through the backend so server-side validation, audit logs,
 * and PDPL constraints stay enforced in one place.
 */
@Composable
fun KycScreen(navController: NavController) {
    val firebaseUser by FirebaseAuthRepository.authStateFlow().collectAsState(initial = FirebaseAuthRepository.currentUser)
    val uid = firebaseUser?.uid

    if (uid == null) {
        SignInRequired(navController, title = "التحقق من الهوية (KYC)")
        return
    }

    // Lifecycle-aware so the underlying Firestore snapshot listener
    // (registered inside `observeKyc` via `addSnapshotListener`) is
    // detached when the user navigates away — without this, the
    // listener stays attached for the lifetime of the back-stack
    // entry and burns Firestore reads + battery while the user is
    // anywhere else in the app.
    val status by observeKyc(uid).collectAsStateWithLifecycle(initialValue = KycStatusDto())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ProfileHeader(title = "التحقق من الهوية (KYC)", onBack = { navController.popBackStack() })

        Spacer(Modifier.height(8.dp))
        StatusBanner(status)

        Spacer(Modifier.height(20.dp))
        when (status.status.lowercase()) {
            "approved" -> ApprovedExplainer()
            "pending" -> PendingExplainer()
            "rejected" -> RejectedExplainer(status.reason) { /* re-submit allowed below */ }
            else -> Unit
        }

        if (status.status.lowercase() != "approved" && status.status.lowercase() != "pending") {
            Spacer(Modifier.height(20.dp))
            SubmitForm()
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatusBanner(status: KycStatusDto) {
    val (label, sub, color) = when (status.status.lowercase()) {
        "approved" -> Triple("تم التحقق ✅", "الهوية مفعّلة على الحساب.", HalqaColors.Brand)
        "pending" -> Triple("قيد المراجعة ⏳", "أرسلنا طلبك للفريق المختص.", HalqaColors.Gold)
        "rejected" -> Triple("لم يُقبل الطلب ⚠️", status.reason ?: "راجع التفاصيل وأعد الإرسال.", Color(0xFFEF4444))
        else -> Triple("غير مُقدَّم", "أكمل النموذج لتفعيل الشارة الموثّقة.", HalqaColors.TextMuted)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column {
            Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(sub, color = HalqaColors.Text, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ApprovedExplainer() {
    Text(
        "حسابك يحمل شارة \"موثَّق\" ويمكنه استخدام مزايا التحقق الكاملة. " +
            "إذا غيّرت بياناتك، تواصل مع الدعم لإعادة المراجعة.",
        color = HalqaColors.TextMuted,
        fontSize = 13.sp,
    )
}

@Composable
private fun PendingExplainer() {
    Text(
        "تتم المراجعة عادة خلال 24-48 ساعة. سنخبرك فور صدور القرار. " +
            "تأكد من تشغيل إشعارات Push لتصلك التحديثات.",
        color = HalqaColors.TextMuted,
        fontSize = 13.sp,
    )
}

@Composable
private fun RejectedExplainer(reason: String?, onRetry: () -> Unit) {
    Column {
        Text(
            "السبب الذي ذكره فريق المراجعة:",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            reason?.takeIf { it.isNotBlank() } ?: "لم يذكر سبب محدد. تحقق من جودة الصور ومن مطابقة الاسم.",
            color = HalqaColors.Text,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        SubmitForm()
    }
}

@Composable
private fun SubmitForm() {
    var fullName by remember { mutableStateOf("") }
    var documentNumber by remember { mutableStateOf("") }
    var identityType by remember { mutableStateOf("national_id") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Text(
        "نحتاج البيانات أدناه لمطابقة هويتك (تُحفظ مشفّرة وتُستخدم للتحقق فقط).",
        color = HalqaColors.TextMuted,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(12.dp))

    Text("نوع الوثيقة", color = HalqaColors.TextMuted, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "national_id" to "هوية وطنية",
            "passport" to "جواز سفر",
            "residency" to "إقامة",
        ).forEach { (key, label) ->
            val isSel = identityType == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) HalqaColors.Brand else HalqaColors.BgElevated)
                    .border(1.dp, if (isSel) HalqaColors.Brand else HalqaColors.Border, RoundedCornerShape(10.dp))
                    .clickable { identityType = key }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(label, color = if (isSel) Color.White else HalqaColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("الاسم الكامل (كما في الوثيقة)", color = HalqaColors.TextMuted, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    HalqaTextField(value = fullName, onValueChange = { fullName = it.take(80) }, placeholder = "الاسم الرباعي")

    Spacer(Modifier.height(14.dp))
    Text("رقم الوثيقة", color = HalqaColors.TextMuted, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    HalqaTextField(
        value = documentNumber,
        onValueChange = { documentNumber = it.filter { c -> c.isLetterOrDigit() }.take(24) },
        placeholder = "1234567890",
        keyboardType = KeyboardType.Ascii,
    )

    Spacer(Modifier.height(14.dp))
    Text(
        "ملاحظة: رفع صور الوثيقة سيتم في الإصدار التالي عبر Firebase Storage. " +
            "حتى ذلك الحين سيقوم فريق المراجعة بطلب الصور منك بعد استلام الطلب.",
        color = HalqaColors.TextDim,
        fontSize = 11.sp,
    )

    feedback?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = HalqaColors.Pink, fontSize = 13.sp)
    }

    Spacer(Modifier.height(16.dp))
    GoldButton(
        text = if (submitting) "يرسل..." else "إرسال للمراجعة",
        onClick = {
            if (submitting) return@GoldButton
            // Match the backend's authoritative bounds in `kyc/submit/route.ts`
            // exactly: fullName.trim() >= 3, documentNumber.trim() >= 4. The
            // Android UI was previously stricter (`< 4` and `< 5`), which
            // silently locked out legitimate 3-character Arabic names
            // ("علي", "تيم", "عمر") and 4-digit document numbers — the user
            // saw a useless "أدخل الاسم الكامل" message that didn't
            // explain the actual minimum, which the backend would have
            // accepted anyway.
            if (fullName.trim().length < 3) {
                feedback = "الاسم الكامل يجب أن يكون 3 أحرف على الأقل."
                return@GoldButton
            }
            if (documentNumber.trim().length < 4) {
                feedback = "رقم الوثيقة يجب أن يكون 4 خانات على الأقل."
                return@GoldButton
            }
            feedback = null
            submitting = true
            scope.launch {
                try {
                    ApiClient.api.submitKyc(
                        KycSubmitRequest(
                            identityType = identityType,
                            fullName = fullName.trim(),
                            documentNumber = documentNumber.trim(),
                            images = emptyList(),
                        )
                    )
                    submitting = false
                    feedback = "تم إرسال طلبك. ستظهر الحالة \"قيد المراجعة\" خلال ثوانٍ."
                } catch (t: Throwable) {
                    submitting = false
                    feedback = "تعذّر الإرسال: ${t.humanize()}"
                }
            }
        },
        fillMaxWidth = true,
    )
}

private fun observeKyc(uid: String) = callbackFlow {
    val reg = FirebaseFirestore.getInstance()
        .collection("kyc_submissions").document(uid)
        .addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(KycStatusDto())
                return@addSnapshotListener
            }
            val data = snap?.data
            if (data == null) {
                trySend(KycStatusDto())
                return@addSnapshotListener
            }
            trySend(
                KycStatusDto(
                    status = (data["status"] as? String) ?: "none",
                    submittedAt = data["submittedAt"] as? String,
                    approvedAt = data["approvedAt"] as? String,
                    reason = data["reason"] as? String,
                    identityType = data["identityType"] as? String,
                )
            )
        }
    awaitClose { reg.remove() }
}
