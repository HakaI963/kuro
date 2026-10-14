package com.kuro.app.sessions

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Step 4b manifest hygiene for Step 5: the session foreground service shell.
 * The notification channel is created here in onCreate, so when Step 5 starts
 * real container sessions there can never be a startForeground call without a
 * channel. Nothing starts this service in Step 4 — no sessions exist yet, and
 * no startForeground call exists anywhere in the app (verified by grep).
 */
class SessionService : Service() {

    companion object {
        const val CHANNEL_ID = "kuro_sessions"
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Kuro sessions",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Step 5 wires real session lifetime here. Until then: do nothing and
        // stop, so the service can never linger without a session behind it.
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
