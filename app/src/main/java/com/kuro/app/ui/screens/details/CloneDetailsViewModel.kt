package com.kuro.app.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.data.clones.RoomCloneRepository
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.engine.CloneHandle
import com.kuro.engine.LaunchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CloneDetailsViewModel(val cloneId: String) : ViewModel() {
    private val cloneRepo = ServiceLocator.cloneRepository
    private val activityRepo = ServiceLocator.activityRepository

    private val _showDelete = MutableStateFlow(false)
    val showDelete: StateFlow<Boolean> = _showDelete

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }

    val clone: StateFlow<CloneProfile?> =
        cloneRepo.clones.combine(_showDelete) { clones, _ ->
            clones.firstOrNull { it.id == cloneId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val relatedEvents: StateFlow<List<ActivityEvent>> =
        activityRepo.events.combine(clone) { events, c ->
            events.filter { it.cloneId == (c?.id ?: cloneId) }.sortedByDescending { it.timestampMillis }.take(4)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun askDelete() { _showDelete.value = true }
    fun dismissDelete() { _showDelete.value = false }

    fun confirmDelete(onDone: () -> Unit) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId)
            if (c != null) {
                val engineResult = ServiceLocator.virtualEngine.remove(
                    CloneHandle(c.id, c.sourceAppId, c.engineUserId)
                )
                if (engineResult.isFailure) {
                    _message.value = engineResult.exceptionOrNull()?.message
                }
            }
            cloneRepo.deleteClone(cloneId)
            if (c != null) {
                activityRepo.addEvent(
                    ActivityEvent(
                        id = "ev-" + System.currentTimeMillis(),
                        cloneId = c.id,
                        cloneName = c.name,
                        type = ActivityType.DELETED,
                        timestampMillis = System.currentTimeMillis(),
                        detail = "Removed from engine"
                    )
                )
            }
            _showDelete.value = false
            onDone()
        }
    }

    fun openClone() {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId) ?: return@launch
            when (val r = ServiceLocator.virtualEngine.launch(
                CloneHandle(c.id, c.sourceAppId, c.engineUserId)
            )) {
                is LaunchResult.Started -> {
                    val now = System.currentTimeMillis()
                    (cloneRepo as? RoomCloneRepository)?.markLaunched(c.id, now)
                        ?: cloneRepo.updateClone(c.copy(lastLaunchedMillis = now))
                    activityRepo.addEvent(
                        ActivityEvent(
                            id = "ev-" + now,
                            cloneId = c.id,
                            cloneName = c.name,
                            type = ActivityType.LAUNCHED,
                            timestampMillis = now,
                            detail = "Launched by engine"
                        )
                    )
                }
                is LaunchResult.NotReady -> _message.value = r.message
                is LaunchResult.Failed -> _message.value = r.message
            }
        }
    }
}
