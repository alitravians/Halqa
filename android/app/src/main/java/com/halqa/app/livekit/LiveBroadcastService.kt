package com.halqa.app.livekit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.halqa.app.MainActivity
import com.halqa.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the publisher [BroadcastSession] alive
 * while the user is broadcasting, even if the app is backgrounded.
 *
 * The Service does NOT own the LiveKit Room — that lives in the
 * [BroadcastSession] singleton which is observable from any screen.
 * The Service exists purely to:
 *   1) Register with `startForeground(...)` so Android does not kill the
 *      mic / camera capture pipeline.
 *   2) Keep a persistent notification visible while live (UX + policy).
 *   3) Auto-`stopSelf()` when the session goes back to Idle.
 */
class LiveBroadcastService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            BroadcastSession.stop()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithNotification(initialNotification())
        observeSession()
        return START_STICKY
    }

    private fun observeSession() {
        if (stateJob != null) return
        stateJob = scope.launch {
            BroadcastSession.state.collectLatest { s ->
                when (s) {
                    is BroadcastState.Idle -> {
                        stopForegroundCompat()
                        stopSelf()
                    }
                    is BroadcastState.Failed -> {
                        stopForegroundCompat()
                        stopSelf()
                    }
                    is BroadcastState.Live -> {
                        notify(buildNotification("بث مباشر • ${s.viewerCount} مشاهد"))
                    }
                    is BroadcastState.Connecting -> {
                        notify(buildNotification("جارٍ الاتصال بالبث…"))
                    }
                    is BroadcastState.Stopping -> {
                        notify(buildNotification("جاري إنهاء البث…"))
                    }
                }
            }
        }
    }

    private fun startForegroundWithNotification(n: Notification) {
        // FOREGROUND_SERVICE_TYPE_CAMERA / FOREGROUND_SERVICE_TYPE_MICROPHONE were
        // introduced in API 30 (R). Calling startForeground(int, Notification, int)
        // exists from API 29 (Q) but with these specific constants only valid on
        // R+; gate strictly on R to avoid undefined behavior on Android 10 devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun notify(n: Notification) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, n)
    }

    private fun initialNotification() = buildNotification("جارٍ تجهيز البث…")

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopPi = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveBroadcastService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Halqa — بث مباشر")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPi)
            .addAction(0, "إنهاء البث", stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        stateJob?.cancel()
        stateJob = null
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 8101
        const val CHANNEL_ID = "halqa_live_broadcast"
        const val ACTION_STOP = "com.halqa.app.action.STOP_BROADCAST"

        fun start(context: Context) {
            val i = Intent(context, LiveBroadcastService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LiveBroadcastService::class.java).apply { action = ACTION_STOP }
            )
        }

        private fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val ch = NotificationChannel(
                        CHANNEL_ID,
                        "البث المباشر",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "إشعار يبقى ظاهراً طوال فترة البث المباشر."
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(ch)
                }
            }
        }
    }
}
