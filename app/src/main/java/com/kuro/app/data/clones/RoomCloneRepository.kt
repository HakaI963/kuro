package com.kuro.app.data.clones

import android.content.Context
import com.kuro.app.data.local.KuroDatabase
import com.kuro.app.data.local.toProfile
import com.kuro.app.data.local.toRecord
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.repositories.CloneRepository
import com.kuro.engine.VirtualEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomCloneRepository(
    private val db: KuroDatabase,
    private val context: Context,
    private val engineName: String,
    private val engine: VirtualEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : CloneRepository {

    private val _clones = MutableStateFlow<List<CloneProfile>>(emptyList())
    override val clones: StateFlow<List<CloneProfile>> = _clones.asStateFlow()

    init {
        scope.launch {
            db.cloneDao().observeAll().collect { records ->
                val pm = context.packageManager
                val confirmed = try {
                    engine.listSpacePackages()
                        .map { it.userId to it.packageName }
                        .toSet()
                } catch (_: Exception) {
                    emptySet()
                }
                _clones.value = records.map { r ->
                    val label = try {
                        val info = pm.getApplicationInfo(r.packageName, 0)
                        pm.getApplicationLabel(info)?.toString() ?: r.cloneName
                    } catch (_: Exception) {
                        r.cloneName
                    }
                    r.toProfile(label, engineConfirmed = (r.engineUserId to r.packageName) in confirmed)
                }
            }
        }
    }

    override suspend fun getClone(id: String): CloneProfile? = withContext(Dispatchers.IO) {
        _clones.value.firstOrNull { it.id == id }
            ?: db.cloneDao().get(id)?.let { rec ->
                val pm = context.packageManager
                val label = try {
                    val info = pm.getApplicationInfo(rec.packageName, 0)
                    pm.getApplicationLabel(info)?.toString() ?: rec.cloneName
                } catch (_: Exception) {
                    rec.cloneName
                }
                val confirmed = try {
                    engine.listSpacePackages()
                        .any { it.userId == rec.engineUserId && it.packageName == rec.packageName }
                } catch (_: Exception) {
                    false
                }
                rec.toProfile(label, confirmed)
            }
    }

    override suspend fun addClone(clone: CloneProfile) = withContext(Dispatchers.IO) {
        db.cloneDao().upsert(clone.toRecord(engineName))
    }

    override suspend fun updateClone(clone: CloneProfile) = withContext(Dispatchers.IO) {
        db.cloneDao().upsert(clone.toRecord(engineName))
    }

    override suspend fun deleteClone(id: String) = withContext(Dispatchers.IO) {
        db.cloneDao().delete(id)
    }

    suspend fun markLaunched(id: String, timestamp: Long) = withContext(Dispatchers.IO) {
        db.cloneDao().setLastLaunched(id, timestamp)
    }
}
