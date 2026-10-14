package com.kuro.app.sessions

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kuro.app.MainActivity
import com.kuro.app.R
import com.kuro.app.core.utils.formatBytes
import com.kuro.app.data.containers.ContainerStore
import com.kuro.app.engine.proot.ContainerEngineLog
import com.kuro.app.engine.proot.ContainerProvisioner
import com.kuro.app.engine.proot.InstallStage
import com.kuro.app.engine.proot.ProotSessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Step 5: the session foreground service, now real.
 * - Holds sessions + installs alive in background (and screen-off, together
 *   with per-container wake locks).
 * - One summary notification lists running sessions with Stop actions;
 *   one progress notification per active download (bar + Cancel).
 * - Channel is created in onCreate, so no startForeground call can ever run
 *   without a channel. startForeground uses SPECIAL_USE on API 29+.
 */
class SessionService : Service() {

    companion object {
        const val CHANNEL_ID = "kuro_sessions"
        private const val SUMMARY_ID = 1001

        const val ACTION_STOP_SESSION = "com.kuro.app.sessions.STOP_SESSION"
        const val ACTION_STOP_ALL = "com.kuro.app.sessions.STOP_ALL"
        const val ACTION_CANCEL_INSTALL = "com.kuro.app.sessions.CANCEL_INSTALL"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_INSTALL_ID = "install_id"

        /** Start (or keep) the service while an install runs. */
        fun ensureProvisioning(context: Context, installId: String) {
            val intent = Intent(context.applicationContext, SessionService::class.java)
                .setAction(ACTION_CANCEL_INSTALL)
                .putExtra(EXTRA_INSTALL_ID, installId)
                // Action is only read for explicit commands; provisioning is
                // signalled by the extra + the service reads live state.
                .putExtra("provision", true)
            ContextCompat.startForegroundService(context.applicationContext, intent)
            refreshNotifications(context.applicationContext)
        }

        /** Start (or keep) the service while sessions run. */
        fun ensureSessions(context: Context) {
            val intent = Intent(context.applicationContext, SessionService::class.java)
            ContextCompat.startForegroundService(context.applicationContext, intent)
            refreshNotifications(context.applicationContext)
        }

        /** Rebuild notifications from live state; stops the service when idle. */
        fun refreshNotifications(context: Context) {
            val intent = Intent(context.applicationContext, SessionService::class.java)
                .setAction("com.kuro.app.sessions.REFRESH")
            // Plain start (not foreground) is enough: if the service is
            // running it refreshes; if not, nothing needs showing.
            runCatching {
                context.applicationContext.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundActive = false

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
        serviceScope.launch {
            combine(ProotSessions.live, ContainerProvisioner.stages) { live, _ -> live }
                .collect { rebuild() }
        }
        serviceScope.launch {
            ContainerProvisioner.stages.collect { rebuild() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A service kicked via startForegroundService MUST call startForeground
        // before stopping, or the system kills it — so foreground first with
        // the current summary, then handle the action (rebuild may stop us).
        startForegroundCompat(SUMMARY_ID, buildSummaryNotification())
        foregroundActive = true
        when (intent?.action) {
            ACTION_STOP_SESSION -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                serviceScope.launch(Dispatchers.IO) {
                    ProotSessions.stop(
                        applicationContext,
                        com.kuro.app.di.ServiceLocator.database,
                        id
                    )
                    rebuild()
                }
                return START_NOT_STICKY
            }
            ACTION_STOP_ALL -> {
                serviceScope.launch(Dispatchers.IO) {
                    ProotSessions.stopAll(
                        applicationContext,
                        com.kuro.app.di.ServiceLocator.database
                    )
                    rebuild()
                }
                return START_NOT_STICKY
            }
            ACTION_CANCEL_INSTALL -> {
                // Explicit cancel only when this is a real user/refresh action,
                // not the provisioning kick (which carries "provision" extra).
                if (intent.getStringExtra(EXTRA_INSTALL_ID) != null &&
                    !intent.getBooleanExtra("provision", false)
                ) {
                    ContainerProvisioner.cancel(intent.getStringExtra(EXTRA_INSTALL_ID)!!)
                }
                rebuild()
                return START_STICKY
            }
            else -> {
                rebuild()
                return START_STICKY
            }
        }
    }

    private fun rebuild() {
        val live = ProotSessions.live.value
        val activeInstalls = ContainerProvisioner.activeInstallIds()
        updateWakeLock(live.isNotEmpty())
        if (live.isEmpty() && activeInstalls.isEmpty()) {
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            foregroundActive = false
            cancelProgressNotifications(activeInstalls)
            stopSelf()
            return
        }
        // Summary notification (sessions) keeps us foreground.
        val summary = buildSummaryNotification()
        startForegroundCompat(SUMMARY_ID, summary)
        foregroundActive = true
        // One progress notification per active install.
        activeInstalls.forEach { id ->
            val stage = ContainerProvisioner.stageOf(id)
            if (stage is InstallStage.Downloading) {
                notifyProgress(id, stage)
            } else {
                cancelProgress(id)
            }
        }
    }

    private fun buildSummaryNotification(): Notification {
        val live = ProotSessions.live.value.values.toList()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val names = live.mapNotNull { s ->
            ContainerStore.containers.value.firstOrNull { it.id == s.containerId }?.name
        }
        val text = if (names.isEmpty()) "No active sessions"
        else names.joinToString(", ")
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.splash_icon_solid)
            .setContentTitle("Kuro sessions (${live.size} running)")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        live.take(3).forEachIndexed { index, s ->
            val stop = PendingIntent.getService(
                this, 3000 + index,
                Intent(this, SessionService::class.java)
                    .setAction(ACTION_STOP_SESSION)
                    .putExtra(EXTRA_SESSION_ID, s.sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val label = ContainerStore.containers.value
                .firstOrNull { it.id == s.containerId }?.name ?: "session"
            builder.addAction(0, "Stop $label", stop)
        }
        val stopAll = PendingIntent.getService(
            this, 3999,
            Intent(this, SessionService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Stop all", stopAll)
        return builder.build()
    }

    private fun notifyProgress(installId: String, stage: InstallStage.Downloading) {
        val cancel = PendingIntent.getService(
            this, 4000 + (installId.hashCode() and 0xFFF),
            Intent(this, SessionService::class.java)
                .setAction(ACTION_CANCEL_INSTALL)
                .putExtra(EXTRA_INSTALL_ID, installId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val total = stage.totalBytes ?: -1
        val pct = if (total > 0) (stage.bytes * 100 / total).toInt().coerceIn(0, 100) else -1
        val speed = if (stage.bytesPerSec > 0) "${formatBytes(stage.bytesPerSec)}/s" else "…"
        val eta = stage.etaSec?.let { "${it}s left" } ?: ""
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.splash_icon_solid)
            .setContentTitle("Downloading distro (${formatBytes(stage.bytes)}${if (total > 0) " / ${formatBytes(total)}" else ""})")
            .setContentText("$speed $eta".trim())
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", cancel)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(progressId(installId), builder.build())
    }

    private fun progressId(installId: String): Int = 5000 + (installId.hashCode() and 0xFFF)

    private fun cancelProgress(installId: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(progressId(installId))
        }
    }

    private fun cancelProgressNotifications(active: List<String>) {
        // Drop progress notifications whose install is no longer active.
        // (Active ones are rebuilt right after; finished ones are cancelled
        // by their own stage handling.)
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            manager.activeNotifications.forEach { sbn ->
                if (sbn.id != SUMMARY_ID && sbn.id >= 5000) manager.cancel(sbn.id)
            }
        }
        active.forEach { cancelProgress(it) }
    }

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                id, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(id, notification)
        }
    }

    private fun updateWakeLock(anyRunning: Boolean) {
        val needsLock = anyRunning && ContainerStore.containers.value.any { c ->
            c.wakeLock && ProotSessions.isRunning(c.id)
        }
        if (needsLock && wakeLock?.isHeld != true) {
            runCatching {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kuro:session")?.also {
                    it.acquire()
                }
            }
        } else if (!needsLock && wakeLock?.isHeld == true) {
            runCatching { wakeLock?.release() }
            wakeLock = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
