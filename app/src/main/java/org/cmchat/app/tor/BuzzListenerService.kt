package org.cmchat.app.tor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * The "scout listener": a minimal foreground service that stays alive after the
 * full app is swiped away, solely so a BUZZ can still reach the phone and post
 * an "Activity" notification. It keeps nothing of the conversation — RAM chat
 * state is already cleared and [MessageService] is in buzz-only mode; Tor and
 * the onion service keep running underneath so a buzz can arrive.
 *
 * Android requires a visible notification for a foreground service, so it shows
 * one minimal, generic line. Force-stopping the app in Android Settings kills
 * even this listener, going fully dark.
 */
class BuzzListenerService : Service() {

    companion object {
        private const val CHANNEL_ID = "cm_listen"
        private const val NOTIF_ID = 7002

        fun start(context: Context) {
            // Started from onTaskRemoved (just-backgrounded); a background-start
            // rejection on API 12+ must not crash — we simply don't listen then.
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, BuzzListenerService::class.java)
                )
            } catch (e: Exception) {
                org.cmchat.app.diag.Diag.e("buzz", "listener FGS start refused", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BuzzListenerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        // startForeground is the first action; fall back untyped, else stopSelf.
        val notif = try { buildNotification() } catch (e: Exception) { stopSelf(); return }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID, notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else startForeground(NOTIF_ID, notif)
        } catch (e: Exception) {
            try { startForeground(NOTIF_ID, notif) } catch (e2: Exception) { stopSelf(); return }
        }
        org.cmchat.app.diag.Diag.i("buzz", "scout listener up")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        org.cmchat.app.diag.Diag.i("buzz", "scout listener down")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Listening", NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Listening")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }
}
