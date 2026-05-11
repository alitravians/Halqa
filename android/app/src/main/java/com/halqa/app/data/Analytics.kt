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

    /**
     * Lina — conversion funnel entry. Fired exactly once per install,
     * the moment a [UserDocBootstrap.Result.Created] result lands on
     * any sign-in path. `method` is one of "phone", "google", "email".
     *
     * The event name `sign_up` is a Firebase Analytics standard event
     * (FirebaseAnalytics.Event.SIGN_UP) so it surfaces on the default
     * dashboard funnel and is automatically forwarded to BigQuery if
     * the project ever turns on the export.
     */
    fun signUp(method: String) {
        firebase.logEvent(
            FirebaseAnalytics.Event.SIGN_UP,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.METHOD, method)
                putLong("ts", System.currentTimeMillis())
            },
        )
    }

    /**
     * Lina — conversion funnel return. Fired on every successful sign-in
     * (including return sign-ins where the user doc already exists, i.e.
     * the [UserDocBootstrap.Result.Patched] / [UserDocBootstrap.Result.Skipped]
     * branches). Returns the same `method` taxonomy as [signUp] so the
     * D7 / D30 retention funnel partitions cleanly.
     */
    fun login(method: String) {
        firebase.logEvent(
            FirebaseAnalytics.Event.LOGIN,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.METHOD, method)
                putLong("ts", System.currentTimeMillis())
            },
        )
    }

    /**
     * Lina — monetisation funnel. Fired immediately after a successful
     * `POST /api/wallet/topup` (paid pack today, IAP later). Uses the
     * Firebase standard `in_app_purchase` event so the LTV / ARPU
     * dashboards work out of the box.
     *
     * `priceLabel` is a free-form string from the backend pack DTO
     * (e.g. "‏€4.99", "45 ر.س.") — currency normalisation lives
     * server-side, the client only forwards what it received.
     */
    fun topupCompleted(packId: String, coins: Long, priceLabel: String) {
        firebase.logEvent(
            "in_app_purchase",
            Bundle().apply {
                putString("pack_id", packId)
                putLong("coins", coins)
                putString("price_label", priceLabel)
                putLong("ts", System.currentTimeMillis())
            },
        )
    }

    /**
     * Lina — monetisation funnel inverse. Fired the moment a viewer
     * taps `إتمام السحب` in [WithdrawSheet] and the backend returns
     * 200 (or the v0.1.23 503 stub — it still represents user intent).
     * Critical for spotting cash-out drift before it hits the Q3 P&L
     * report.
     */
    fun withdrawalInitiated(amountDiamonds: Long) {
        firebase.logEvent(
            "withdrawal_initiated",
            Bundle().apply {
                putLong("amount_diamonds", amountDiamonds)
                putLong("ts", System.currentTimeMillis())
            },
        )
    }

    /**
     * Lina — activation funnel. Fired exactly ONCE per install the
     * first time a viewer reaches the `LiveWatchScreen` and the watch
     * session transitions into `WatchState.Watching`. The flag is
     * persisted via [OnboardingPrefs] so a re-open of the same stream
     * (or any future stream) does NOT re-fire.
     */
    fun firstStreamWatched(streamId: String) {
        firebase.logEvent(
            "first_stream_watched",
            Bundle().apply {
                putString("stream_id", streamId)
                putLong("ts", System.currentTimeMillis())
            },
        )
    }

    /**
     * Lina — activation funnel inverse for the broadcaster side.
     * Fired exactly ONCE per install the first time the broadcaster's
     * own stream snapshot transitions `giftTotal` from 0 to >0 (i.e.
     * the first gift they ever receive). Persisted via [OnboardingPrefs].
     */
    fun firstGiftReceived(streamId: String) {
        firebase.logEvent(
            "first_gift_received",
            Bundle().apply {
                putString("stream_id", streamId)
                putLong("ts", System.currentTimeMillis())
            },
        )
    }

    /**
     * Crashlytics + Firebase Analytics user tagging. Called by every
     * sign-in path (`PhoneAuthRepository`, `GoogleAuthRepository`,
     * `AuthRepository.signInWithEmailInternal`) immediately after
     * `UserDocBootstrap.ensureUserDoc` resolves, so every subsequent
     * Analytics event and Crashlytics report is joinable back to
     * `/users/{uid}` without a separate ETL stage.
     */
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
