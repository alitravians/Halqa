package com.halqa.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lina (Growth) — durable client-side flags for one-shot onboarding /
 * conversion surfaces. Each entry is a boolean "have we done this on
 * THIS install yet?" gate. Once set, it survives backgrounding, low-
 * memory kills, and config changes; only an app uninstall / data clear
 * resets it.
 *
 * Why this exists separately from [AuthPrefs] and [SettingsPrefs]:
 *   - These flags are not "settings" the user can toggle (they're
 *     telemetry/onboarding state) and shouldn't be presented in the
 *     Settings screen.
 *   - They're not auth state either (an account sign-out shouldn't
 *     reset them; signing back in on the same install should NOT
 *     show the founder banner a second time).
 *   - Keeping the SharedPreferences file isolated means migrations on
 *     either of the other two prefs files can't accidentally wipe
 *     the founder flag.
 *
 * Each key is namespaced with the version-band it was introduced in
 * so a future bulk-reset (e.g. "reset all v0.1.x onboarding so we can
 * re-run it for the v0.2 founder cohort") is a single targeted erase
 * instead of a destructive clear-all.
 */
object OnboardingPrefs {
    private const val FILE = "halqa_onboarding_prefs"

    /** Has the v0.1 founder banner been dismissed at least once? */
    private const val KEY_FOUNDER_BANNER_SHOWN = "v0_1__founder_banner_shown"

    /** Has the POST_NOTIFICATIONS runtime prompt been shown yet (Android 13+)? */
    private const val KEY_NOTIFICATIONS_ASKED = "v0_1__notifications_asked"

    /** Did we already fire the `first_stream_watched` Analytics event from this install? */
    private const val KEY_FIRST_STREAM_FIRED = "v0_1__first_stream_watched_fired"

    /** Did we already fire the `first_gift_received` Analytics event for the broadcaster? */
    private const val KEY_FIRST_GIFT_RECEIVED_FIRED = "v0_1__first_gift_received_fired"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("OnboardingPrefs.init(context) must be called before use")

    fun isFounderBannerShown(): Boolean =
        requirePrefs().getBoolean(KEY_FOUNDER_BANNER_SHOWN, false)

    fun markFounderBannerShown() {
        requirePrefs().edit().putBoolean(KEY_FOUNDER_BANNER_SHOWN, true).apply()
    }

    fun wasNotificationsAsked(): Boolean =
        requirePrefs().getBoolean(KEY_NOTIFICATIONS_ASKED, false)

    fun markNotificationsAsked() {
        requirePrefs().edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
    }

    fun wasFirstStreamFired(): Boolean =
        requirePrefs().getBoolean(KEY_FIRST_STREAM_FIRED, false)

    fun markFirstStreamFired() {
        requirePrefs().edit().putBoolean(KEY_FIRST_STREAM_FIRED, true).apply()
    }

    fun wasFirstGiftReceivedFired(): Boolean =
        requirePrefs().getBoolean(KEY_FIRST_GIFT_RECEIVED_FIRED, false)

    fun markFirstGiftReceivedFired() {
        requirePrefs().edit().putBoolean(KEY_FIRST_GIFT_RECEIVED_FIRED, true).apply()
    }
}
