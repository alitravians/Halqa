package com.halqa.app.ui.screens.golive

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.halqa.app.data.FirebaseAuthRepository
import com.halqa.app.data.SafetyPrefs
import com.halqa.app.livekit.BroadcastSession
import com.halqa.app.livekit.LiveBroadcastService
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TimeZone

// The pre-broadcast category chip list was deleted along with its `var
// category` state. The chips were unwired in exactly the same way as the
// audio-only / allow-PK / chat-filter toggles removed in PR #37: the
// selected value lived in a private Compose `mutableStateOf`, the user
// could click and visually "pick" a category, but `launchBroadcast()`
// never read the value, `BroadcastSession.start()` did not accept it,
// `LiveKitTokenRequest` had no `category` field, and the backend
// `streams/{id}` doc has no `category` column for `FeedScreen` to
// filter on (`StreamPreview.category` is hard-coded to "الكل" by
// `FeedScreen` because there's nothing to read).
//
// A control that lets the user think they're picking "موسيقى" when in
// reality every stream they create is uncategorised is a UX lie — the
// host expects to appear under the موسيقى chip in `FeedScreen` and
// silently does not, with no error message.
//
// Bring this back when categories are an end-to-end feature: backend
// validation against an allow-list, persistence on the streams doc,
// `LiveStreamDto.category`, and a real `FeedScreen` filter that reads
// it.

@Composable
fun GoLivePrepScreen(navController: NavController) {
    val context = LocalContext.current

    // Defence-in-depth #1 (UI gate): refuse to render the broadcast prep
    // surface at all if there's no Firebase session. Previously this case
    // fell through to launchBroadcast() which showed the camera/mic
    // permission rationale dialog — extremely misleading because the user
    // had granted both permissions and the real cause was "not signed in".
    //
    // We observe `authStateFlow()` so that if the user signs out from
    // Settings (or session is revoked, or token expires after long idle,
    // or password reset on another device), this screen reactively flips
    // to the sign-in notice instead of a stale snapshot. The current
    // FirebaseAuth user is used as `initialValue` to avoid a cold-start
    // flash of the notice on first composition.
    val firebaseUser by FirebaseAuthRepository.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = FirebaseAuth.getInstance().currentUser)
    val firebaseUid = firebaseUser?.uid
    if (firebaseUid.isNullOrBlank()) {
        SignInRequiredNotice(
            onSignIn = {
                navController.navigate(Routes.Auth) {
                    popUpTo(Routes.Main) { inclusive = false }
                }
            },
        )
        return
    }

    var title by remember { mutableStateOf("") }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showSignInRequired by remember { mutableStateOf(false) }
    var showReadyToStream by remember { mutableStateOf(false) }

    // Layla GR3 — broadcaster ≥18 gate.
    //
    // The DOB is attested at sign-in time on the [Routes.DateOfBirth]
    // screen and stamped onto `/users/{uid}.dob`. We mirror the value
    // here on screen entry so the synchronous `launchBroadcast()`
    // closure can decide without a coroutine — fetching inside the
    // closure would force every "ابدأ البث" tap to spinner-and-wait
    // even when the value is cached.
    //
    // Three states matter:
    //   - `null`: still loading (or read failed). The button is left
    //     enabled so the user isn't blocked on a transient Firestore
    //     hiccup; `launchBroadcast()` will fall through to the legacy
    //     path. Worst case is the broadcast starts and the backend
    //     /api/livekit/token + dob backfill catches it server-side.
    //   - "" (empty string): doc exists but `dob` field is missing.
    //     This is the grandfathered closed-beta path (signed up under
    //     v0.1.21 before GR3 shipped). We force them through the DOB
    //     screen rather than blocking outright so they have a way to
    //     recover.
    //   - ISO date: parse and check (today − dob).years >= 18.
    var attestedDobIso by remember { mutableStateOf<String?>(null) }
    var dobLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(firebaseUid) {
        attestedDobIso = runCatching {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(firebaseUid)
                .get()
                .await()
                .getString("dob")
                .orEmpty()
        }.getOrNull()
        dobLoaded = true
    }
    var showUnderAgeBlock by remember { mutableStateOf(false) }
    var showDobMissingBlock by remember { mutableStateOf(false) }

    // Re-read the in-memory acceptance flag whenever this screen returns to the
    // foreground (e.g. after the user navigated to AgeGateScreen and came back).
    // We deliberately *do not* auto-`navigate(AgeGate)` from a LaunchedEffect:
    // that pattern caused a navigation loop when the user tapped Cancel on the
    // gate (gate pops back here -> LaunchedEffect fires again -> gate again).
    // Instead, when the gate is not yet accepted, we render an inline
    // [AgeGateInlineNotice] inside the Live tab. The user has a single explicit
    // CTA to open the AgeGate route, can cancel safely from inside it, and is
    // free to switch tabs (the NavigationBar is still visible).
    var ageGateAccepted by remember { mutableStateOf(SafetyPrefs.hasAcceptedAgeGate()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ageGateAccepted = SafetyPrefs.hasAcceptedAgeGate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (!ageGateAccepted) {
        AgeGateInlineNotice(
            onOpenAgeGate = { navController.navigate(Routes.AgeGate) },
        )
        return
    }

    fun launchBroadcast() {
        // Defence-in-depth #2: even if the UI gate let us reach this point
        // without auth, refuse to start a broadcast without a Firebase uid.
        // BroadcastSession + the backend `/api/livekit/token` endpoint also
        // re-check, so a missing uid here is a soft failure with a real
        // Arabic-language explanation, NOT the misleading permission dialog.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            showSignInRequired = true
            return
        }

        // Layla GR3 — broadcaster ≥18 gate.
        //
        // [attestedDobIso] is hydrated by the LaunchedEffect at the top
        // of this Composable. The semantics are deliberately
        // permissive on the "still loading" path so a transient
        // Firestore stall does not block a known-adult broadcaster
        // from going live. The hard blocks are:
        //
        //  1. Doc loaded but `dob` field is empty/missing → user
        //     signed up before GR3 shipped (closed-beta v0.1.20/21
        //     grandfather). Send them through the DOB screen rather
        //     than letting them broadcast un-attested.
        //  2. `dob` is set and parses to < 18 years old → hard
        //     non-dismissible dialog, no path to start the stream.
        //
        // We compute `today` in UTC because `dob` is also stored as a
        // UTC ISO date (see DateOfBirthScreen.persistDob); using a
        // local-timezone "today" would let a user born today open the
        // app from a UTC-ahead timezone and pass the gate a few hours
        // early. We use [Calendar] instead of `java.time` because the
        // app's `minSdk = 24` lacks the `java.time` runtime without
        // core-library desugaring.
        val dobIso = attestedDobIso
        if (dobLoaded) {
            if (dobIso.isNullOrBlank()) {
                showDobMissingBlock = true
                return
            }
            val years = computeAgeYearsFromIsoDob(dobIso)
            if (years != null && years < 18) {
                showUnderAgeBlock = true
                return
            }
            // Falls through on `years == null`: the doc has a
            // malformed `dob` value, which should be impossible
            // because the writer is our own DateOfBirthScreen.
            // Server-side gates are the safety net.
        }

        val streamId = "u_${uid}_${System.currentTimeMillis()}"
        BroadcastSession.start(
            appContext = context.applicationContext,
            streamId = streamId,
            title = title.ifBlank { null },
        )
        LiveBroadcastService.start(context.applicationContext)
        navController.navigate(Routes.Broadcasting)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val cam = result[Manifest.permission.CAMERA] == true
        val mic = result[Manifest.permission.RECORD_AUDIO] == true
        if (cam && mic) {
            launchBroadcast()
        } else {
            showPermissionRationale = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            "ابدأ حلقتك",
            color = HalqaColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "اضبط بثك المباشر قبل الانطلاق.",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.radialGradient(listOf(Color(0xFF1F1144), Color(0xFF0A0A1A))))
                .border(1.dp, HalqaColors.Border, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Camera, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "معاينة الكاميرا",
                    color = HalqaColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("سيتم تفعيل الكاميرا عند البدء", color = HalqaColors.TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        HalqaTextField(
            value = title,
            onValueChange = { title = it },
            label = "عنوان البث",
            placeholder = "مثال: حلقة الترفيه المسائية",
        )

        // The pre-broadcast options card ("audio-only", "allow PK", "auto chat
        // filters") was removed because none of those toggles were wired
        // anywhere — `ToggleRow` kept its `checked` state in private
        // `remember { mutableStateOf(false) }` and never propagated it. So:
        //   - "بث الصوت فقط" looked like it disabled the camera, but
        //     `BroadcastSession.start()` always called `setCameraEnabled(true)`.
        //     A privacy-adjacent toggle that lies to the user is worse than
        //     not having the toggle at all.
        //   - "السماح بدخول PK" did nothing — there is no PK-matchmaking
        //     server flag yet.
        //   - "الفلاتر التلقائية للشات" did nothing — chat filtering hasn't
        //     shipped (chat itself is read-only on the client right now).
        //
        // Bring them back when the underlying features ship and the toggle is
        // actually plumbed through BroadcastSession + /api/livekit/token + the
        // chat-moderation pipeline.

        Spacer(Modifier.height(20.dp))

        // Pre-flight reminder card (shown on every visit per Layla's T&S guidance —
        // "explicit before each broadcast", complementing the one-time AgeGate).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HalqaColors.BgElevated)
                .border(1.dp, HalqaColors.Warning.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = HalqaColors.Warning, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    "تذكير قبل البث",
                    color = HalqaColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            ReminderLine(Icons.Filled.ChildCare, "ممنوع البث لمن هم دون 18 سنة، وممنوع ظهور الأطفال في الكاميرا.")
            ReminderLine(Icons.Filled.Shield, "النظام يراجع البث تلقائياً، ويوقفه فوراً عند الاشتباه.")
            ReminderLine(Icons.Filled.Gavel, "تتدرّج العقوبات: تحذير → 24 ساعة → 7 أيام → 30 يوم → دائم.")
        }

        Spacer(Modifier.height(20.dp))

        PrimaryButton(
            text = "ابدأ البث الآن",
            onClick = { showWarningDialog = true },
        )
        Spacer(Modifier.height(12.dp))
        GoldButton(text = "جدولة بث", onClick = { /* schedule */ })

        Spacer(Modifier.height(40.dp))
    }

    if (showWarningDialog) {
        PreStreamWarningDialog(
            onDismiss = { showWarningDialog = false },
            onConfirm = {
                showWarningDialog = false
                val haveCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val haveMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (haveCam && haveMic) {
                    launchBroadcast()
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }
            },
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }) { Text("فتح الإعدادات", color = HalqaColors.BrandLight) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("لاحقاً", color = HalqaColors.TextMuted)
                }
            },
            title = { Text("نحتاج صلاحيات الكاميرا والميكروفون", color = HalqaColors.Text) },
            text = {
                Text(
                    "البث المباشر يتطلب الوصول للكاميرا والميكروفون. لا نسجّل أي شيء خارج البث، ولا نشاركه مع أي طرف ثالث. افتح الإعدادات لتفعيل الصلاحيتين.",
                    color = HalqaColors.TextMuted,
                )
            },
            containerColor = HalqaColors.BgElevated,
        )
    }

    if (showSignInRequired) {
        AlertDialog(
            onDismissRequest = { showSignInRequired = false },
            confirmButton = {
                TextButton(onClick = {
                    showSignInRequired = false
                    navController.navigate(Routes.Auth) {
                        // Don't pop back to a half-prepared go-live state.
                        popUpTo(Routes.Main) { inclusive = false }
                    }
                }) { Text("تسجيل الدخول", color = HalqaColors.BrandLight) }
            },
            dismissButton = {
                TextButton(onClick = { showSignInRequired = false }) {
                    Text("لاحقاً", color = HalqaColors.TextMuted)
                }
            },
            title = { Text("يلزم تسجيل الدخول", color = HalqaColors.Text) },
            text = {
                Text(
                    "لبدء بث مباشر، يجب تسجيل الدخول أولاً. هذا لربط البث بحسابك وتفعيل الإشعارات وحفظ السجل.",
                    color = HalqaColors.TextMuted,
                )
            },
            containerColor = HalqaColors.BgElevated,
        )
    }

    if (showReadyToStream) {
        AlertDialog(
            onDismissRequest = { showReadyToStream = false },
            confirmButton = {
                TextButton(onClick = { showReadyToStream = false }) {
                    Text("تمام", color = HalqaColors.BrandLight)
                }
            },
            title = { Text("جاهز للبث", color = HalqaColors.Text) },
            text = {
                Text(
                    "تم تأكيد التعهّدات وتفعيل الصلاحيات. سيتم تشغيل البث الفعلي عبر LiveKit في المرحلة القادمة من التطوير.",
                    color = HalqaColors.TextMuted,
                )
            },
            containerColor = HalqaColors.BgElevated,
        )
    }

    // Layla GR3 — under-18 broadcaster block. Non-dismissible by tap-
    // outside (the only escape is the explicit "حسناً" button) and has
    // no path to start the broadcast. The age threshold is hard-coded
    // because changing it must be a deliberate, auditable code change,
    // not an env var.
    if (showUnderAgeBlock) {
        AlertDialog(
            onDismissRequest = { /* non-dismissible */ },
            confirmButton = {
                TextButton(onClick = { showUnderAgeBlock = false }) {
                    Text("حسناً", color = HalqaColors.BrandLight)
                }
            },
            title = { Text("البث المباشر للبالغين فقط", color = HalqaColors.Text) },
            text = {
                Text(
                    "البث المباشر متاح لمن أعمارهم 18 سنة فأكثر. " +
                        "يمكنك الاستمرار في تصفح البث ودعم المبدعين بالهدايا.",
                    color = HalqaColors.TextMuted,
                )
            },
            containerColor = HalqaColors.BgElevated,
        )
    }

    // Layla GR3 — grandfathered users (signed up under v0.1.20/21
    // before this gate shipped) are routed back through the DOB
    // attestation screen instead of being silently blocked. After
    // they confirm a DOB, they hit Main and `attestedDobIso` will
    // be populated on next entry to GoLivePrep.
    if (showDobMissingBlock) {
        AlertDialog(
            onDismissRequest = { showDobMissingBlock = false },
            confirmButton = {
                TextButton(onClick = {
                    showDobMissingBlock = false
                    navController.navigate(Routes.DateOfBirth)
                }) { Text("تأكيد التاريخ", color = HalqaColors.BrandLight) }
            },
            dismissButton = {
                TextButton(onClick = { showDobMissingBlock = false }) {
                    Text("لاحقاً", color = HalqaColors.TextMuted)
                }
            },
            title = { Text("تحقق من العمر مطلوب", color = HalqaColors.Text) },
            text = {
                Text(
                    "نحتاج لتأكيد تاريخ ميلادك قبل البث المباشر. هذه خطوة لمرة واحدة.",
                    color = HalqaColors.TextMuted,
                )
            },
            containerColor = HalqaColors.BgElevated,
        )
    }
}

@Composable
private fun PreStreamWarningDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("أتعهّد و أبدأ البث", color = HalqaColors.BrandLight) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء", color = HalqaColors.TextMuted) }
        },
        title = { Text("قبل بدء البث", color = HalqaColors.Text) },
        text = {
            Column {
                Text(
                    "بضغطك على \"أتعهّد و أبدأ البث\" تؤكد:",
                    color = HalqaColors.TextMuted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                ReminderLine(Icons.Filled.ChildCare, "أن عمرك 18 سنة فأكثر.")
                ReminderLine(Icons.Filled.Shield, "أنه لن يظهر أي قاصر في بثك.")
                ReminderLine(Icons.Filled.Gavel, "أن النظام قد يفتح مراجعة 10 دقائق ثم يصدر العقوبة.")
            }
        },
        containerColor = HalqaColors.BgElevated,
    )
}

@Composable
private fun ReminderLine(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = HalqaColors.Warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, color = HalqaColors.Text, fontSize = 12.sp, lineHeight = 20.sp)
    }
}

/**
 * Full-screen blocker shown inside the Live tab whenever
 * [SafetyPrefs.hasAcceptedAgeGate] is false. Replaces the previous
 * `LaunchedEffect { navigate(Routes.AgeGate) }` redirect that produced an
 * infinite loop when users cancelled the gate (the gate would re-pop here and
 * the effect would fire again).
 *
 * The bottom NavigationBar is still visible so the user can always switch
 * tabs to leave; the only forward action is the explicit "افتح ميثاق
 * التعهّدات" button.
 */
@Composable
private fun AgeGateInlineNotice(onOpenAgeGate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(HalqaColors.Danger.copy(alpha = 0.18f))
                .border(1.dp, HalqaColors.Danger.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = HalqaColors.Danger, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "البث المباشر يحتاج تعهّد سلامة أوّلاً",
            color = HalqaColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "قبل بثك الأول، اقرأ ميثاق السلامة و وقّع على ثلاثة تعهّدات: العمر 18+، عدم ظهور قاصرين، و قبول مراجعة المخالفات لمدة 10 دقائق. هذا إلزامي لجميع المذيعين.",
            color = HalqaColors.TextMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HalqaColors.BgElevated)
                .border(1.dp, HalqaColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            ReminderLine(icon = Icons.Filled.Warning, text = "ممنوع منعاً باتاً البث لمن هم دون 18 سنة.")
            ReminderLine(icon = Icons.Filled.ChildCare, text = "ممنوع ظهور أي قاصر في إطار الكاميرا.")
            ReminderLine(icon = Icons.Filled.Gavel, text = "نظام المراقبة الذاتي يفتح مراجعة 10 دقائق ثم يُصدر العقوبة.")
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "افتح ميثاق التعهّدات", onClick = onOpenAgeGate)
        Spacer(Modifier.height(12.dp))
        Text(
            "يمكنك التنقّل لتبويب آخر في أي وقت من الشريط السفلي.",
            color = HalqaColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

/**
 * Inline notice rendered in place of the broadcast prep UI when the user
 * has no Firebase session. Replaces the previous misleading "permission
 * rationale" dialog that was shown for both genuine permission denial AND
 * missing-auth — the user couldn't tell why broadcasting wouldn't start.
 */
@Composable
private fun SignInRequiredNotice(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(HalqaColors.BrandDark.copy(alpha = 0.18f))
                .border(1.dp, HalqaColors.BrandLight.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = HalqaColors.BrandLight, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "يلزم تسجيل الدخول لبدء البث",
            color = HalqaColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "لبدء بث مباشر، نحتاج ربط البث بحسابك حتى تظهر هويتك للمشاهدين، ويُحفظ السجل، وتعمل الإشعارات والإجراءات الإدارية.",
            color = HalqaColors.TextMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = "تسجيل الدخول", onClick = onSignIn)
        Spacer(Modifier.height(12.dp))
        Text(
            "لا يلزمك دفع أو تحقّق هوية لتجربة البث في الإصدار التجريبي.",
            color = HalqaColors.TextMuted,
            fontSize = 12.sp,
        )
    }
}

/**
 * Compute the broadcaster's age in completed years from a `dob`
 * stored as a UTC ISO date string ("YYYY-MM-DD" — the format written
 * by [DateOfBirthScreen.persistDob]). Returns `null` if the input is
 * malformed; the caller treats `null` as "fall through to backend
 * gate" rather than as "block".
 *
 * Implemented with [java.util.Calendar] in UTC because the app's
 * `minSdk = 24` lacks the `java.time` runtime without core-library
 * desugaring.
 */
private fun computeAgeYearsFromIsoDob(iso: String): Int? {
    val parts = iso.split("-")
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    val today = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    var years = today.get(Calendar.YEAR) - y
    val curMonth = today.get(Calendar.MONTH) + 1
    val curDay = today.get(Calendar.DAY_OF_MONTH)
    if (curMonth < m || (curMonth == m && curDay < d)) {
        years -= 1
    }
    return years
}
