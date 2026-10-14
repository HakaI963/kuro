package com.kuro.app.ui.screens.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.core.system.StoragePaths
import com.kuro.app.core.system.SystemMetricsReader
import com.kuro.app.data.containers.ContainerRepository
import com.kuro.app.data.containers.ContainerStatus
import com.kuro.app.data.containers.ContainerStore
import com.kuro.app.data.containers.ContainerSync
import com.kuro.app.data.containers.LinuxContainer
import com.kuro.app.data.local.ContainerRecord
import com.kuro.app.di.ServiceLocator
import com.kuro.app.engine.proot.ContainerEngineLog
import com.kuro.app.engine.proot.ContainerProvisioner
import com.kuro.app.engine.proot.InstallFailure
import com.kuro.app.engine.proot.LiveSession
import com.kuro.app.engine.proot.ProotBinary
import com.kuro.app.engine.proot.ProotSessions
import com.kuro.app.engine.proot.SessionStarter
import com.kuro.app.sessions.SessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalViewModel : ViewModel() {
    private val db get() = ServiceLocator.database

    val containers: StateFlow<List<LinuxContainer>> = ContainerStore.containers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live pty sessions (fds/pids). Drives REAL card status. */
    val liveSessions: StateFlow<Map<String, LiveSession>> = ProotSessions.live

    /** Last exit status per finished session (ended-note UI). */
    val sessionExits: StateFlow<Map<String, Int>> = ProotSessions.lastExit

    private val _starting = MutableStateFlow<Set<String>>(emptySet())
    val starting: StateFlow<Set<String>> = _starting.asStateFlow()

    private val _startError = MutableStateFlow<Pair<String, InstallFailure>?>(null)
    val startError: StateFlow<Pair<String, InstallFailure>?> = _startError.asStateFlow()

    private val _deleted = MutableStateFlow<Pair<String, Long>?>(null)
    /** Last delete result: container name + freed bytes. */
    val deleted: StateFlow<Pair<String, Long>?> = _deleted.asStateFlow()

    init {
        // No-op: startup sync runs from ensureLoaded(context) so the VM never
        // needs an application context of its own.
    }

    /** One-time Room -> store sync + size refresh. Call from UI with context. */
    fun ensureLoaded(context: Context) {
        val dir = context.applicationContext.filesDir
        viewModelScope.launch {
            runCatching {
                ContainerSync.ensureLoaded(db, dir, DistroCatalog::displayNameOf)
            }
            refreshSizes(context)
        }
    }

    fun clearStartError() {
        _startError.value = null
    }

    fun clearDeleted() {
        _deleted.value = null
    }

    fun refreshSizes(context: Context) {
        val dir = context.applicationContext.filesDir
        viewModelScope.launch(Dispatchers.IO) {
            ContainerStore.containers.value.forEach { c ->
                val size = SystemMetricsReader.dirSizeNow(
                    StoragePaths.prootRootfsDir(dir, c.id)
                ) ?: return@forEach
                ContainerStore.updateSize(c.id, size)
            }
        }
    }

    fun startSession(context: Context, containerId: String) {
        val app = context.applicationContext
        if (containerId in _starting.value) return
        _starting.value = _starting.value + containerId
        _startError.value = null
        viewModelScope.launch {
            val outcome = SessionStarter.start(app, db, containerId)
            _starting.value = _starting.value - containerId
            when (outcome) {
                is SessionStarter.StartOutcome.Success -> Unit
                is SessionStarter.StartOutcome.Failure ->
                    _startError.value = containerId to outcome.failure
            }
        }
    }

    fun stop(id: String, context: Context) {
        val app = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            // Kill every live session of this container (process groups).
            ProotSessions.live.value.values
                .filter { it.containerId == id }
                .map { it.sessionId }
                .forEach { ProotSessions.stop(app, db, it) }
            ContainerStore.setStatus(id, ContainerStatus.STOPPED)
        }
    }

    fun delete(context: Context, id: String) {
        val app = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val target = ContainerStore.containers.value.firstOrNull { it.id == id }
            val before = target?.sizeBytes
                ?: SystemMetricsReader.dirSizeNow(StoragePaths.prootRootfsDir(app.filesDir, id))
                ?: 0L
            ProotSessions.live.value.values
                .filter { it.containerId == id }
                .map { it.sessionId }
                .forEach { ProotSessions.stop(app, db, it) }
            runCatching {
                StoragePaths.prootContainerDir(app.filesDir, id).deleteRecursively()
            }
            val after = SystemMetricsReader.dirSizeNow(
                StoragePaths.prootContainerDir(app.filesDir, id)
            ) ?: 0L
            runCatching { ContainerRepository(db).delete(id) }
            ContainerStore.remove(id)
            ContainerEngineLog.append(app.filesDir, "delete", "$id removed")
            SessionService.refreshNotifications(app)
            withContext(Dispatchers.Main) {
                _deleted.value = (target?.name ?: id) to (before - after).coerceAtLeast(0L)
            }
        }
    }

    fun stopAll(context: Context) {
        val app = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            ProotSessions.stopAll(app, db)
        }
    }

    suspend fun configOf(containerId: String): ContainerRecord? =
        withContext(Dispatchers.IO) {
            runCatching { db.containerDao().get(containerId) }.getOrNull()
        }

    /** Proot binary readiness for the session screen pre-check. */
    suspend fun ensureProotBinary(context: Context): Result<java.io.File> =
        ProotBinary.ensureInstalled(context)

    fun saveInstalled(record: ContainerRecord) {
        viewModelScope.launch {
            runCatching { ContainerRepository(db).save(record) }
        }
    }

    fun cancelInstall(installId: String) {
        ContainerProvisioner.cancel(installId)
    }
}
