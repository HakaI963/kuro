package com.kuro.app.data.clones

import com.kuro.app.data.local.KuroDatabase
import com.kuro.app.data.local.toEvent
import com.kuro.app.data.local.toRecord
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.repositories.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoomActivityRepository(
    private val db: KuroDatabase,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ActivityRepository {

    private val _events = MutableStateFlow<List<ActivityEvent>>(emptyList())
    override val events: StateFlow<List<ActivityEvent>> = _events.asStateFlow()

    init {
        scope.launch {
            db.activityDao().observeAll().collect { records ->
                _events.value = records.map { it.toEvent() }
            }
        }
    }

    override suspend fun addEvent(event: ActivityEvent) = withContext(Dispatchers.IO) {
        db.activityDao().insert(event.toRecord())
    }
}
