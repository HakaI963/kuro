package com.kuro.app.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.engine.EngineResult
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

    private val _showOpened = MutableStateFlow(false)
    val showOpened: StateFlow<Boolean> = _showOpened

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun openSettings() { _showSettings.value = true }
    fun dismissSettings() { _showSettings.value = false }
    fun clearMessage() { _message.value = null }

    fun setNotifications(v: Boolean) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId) ?: return@launch
            cloneRepo.updateClone(c.copy(notificationsEnabled = v))
        }
    }
    fun setSeparateData(v: Boolean) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId) ?: return@launch
            cloneRepo.updateClone(c.copy(separateData = v))
        }
    }
    fun setBiometric(v: Boolean) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId) ?: return@launch
            cloneRepo.updateClone(c.copy(biometricLock = v))
        }
    }
    fun setHidden(v: Boolean) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId) ?: return@launch
            val engine = ServiceLocator.engine.value
            val result = if (v) engine.hide(c.sourceAppId) else engine.unhide(c.sourceAppId)
            when (result) {
                is EngineResult.Ok -> {
                    cloneRepo.updateClone(c.copy(hiddenFromLauncher = v))
                    activityRepo.addEvent(
                        ActivityEvent(
                            id = "ev-" + System.currentTimeMillis(),
                            cloneId = c.id,
                            cloneName = c.name,
                            type = if (v) ActivityType.HIDDEN else ActivityType.UNHIDDEN,
                            timestampMillis = System.currentTimeMillis(),
                            detail = if (v) "Hidden from launcher" else "Shown in launcher"
                        )
                    )
                    _message.value = if (v) "Hidden" else "Unhidden"
                }
                is EngineResult.Unavailable -> {
                    // Persist the preference so it applies once provisioned.
                    cloneRepo.updateClone(c.copy(hiddenFromLauncher = v))
                    _message.value = result.message
                }
                is EngineResult.Failed -> _message.value = result.message
            }
        }
    }

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
                when (val r = ServiceLocator.engine.value.remove(c.sourceAppId)) {
                    is EngineResult.Ok, is EngineResult.Unavailable -> Unit
                    is EngineResult.Failed -> _message.value = r.message
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
                        detail = "Deleted from details"
                    )
                )
            }
            _showDelete.value = false
            onDone()
        }
    }

    fun toggleFreeze() {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId) ?: return@launch
            val freezing = c.status != CloneStatus.FROZEN
            val engine = ServiceLocator.engine.value
            val result = if (freezing) engine.freeze(c.sourceAppId) else engine.unfreeze(c.sourceAppId)
            when (result) {
                is EngineResult.Ok -> {
                    cloneRepo.updateClone(c.copy(status = if (freezing) CloneStatus.FROZEN else CloneStatus.ACTIVE))
                    activityRepo.addEvent(
                        ActivityEvent(
                            id = "ev-" + System.currentTimeMillis(),
                            cloneId = c.id,
                            cloneName = c.name,
                            type = if (freezing) ActivityType.FROZEN else ActivityType.UNFROZEN,
                            timestampMillis = System.currentTimeMillis(),
                            detail = if (freezing) "Frozen from details" else "Unfrozen from details"
                        )
                    )
                    _message.value = if (freezing) "Frozen" else "Unfrozen"
                }
                is EngineResult.Unavailable -> _message.value = result.message
                is EngineResult.Failed -> _message.value = result.message
            }
        }
    }

    fun openClone() {
        viewModelScope.launch {
            val c = cloneRepo.getClone(cloneId) ?: return@launch
            when (val r = ServiceLocator.engine.value.open(c.sourceAppId)) {
                is EngineResult.Ok -> {
                    activityRepo.addEvent(
                        ActivityEvent(
                            id = "ev-" + System.currentTimeMillis(),
                            cloneId = c.id,
                            cloneName = c.name,
                            type = ActivityType.LAUNCHED,
                            timestampMillis = System.currentTimeMillis(),
                            detail = "Launch requested in isolated space (work profile)"
                        )
                    )
                    cloneRepo.updateClone(c.copy(lastLaunchedMillis = System.currentTimeMillis()))
                    _showOpened.value = true
                }
                is EngineResult.Unavailable -> {
                    activityRepo.addEvent(
                        ActivityEvent(
                            id = "ev-" + System.currentTimeMillis(),
                            cloneId = c.id,
                            cloneName = c.name,
                            type = ActivityType.LAUNCHED,
                            timestampMillis = System.currentTimeMillis(),
                            detail = "Launch requested (work profile not ready)"
                        )
                    )
                    _message.value = r.message
                }
                is EngineResult.Failed -> _message.value = r.message
            }
        }
    }

    fun dismissOpened() { _showOpened.value = false }
}
