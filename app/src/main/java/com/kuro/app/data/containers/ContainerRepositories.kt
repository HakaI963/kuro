package com.kuro.app.data.containers

import com.kuro.app.core.system.StoragePaths
import com.kuro.app.core.system.SystemMetricsReader
import com.kuro.app.data.local.ContainerRecord
import com.kuro.app.data.local.KuroDatabase
import com.kuro.app.data.local.SessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Step 5: Room-backed persistence for container configs and session metadata.
 * Live state (status, sizes, fds) stays in memory (ContainerStore/ProotSessions);
 * this repository layer is the durable side both are synced with.
 */
class ContainerRepository(private val db: KuroDatabase) {
    fun observeConfigs(): Flow<List<ContainerRecord>> = db.containerDao().observeAll()

    suspend fun save(record: ContainerRecord) = withContext(Dispatchers.IO) {
        db.containerDao().upsert(record)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.containerDao().delete(id)
        db.sessionDao().deleteForContainer(id)
    }
}

class SessionRepository(private val db: KuroDatabase) {
    fun observeAll(): Flow<List<SessionRecord>> = db.sessionDao().observeAll()

    suspend fun save(record: SessionRecord) = withContext(Dispatchers.IO) {
        db.sessionDao().upsert(record)
    }

    suspend fun setStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        db.sessionDao().setStatus(id, status)
    }

    suspend fun clearForContainer(containerId: String) = withContext(Dispatchers.IO) {
        db.sessionDao().deleteForContainer(containerId)
    }
}

/**
 * Step 5: one-time startup sync — Room configs become the in-memory list
 * (status STOPPED until a live session exists; sizes recomputed on IO).
 */
object ContainerSync {
    @Volatile
    private var synced = false

    suspend fun ensureLoaded(
        db: KuroDatabase,
        appFilesDir: File,
        distroNameOf: (String) -> String
    ) {
        if (synced) return
        synchronized(this) {
            if (synced) return
            synced = true
        }
        val loaded = runCatching {
            withContext(Dispatchers.IO) { db.containerDao().observeAll().first() }
        }.getOrNull() ?: emptyList()
        val ui = loaded.map { r ->
            val size = withContext(Dispatchers.IO) {
                SystemMetricsReader.dirSizeNow(StoragePaths.prootRootfsDir(appFilesDir, r.id))
            } ?: 0L
            LinuxContainer(
                id = r.id,
                name = r.name,
                distroId = r.distroId,
                distroName = distroNameOf(r.distroId),
                type = runCatching { ContainerType.valueOf(r.type) }.getOrDefault(ContainerType.PROOT),
                status = ContainerStatus.STOPPED,
                sizeBytes = size,
                withGui = r.interfaceMode != "TUI",
                bridgeEnabled = r.bridgeEnabled,
                interfaceMode = r.interfaceMode
            )
        }
        ContainerStore.replaceAll(ui)
    }
}
