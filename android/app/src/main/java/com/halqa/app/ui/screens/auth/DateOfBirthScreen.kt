package com.halqa.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TimeZone

/**
 * Layla T&S guardrail GR3 — date-of-birth self-attestation.
 *
 * Why it exists
 * -------------
 * Halqa's two consumer auth methods (Phone OTP, Google Sign-In) ship
 * with `BYPASS_KYC_FOR_BETA=true`, which means the closed-beta build
 * does not call the regular KYC funnel. Layla flagged that SA prepaid
 * (Sawa, Mobily, Zain) retailers do not enforce date-of-birth strictly
 * at SIM activation, so the carrier signal alone is insufficient
 * evidence that a sign-up is an adult. Google Sign-In is even worse —
 * a 13-year-old's family Google account works fine.
 *
 * The screen does NOT replace KYC. It is a self-attested age gate
 * good enough to:
 *   1. Block under-13s outright (Material3 [SelectableDates] caps
 *      the picker at today − 13y so nothing more recent is even
 *      tappable; the field constraint matches the kids-content
 *      regulatory line we cannot cross under any circumstance).
 *   2. Stamp `dob` + `dob_attested_at` durably on `/users/{uid}` so
 *      the broadcaster gate in [com.halqa.app.ui.screens.golive.GoLivePrepScreen.launchBroadcast]
 *      can hard-block a < 18 user from going live.
 *   3. Emit an independent audit record at
 *      `/audit/{uid}/events` (the same collection used by GR2 for
 *      the KYC bypass grant) with `dob_year_only` instead of the
 *      full date — minimising PII in the audit log per Layla's spec.
 *
 * Failure modes
 * -------------
 * Firestore writes can fail (offline, transient network). The screen
 * handles them by:
 *   - Showing an Arabic error message inline so the user understands
 *     they're stuck on the gate (rather than silently passing through
 *     without a `dob` field, which would defeat the whole point).
 *   - Keeping the "تأكيد" button enabled so a retry doesn't require
 *     re-picking the date.
 *
 * Idempotency
 * -----------
 * The Firestore rule on `/users/{userId}` (see [firebase/firestore.rules])
 * forbids changing `dob` once it is set — the field is one-shot.
 * Re-entering this screen with a doc that already has `dob` is a
 * no-op: the LaunchedEffect that guards entry skips the screen and
 * navigates straight to [Routes.Main]. The grandfathering path on
 * v0.1.22 first-launch (users signed up under v0.1.21 with no `dob`)
 * routes through here exactly once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateOfBirthScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Idempotency guard: a re-login on a user whose `dob` is already
    // set (or an accidental nav back here) skips the picker and
    // proceeds straight to Main. Without this guard the user would
    // see the picker, attempt to "تأكيد", and hit the firestore
    // immutability rule that forbids changing `dob` once set —
    // surfacing as the inline "تعذّر حفظ التاريخ" error which is
    // misleading for what is really "you've already done this".
    //
    // [precheckDone] gates the entire picker UI behind the read so
    // returning users don't get a flash of the picker while the
    // precheck is in flight.
    var precheckDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            precheckDone = true
            return@LaunchedEffect
        }
        val existing = runCatching {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .await()
                .getString("dob")
        }.getOrNull()
        if (!existing.isNullOrBlank()) {
            navController.navigate(Routes.Main) {
                popUpTo(Routes.Auth) { inclusive = true }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }
        precheckDone = true
    }
    if (!precheckDone) {
        // Empty placeholder during the precheck. Showing a spinner here
        // would imply progress where there is none for >99% of new
        // sign-ups; the read is fast enough that a blank background
        // is the calmest UX.
        Box(modifier = Modifier.fillMaxSize().background(HalqaColors.Bg))
        return
    }

    // Today's epoch-ms at UTC midnight. Material3 DatePicker rejects
    // candidate timestamps via [SelectableDates.isSelectableDate]; we
    // cap selection at today − 13 years to make picking an under-13
    // value literally impossible. The 13-year floor is the kids'
    // content regulatory line (COPPA-style), not the broadcaster gate
    // (which is 18 and enforced post-attestation, not at the picker).
    //
    // We use [Calendar] instead of `java.time.LocalDate` because the
    // app's `minSdk` is 24 and the project does not enable core
    // library desugaring; `java.time` would crash at runtime on API
    // 24 / 25 with `NoClassDefFoundError`. Calendar is available
    // since API 1 and behaves identically with TimeZone("UTC").
    val (maxSelectableUtcMs, defaultUtcMs, currentYear) = remember {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val year = cal.get(Calendar.YEAR)
        cal.add(Calendar.YEAR, -13)
        val max = cal.timeInMillis
        cal.add(Calendar.YEAR, -5) // 13 + 5 = 18
        val default = cal.timeInMillis
        Triple(max, default, year)
    }

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = defaultUtcMs,
        yearRange = (currentYear - 100)..(currentYear - 13),
        selectableDates = remember(maxSelectableUtcMs) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= maxSelectableUtcMs

                override fun isSelectableYear(year: Int): Boolean =
                    year <= currentYear - 13
            }
        },
    )

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

            Text(
                "تاريخ الميلاد",
                color = HalqaColors.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                // Layla mandated copy: tells the user *why* we ask
                // (broadcasting requires 18+) without making it sound
                // like KYC. Self-attestation framing.
                "نحتاج تاريخ ميلادك للتحقق من العمر. " +
                    "البث المباشر متاح للبالغين فقط (18 سنة فأكثر).",
                color = HalqaColors.TextMuted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1A0F33)),
            ) {
                DatePicker(
                    state = pickerState,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                    colors = DatePickerDefaults.colors(
                        containerColor = Color(0xFF1A0F33),
                        titleContentColor = HalqaColors.Text,
                        headlineContentColor = HalqaColors.Text,
                        weekdayContentColor = HalqaColors.TextMuted,
                        subheadContentColor = HalqaColors.Text,
                        yearContentColor = HalqaColors.Text,
                        currentYearContentColor = HalqaColors.Text,
                        selectedYearContentColor = HalqaColors.Bg,
                        selectedYearContainerColor = Color(0xFF7C3AED),
                        dayContentColor = HalqaColors.Text,
                        selectedDayContentColor = Color.White,
                        selectedDayContainerColor = Color(0xFF7C3AED),
                        todayContentColor = HalqaColors.Text,
                        todayDateBorderColor = Color(0xFFEC4899),
                    ),
                )
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error!!,
                    color = Color(0xFFFF6B6B),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                text = if (saving) "جارِ الحفظ..." else "تأكيد",
                enabled = !saving && pickerState.selectedDateMillis != null,
                onClick = onClick@{
                    val selectedMs = pickerState.selectedDateMillis ?: return@onClick
                    saving = true
                    error = null
                    scope.launch {
                        val ok = persistDob(selectedMs)
                        if (ok) {
                            navController.navigate(Routes.Main) {
                                popUpTo(Routes.Auth) { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            saving = false
                            error = "تعذّر حفظ التاريخ. تحقق من الإنترنت وحاول مجدداً."
                        }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))

            // No "skip" button. The screen is required, by design.
            // The only escape is signing out, which is the dialog's
            // own escape hatch — same UX as KYC.
            GhostButton(
                text = "تسجيل الخروج",
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Routes.Auth) {
                        popUpTo(Routes.DateOfBirth) { inclusive = true }
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            Text(
                // Disclosure: we keep this short and bilingual-friendly.
                // The /audit event stores `dob_year_only`, NEVER the
                // full date — the fine print here matches Layla's
                // PDPL minimisation framing.
                "نحفظ تاريخ الميلاد على حسابك ولا نشاركه. " +
                    "سجل التدقيق يحتفظ بسنة الميلاد فقط.",
                color = HalqaColors.TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Persist the attested DOB to `/users/{uid}` and write the audit
 * trail. Returns true on success.
 *
 * The two writes are intentionally NOT in a Firestore transaction:
 *   - The user-doc update is the ONLY load-bearing write (the
 *     broadcaster gate reads `dob` from there).
 *   - The audit event is a best-effort durability record.
 * Wrapping them in a transaction would mean a transient failure on
 * the audit collection (e.g. rate-limit) blocks the user from
 * proceeding, which is worse UX than missing one audit row in a
 * very rare failure mode. The user-doc write is the source of truth.
 */
private suspend fun persistDob(selectedUtcMs: Long): Boolean {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
    val firestore = FirebaseFirestore.getInstance()

    // Format the UTC epoch-ms back to an ISO date string ("YYYY-MM-DD")
    // using [Calendar] in UTC. We deliberately avoid `java.time` because
    // the app's `minSdk = 24` lacks the `java.time` runtime without
    // core-library desugaring (see picker-state comment for context).
    //
    // Storing the date as a string instead of a Firestore Timestamp
    // avoids timezone foot-guns: a Timestamp at midnight UTC rendered
    // for a user in Riyadh would slip back a calendar day. A plain
    // ISO date is unambiguous and deterministic to parse.
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = selectedUtcMs
    }
    val dobYear = cal.get(Calendar.YEAR)
    val dobIso = String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        dobYear,
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )

    return try {
        firestore.collection("users").document(uid).update(
            mapOf(
                "dob" to dobIso,
                "dob_attested_at" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()

        // Audit event: year-only, per GR3 spec — full DOB lives on
        // the user doc but the audit log keeps only the granularity
        // staff need for "was this user old enough at sign-up?"
        // questions.
        runCatching {
            firestore.collection("audit")
                .document(uid)
                .collection("events")
                .add(
                    mapOf(
                        "uid" to uid,
                        "type" to "dob_attested",
                        "dob_attested_at" to FieldValue.serverTimestamp(),
                        "dob_year_only" to dobYear,
                    ),
                ).await()
        }
        true
    } catch (_: Throwable) {
        false
    }
}
