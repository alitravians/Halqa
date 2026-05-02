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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.halqa.app.data.SafetyPrefs
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.components.PrimaryButton
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors

private val categories = listOf("ترفيه", "موسيقى", "ألعاب", "دردشة", "تعليم", "طبخ", "رياضة", "ثقافة")

@Composable
fun GoLivePrepScreen(navController: NavController) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.first()) }
    var showWarningDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showReadyToStream by remember { mutableStateOf(false) }

    val context = LocalContext.current

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val cam = result[Manifest.permission.CAMERA] == true
        val mic = result[Manifest.permission.RECORD_AUDIO] == true
        if (cam && mic) {
            showReadyToStream = true
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

        Spacer(Modifier.height(16.dp))

        Text(
            "التصنيف",
            color = HalqaColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HalqaColors.BgElevated)
                    .border(1.dp, HalqaColors.Border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Column {
                    categories.chunked(4).forEach { row ->
                        Row {
                            row.forEach { c ->
                                val sel = c == category
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (sel) Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.05f))))
                                        .clickable { category = c }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(c, color = if (sel) Color.White else HalqaColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(HalqaColors.BgElevated)
                .border(1.dp, HalqaColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            ToggleRow("بث الصوت فقط", icon = Icons.Filled.Mic)
            Spacer(Modifier.height(8.dp))
            ToggleRow("السماح بدخول PK", icon = Icons.Filled.SettingsSuggest)
            Spacer(Modifier.height(8.dp))
            ToggleRow("الفلاتر التلقائية للشات", icon = Icons.Filled.SettingsSuggest)
        }

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
                    showReadyToStream = true
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

@Composable
private fun ToggleRow(label: String, icon: ImageVector) {
    var checked by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked },
    ) {
        Icon(icon, contentDescription = null, tint = HalqaColors.TextMuted)
        Spacer(Modifier.size(12.dp))
        Text(label, color = HalqaColors.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        // Force LTR for the toggle knob alignment. The whole app forces RTL
        // globally (MainActivity), which would otherwise flip CenterStart /
        // CenterEnd and place the knob on the wrong physical side. Material
        // spec explicitly says switches do not mirror in RTL.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (checked) HalqaColors.Brand else Color.White.copy(alpha = 0.12f)),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
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
