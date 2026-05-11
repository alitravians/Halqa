package com.halqa.app.ui.screens.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.halqa.app.data.OnboardingPrefs
import com.halqa.app.ui.theme.HalqaColors

/**
 * Lina — POST_NOTIFICATIONS runtime permission gate (Android 13+).
 *
 * Why it lives here (not in MainActivity.onCreate):
 *   - The first runtime ask should happen AFTER sign-in + DOB are
 *     resolved, not at cold-start where the user has no context yet.
 *     Anchoring it on the first composition of `Main` guarantees the
 *     user has just landed in the app after completing onboarding.
 *   - As a Composable we get the ActivityResultLauncher for free via
 *     `rememberLauncherForActivityResult`, no Activity plumbing.
 *
 * Flow:
 *   1. Composable mounts. If pre-Android-13 → no-op. If permission
 *      already granted → no-op (and we mark the flag so we never
 *      re-check). If [OnboardingPrefs.wasNotificationsAsked] → no-op
 *      (user already saw this rationale + system prompt and either
 *      granted or denied; we respect the denial and don't nag).
 *   2. Otherwise show an Arabic rationale `AlertDialog` explaining
 *      WHY we want notifications (live alerts, gifts, replies). The
 *      Play 2024 policy section 2.7.4 explicitly requires this
 *      pre-prompt before any notification permission request.
 *   3. On "متابعة" → launch the system permission request.
 *   4. On either grant or deny, mark the flag so the dialog never
 *      reappears. If the user wants to flip it later they go through
 *      OS Settings → App info → Permissions; the rationale dialog is
 *      a one-shot.
 *   5. On "ليس الآن" → mark the flag (same one-shot rule) and dismiss.
 */
@Composable
fun NotificationsPermissionGate() {
    // Pre-Android-13 builds don't have the runtime permission concept
    // for POST_NOTIFICATIONS — the legacy AndroidManifest declaration
    // is sufficient. Bail before touching any Activity APIs.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _: Boolean ->
        // Regardless of grant/deny, mark so we never re-prompt on this
        // install. Grant: the OS now delivers our notifications.
        // Deny: respect it; user can re-enable later via Settings.
        OnboardingPrefs.markNotificationsAsked()
        showRationale = false
    }

    LaunchedEffect(Unit) {
        if (OnboardingPrefs.wasNotificationsAsked()) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            OnboardingPrefs.markNotificationsAsked()
            return@LaunchedEffect
        }
        showRationale = true
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                // Touching outside the dialog = "not now". Same one-shot rule.
                OnboardingPrefs.markNotificationsAsked()
                showRationale = false
            },
            containerColor = HalqaColors.BgElevated,
            title = {
                Text(
                    "فعّل التنبيهات",
                    color = Color.White,
                )
            },
            text = {
                Text(
                    "نرسل لك تنبيهات عند بدء بث مفضّل لديك، عند استلام هدية، أو عند الردّ عليك. يمكنك إيقافها لاحقاً من إعدادات الجهاز.",
                    color = Color.White.copy(alpha = 0.85f),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                ) {
                    Text("متابعة", color = HalqaColors.BrandLight)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        OnboardingPrefs.markNotificationsAsked()
                        showRationale = false
                    },
                ) {
                    Text("ليس الآن", color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }
}
