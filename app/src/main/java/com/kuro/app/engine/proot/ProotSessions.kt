package com.kuro.app.engine.proot

import android.content.Context
import com.kuro.app.core.system.StoragePaths
import com.kuro.app.data.containers.ContainerStatus
import com.kuro.app.data.containers.ContainerStore
import com.kuro.app.data.local.SessionRecord
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Step 5: live terminal sessions.
 * - In-memory registry sessionId -> TerminalSession (fds/pids live here).
 * - Persisted metadata (Room SessionRecord): id, containerId, pid, startedAt.
 * - Back navigation detaches the View (see KuroTerminalView) WITHOUT killing:
 *   reopening ATTACHES the same live session fd.
 * - Multiple simultaneous containers/sessions: independent processes, one
 *   TerminalSession each. Stop = SIGKILL the process group; service + store
 *   updated; Room marked ENDED.
 */
data class LiveSession(
    val sessionId: String,
    val containerId: String,
    val terminalSession: TerminalSession,
    val pid: Int,
    val startedAt: Long
)

object ProotSessions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _live = MutableStateFlow<Map<String, LiveSession>>(emptyMap())
    val live: StateFlow<Map<String, LiveSession>> = _live.asStateFlow()

    private val _lastExit = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Last recorded exit status per session (for the "session ended" note). */
    val lastExit: StateFlow<Map<String, Int>> = _lastExit.asStateFlow()

    fun liveForContainer(containerId: String): LiveSession? =
        _live.value.values.firstOrNull { it.containerId == containerId }

    fun isRunning(containerId: String): Boolean =
        liveForContainer(containerId)?.terminalSession?.isRunning == true

    /**
     * Create (not yet spawned) a TerminalSession for [containerId]. Spawning
     * happens when the first TerminalView attaches (initializeEmulator).
     * Must be called on the main thread (TerminalSession owns a Handler).
     */
    fun create(
        appContext: Context,
        containerId: String,
        plan: ProotLauncher.LaunchPlan,
        sessionId: String = "sess-" + UUID.randomUUID().toString().take(8),
        db: com.kuro.app.data.local.KuroDatabase,
        onPid: (Int) -> Unit = {}
    ): LiveSession {
        existing(sessionId)?.let { return it }
        val startedAt = System.currentTimeMillis()
        val filesDir = appContext.filesDir
        val client = SessionEvents(sessionId, containerId, filesDir, appContext, db, onPid)
        val session = TerminalSession(
            plan.shellPath,
            plan.cwd,
            plan.args.toTypedArray(),
            plan.env.toTypedArray(),
            10_000,
            client
        )
        session.mSessionName = containerId
        val live = LiveSession(sessionId, containerId, session, pid = -1, startedAt = startedAt)
        _live.update { it + (sessionId to live) }
        ContainerStore.setStatus(containerId, ContainerStatus.RUNNING)
        scope.launch(Dispatchers.IO) {
            runCatching {
                com.kuro.app.data.containers.SessionRepository(db).save(
                    SessionRecord(sessionId, containerId, -1, startedAt, "RUNNING")
                )
            }
        }
        ContainerEngineLog.append(
            appContext.filesDir, "session",
            "$sessionId created for $containerId (${plan.shellLabel})"
        )
        return live
    }

    fun existing(sessionId: String): LiveSession? = _live.value[sessionId]

    fun drop(sessionId: String) {
        _live.update { it - sessionId }
    }

    /** Stop = SIGKILL the process group, mark ENDED, update store + service. */
    fun stop(appContext: Context, db: com.kuro.app.data.local.KuroDatabase, sessionId: String) {
        val live = existing(sessionId) ?: return
        runCatching {
            val pid = live.pid.takeIf { it > 0 }
                ?: live.terminalSession.getPid().takeIf { it > 0 }
            if (pid != null) {
                // Child did setsid(): negative pid kills the whole group.
                runCatching { android.system.Os.kill(-pid, android.system.OsConstants.SIGKILL) }
                    .onFailure { runCatching { live.terminalSession.finishIfRunning() } }
            } else {
                runCatching { live.terminalSession.finishIfRunning() }
            }
        }
        finish(appContext, db, sessionId, null)
    }

    fun stopAll(appContext: Context, db: com.kuro.app.data.local.KuroDatabase) {
        _live.value.keys.toList().forEach { stop(appContext, db, it) }
        ContainerStore.stopAll()
    }

    internal fun onPid(
        appContext: Context,
        db: com.kuro.app.data.local.KuroDatabase,
        sessionId: String,
        pid: Int,
        onPid: (Int) -> Unit
    ) {
        _live.update { map ->
            map[sessionId]?.let { map + (sessionId to it.copy(pid = pid)) } ?: map
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                val repo = com.kuro.app.data.containers.SessionRepository(db)
                val rec = SessionRecord(sessionId, "", pid, 0L, "RUNNING")
                // Preserve containerId/startedAt if we already saved them.
                val live = existing(sessionId)
                repo.save(
                    rec.copy(
                        containerId = live?.containerId ?: "",
                        startedAt = live?.startedAt ?: System.currentTimeMillis()
                    )
                )
            }
        }
        ContainerEngineLog.append(appContext.filesDir, "session", "$sessionId spawned pid=$pid")
        com.kuro.app.sessions.SessionService.refreshNotifications(appContext)
        onPid(pid)
    }

    internal fun finish(
        appContext: Context,
        db: com.kuro.app.data.local.KuroDatabase,
        sessionId: String,
        exitStatus: Int?
    ) {
        val live = existing(sessionId)
        drop(sessionId)
        if (exitStatus != null) {
            _lastExit.update { it + (sessionId to exitStatus) }
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                com.kuro.app.data.containers.SessionRepository(db).setStatus(sessionId, "ENDED")
            }
        }
        // Container shows STOPPED when none of its sessions remain live.
        if (live != null && liveForContainer(live.containerId) == null) {
            ContainerStore.setStatus(live.containerId, ContainerStatus.STOPPED)
        }
        ContainerEngineLog.append(
            appContext.filesDir, "session",
            "$sessionId ended${if (exitStatus != null) " exit=$exitStatus" else ""}"
        )
        com.kuro.app.sessions.SessionService.refreshNotifications(appContext)
    }

    fun containerDir(appFilesDir: File, containerId: String): File =
        StoragePaths.prootContainerDir(appFilesDir, containerId)

    private class SessionEvents(
        val sessionId: String,
        val containerId: String,
        val filesDir: File,
        val appContext: Context,
        val db: com.kuro.app.data.local.KuroDatabase,
        val onPid: (Int) -> Unit
    ) : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) = Unit
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) {
            ProotSessions.finish(appContext, db, sessionId, finishedSession.exitStatus)
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
        override fun onPasteTextFromClipboard(session: TerminalSession) = Unit
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
            ProotSessions.onPid(appContext, db, sessionId, pid, onPid)
        }

        override fun logError(tag: String, message: String) {
            ContainerEngineLog.append(filesDir, "termux", "E/$tag: $message")
        }

        override fun logWarn(tag: String, message: String) {
            ContainerEngineLog.append(filesDir, "termux", "W/$tag: $message")
        }

        override fun logInfo(tag: String, message: String) = Unit
        override fun logDebug(tag: String, message: String) = Unit
        override fun logVerbose(tag: String, message: String) = Unit
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            ContainerEngineLog.append(filesDir, "termux", "E/$tag: $message ${e.message}")
        }

        override fun logStackTrace(tag: String, e: Exception) {
            ContainerEngineLog.append(filesDir, "termux", "E/$tag: ${e.message}")
        }
    }
}
