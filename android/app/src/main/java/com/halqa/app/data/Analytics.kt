package com.halqa.app.data

import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Lightweight wrapper around FirebaseAnalytics + Crashlytics.
 *
 * Why this file exists:
 *  - Lina Al-Saud (Growth & Ad) — closed-beta needs the `gift_sent`
 *    event with its full payload to compute Loop Closure Rate D1
 *    (the only KPI that matters in beta). Without instrumentation
 *    we ship blind. session: a03fa7dc00a740f884f8e65c7320acef.
 *  - Reem Al-Otaibi (Play Store Ops & Performance) — Crashlytics
 *    must be initialised early so any cold-start crash reports back
 *    to Firebase. session: 6e36fb767a5d48d585dce5685ddc77c8.
 *
 * Both SDKs auto-initialise via the Firebase ContentProvider mechanism
 * (no manual init needed — the plugins handle it). This file just
 * exposes the call-sites the repositories use.
 *
 * Call `Analytics.init(application)` from `HalqaApplication.onCreate()`
 * before any logging call.
 */
object Analytics {

    @Volatile private var app: Application? = null

    fun init(application: Application) {
        app = application
        // Touch instances so Firebase ContentProvider-installed SDKs warm up.
        FirebaseAnalytics.getInstance(application)
        FirebaseCrashlytics.getInstance()
    }

    private val firebase: FirebaseAnalytics
        get() = FirebaseAnalytics.getInstance(
            requireNotNull(app) { "Analytics.init() not called" },
        )

    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    /**
     * Lina's spec for Loop Closure D1 measurement.
     *   payload: { sender_id, receiver_id, gift_id, coins, is_first_gift, ts }
     * The warehouse self-joins receiver→sender within 24h to compute
     * the % of senders who got a gift back.
     */
    fun giftSent(
        senderUid: String,
        receiverUid: String,
        giftId: String,
        coins: Long,
        isFirstGift: Boolean,
    ) {
        firebase.logEvent(
            "gift_sent",
            Bundle().apply {
                putString("sender_id", senderUid)
                putString("receiver_id", receiverUid)
                putString("gift_id", giftId)
                putLong("coins", coins)
                putBoolean("is_first_gift", isFirstGift)
                putLong("ts", System.currentTimeMillis())
            },
        )
    }

    /** Crashlytics: tag the current user so reports are joinable to Firestore users/{uid}. */
    fun setUser(uid: String) {
        crashlytics.setUserId(uid)
        firebase.setUserId(uid)
    }

    /** Non-fatal log — useful for "we caught this but want to know about it". */
    fun logNonFatal(t: Throwable, tag: String? = null) {
        if (tag != null) crashlytics.log("[$tag] ${t.message}")
        crashlytics.recordException(t)
    }
}
