package com.halqa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.halqa.app.data.FirebaseAuthRepository
import com.halqa.app.data.SettingsPrefs
import com.halqa.app.data.UserRepository
import com.halqa.app.ui.HalqaApp
import com.halqa.app.ui.theme.HalqaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        super.onCreate(savedInstanceState)
        setContent {
            // Live language. We seed from SharedPreferences (so cold-start
            // doesn't flicker between locales), then mirror the Firestore
            // settings doc into prefs whenever it changes — so toggling
            // the language on one device reflows the layout on every other
            // device immediately.
            val language by SettingsPrefs.languageFlow()
                .collectAsState(initial = SettingsPrefs.getLanguage())
            val firebaseUser by FirebaseAuthRepository.authStateFlow()
                .collectAsState(initial = FirebaseAuthRepository.currentUser)

            LaunchedEffect(firebaseUser?.uid) {
                val uid = firebaseUser?.uid ?: return@LaunchedEffect
                UserRepository.observeSettings(uid)
                    .distinctUntilChanged()
                    .collect { settings ->
                        if (settings.language.isNotBlank() &&
                            settings.language != SettingsPrefs.getLanguage()
                        ) {
                            SettingsPrefs.setLanguage(settings.language)
                        }
                    }
            }

            val layoutDirection = if (language.equals("en", ignoreCase = true)) {
                LayoutDirection.Ltr
            } else {
                LayoutDirection.Rtl
            }

            HalqaTheme {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Surface(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A1A)),
                        color = Color(0xFF0A0A1A),
                    ) {
                        HalqaApp()
                    }
                }
            }
        }
    }
}
