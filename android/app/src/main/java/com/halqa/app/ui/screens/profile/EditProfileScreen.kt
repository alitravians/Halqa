package com.halqa.app.ui.screens.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.halqa.app.data.FirebaseAuthRepository
import com.halqa.app.data.UserRepository
import com.halqa.app.data.remote.UpdateProfileRequest
import com.halqa.app.data.remote.humanize
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.HalqaTextField
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch

/**
 * Edit-profile screen — POST /api/users/me + Firestore listener for live mirroring.
 *
 * Shows the current Firestore profile (real-time), lets the user edit display
 * name / handle / bio, and submits via the backend so audit logging fires.
 */
@Composable
fun EditProfileScreen(navController: NavController) {
    val firebaseUser by FirebaseAuthRepository.authStateFlow().collectAsState(initial = FirebaseAuthRepository.currentUser)
    val uid = firebaseUser?.uid

    if (uid == null) {
        SignInRequired(navController, title = "تعديل الملف")
        return
    }

    val profile by UserRepository.observeProfile(uid).collectAsStateWithLifecycle(initialValue = null)

    var displayName by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile) {
        if (!loaded && profile != null) {
            displayName = profile?.displayName.orEmpty()
            handle = profile?.handle.orEmpty()
            bio = profile?.bio.orEmpty()
            loaded = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        ProfileHeader(title = "تعديل الملف", onBack = { navController.popBackStack() })

        if (!loaded) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HalqaColors.Brand)
            }
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        Text("الاسم الظاهر", color = HalqaColors.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        HalqaTextField(
            value = displayName,
            onValueChange = { displayName = it.take(60) },
            placeholder = "كيف تحب أن يراك المتابعون؟",
        )

        Spacer(Modifier.height(14.dp))
        Text("المعرّف (@handle)", color = HalqaColors.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        HalqaTextField(
            value = handle,
            onValueChange = { handle = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(24) },
            placeholder = "ali_traveler",
            keyboardType = KeyboardType.Ascii,
        )

        Spacer(Modifier.height(14.dp))
        Text("نبذة قصيرة", color = HalqaColors.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        HalqaTextField(
            value = bio,
            onValueChange = { bio = it.take(280) },
            placeholder = "اكتب عن نفسك بسطر أو سطرين",
            singleLine = false,
        )
        Text("${bio.length}/280", color = HalqaColors.TextDim, fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))

        Spacer(Modifier.height(20.dp))
        feedback?.let { msg ->
            Text(msg, color = HalqaColors.Pink, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "إلغاء",
                onClick = { navController.popBackStack() },
                fillMaxWidth = false,
                modifier = Modifier.weight(1f),
            )
            GoldButton(
                text = if (saving) "يحفظ..." else "حفظ",
                onClick = {
                    if (saving) return@GoldButton
                    feedback = null
                    saving = true
                    scope.launch {
                        try {
                            UserRepository.updateMe(
                                UpdateProfileRequest(
                                    displayName = displayName.trim(),
                                    handle = handle.trim(),
                                    bio = bio.trim(),
                                )
                            )
                            saving = false
                            navController.popBackStack()
                        } catch (t: Throwable) {
                            saving = false
                            feedback = "تعذّر الحفظ: ${t.humanize()}"
                        }
                    }
                },
                fillMaxWidth = false,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
internal fun ProfileHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "رجوع",
                tint = HalqaColors.Text,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = HalqaColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SignInRequired(navController: NavController, title: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .padding(16.dp),
    ) {
        ProfileHeader(title = title, onBack = { navController.popBackStack() })
        Spacer(Modifier.height(40.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔒", fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "تحتاج إلى تسجيل الدخول",
                    color = HalqaColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "سجّل دخولك من شاشة الحساب لتتمكن من إدارة ملفك.",
                    color = HalqaColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

