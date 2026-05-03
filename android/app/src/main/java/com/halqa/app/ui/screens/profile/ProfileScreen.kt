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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.halqa.app.data.AuthRepository
import com.halqa.app.data.FirebaseAuthRepository
import com.halqa.app.data.UserRepository
import com.halqa.app.ui.components.GhostButton
import com.halqa.app.ui.components.GoldButton
import com.halqa.app.ui.components.avatarInitial
import com.halqa.app.ui.navigation.Routes
import com.halqa.app.ui.theme.HalqaColors
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(navController: NavController) {
    val firebaseUser by FirebaseAuthRepository.authStateFlow().collectAsState(initial = FirebaseAuthRepository.currentUser)
    val uid = firebaseUser?.uid
    val liveProfile = if (uid != null) {
        UserRepository.observeProfile(uid).collectAsStateWithLifecycle(initialValue = null).value
    } else {
        null
    }
    val scope = rememberCoroutineScope()

    val displayName = liveProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: firebaseUser?.displayName
        ?: firebaseUser?.email?.substringBefore('@')
        ?: "زائر"
    val handle = liveProfile?.handle?.takeIf { it.isNotBlank() }
        ?.let { "@$it" }
        ?: firebaseUser?.email?.let { "@${it.substringBefore('@')}" }
        ?: ""
    val bio = liveProfile?.bio?.takeIf { it.isNotBlank() }
        ?: "أكمل ملفك من \"تعديل الملف\" لتظهر نبذتك هنا."

    // Stats (followers, following, level, streamsHosted) and badges
    // were previously rendered using MockData.currentUser placeholder
    // values. There is no backend service that tracks any of these
    // metrics — followers/following = 0, level = 1, streamsHosted = 0,
    // badges = emptyList() for every user. The Achievement section
    // rendered 4 hardcoded badges (نجم صاعد, محارب PK, صوت ذهبي,
    // عاشق الهدايا) unconditionally for every user. All of this was
    // the same class of UX-lying dead UI removed in PR #65 (dead
    // category chip), PR #69 (fake topup button), PR #71 (dead
    // Search/Notifications icons). Removed so the profile screen is
    // honest. When backend services for followers, levels, and badges
    // ship, restore via Firestore fields on users/{uid} + a real
    // UserRepository.observeProfile field + UI consumers here.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HalqaColors.Bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF1F1144), Color(0xFF0A0A1A)))),
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-48).dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(HalqaColors.Brand, HalqaColors.Pink)))
                        .border(3.dp, HalqaColors.Bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(avatarInitial(displayName), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.padding(top = 40.dp)) {
                    Text(displayName, color = HalqaColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    if (handle.isNotBlank()) {
                        Text(handle, color = HalqaColors.TextMuted, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                bio,
                color = HalqaColors.Text,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = "تعديل الملف",
                    onClick = { navController.navigate(Routes.EditProfile) },
                    fillMaxWidth = false,
                    modifier = Modifier.weight(1f),
                )
                GoldButton(
                    text = "💎 المحفظة",
                    onClick = { navController.navigate(Routes.Wallet) },
                    fillMaxWidth = false,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("الإعدادات")
            MenuRow(Icons.Filled.AccountBalanceWallet, "محفظة الكوينز والأرباح") { navController.navigate(Routes.Wallet) }
            MenuRow(Icons.Filled.History, "سجل البث والمعارك") { navController.navigate(Routes.StreamHistory) }
            MenuRow(Icons.Filled.Star, "تطوير الـ Avatar") { navController.navigate(Routes.PkArena) }
            MenuRow(Icons.Filled.Shield, "التحقق من الهوية (KYC)") { navController.navigate(Routes.Kyc) }
            MenuRow(Icons.Filled.SettingsSuggest, "الإعدادات العامة") { navController.navigate(Routes.Settings) }

            Spacer(Modifier.height(20.dp))
            SectionTitle("القانوني")
            MenuRow(Icons.Filled.Lock, "شروط الاستخدام") { navController.navigate(Routes.Terms) }
            MenuRow(Icons.Filled.Lock, "سياسة الخصوصية") { navController.navigate(Routes.Privacy) }
            MenuRow(Icons.Filled.Lock, "إرشادات المجتمع") { navController.navigate(Routes.Community) }

            Spacer(Modifier.height(20.dp))
            MenuRow(Icons.Filled.Logout, "تسجيل الخروج", danger = true) {
                scope.launch {
                    AuthRepository.signOut()
                    navController.navigate(Routes.Auth) {
                        popUpTo(Routes.Main) { inclusive = true }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        color = HalqaColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Color(0xFFEF4444) else HalqaColors.TextMuted)
        Spacer(Modifier.size(12.dp))
        Text(
            label,
            color = if (danger) Color(0xFFEF4444) else HalqaColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text("›", color = HalqaColors.TextDim, fontSize = 18.sp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(HalqaColors.Border),
    )
}
