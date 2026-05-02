package com.halqa.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * SharedPreferences-backed cache for the locally-mirrored copy of the
 * user's settings. The authoritative source is Firestore
 * (`users/{uid}/settings/default`, see [UserRepository.observeSettings]),
 * but we cache a few keys here so we can apply them at cold-start
 * (`MainActivity.onCreate`) before Firebase Auth + Firestore have finished
 * resolving — Arabic users in particular expect the language toggle to
 * persist across app restarts and not flicker between Arabic and English
 * on every launch.
 *
 * Schema is key-per-field rather than a single JSON blob so additive
 * migrations don't break older installs.
 */
object SettingsPrefs {
    private const val FILE = "halqa_settings_prefs"
    private const val KEY_LANGUAGE = "language"

    /** Default language for the app. Saudi Arabian Arabic. */
    const val DEFAULT_LANGUAGE = "ar"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("SettingsPrefs.init(context) must be called before use")

    fun getLanguage(): String =
        requirePrefs().getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE

    fun setLanguage(language: String) {
        requirePrefs().edit().putString(KEY_LANGUAGE, language).apply()
    }

    /**
     * Reactive language flow. Emits the current value immediately, then
     * emits again every time the underlying preference changes (e.g. when
     * the Settings screen toggles the language and we mirror the change
     * back to local storage).
     */
    fun languageFlow(): Flow<String> = callbackFlow {
        val p = requirePrefs()
        trySend(p.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY_LANGUAGE) {
                trySend(sp.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE)
            }
        }
        p.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { p.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
