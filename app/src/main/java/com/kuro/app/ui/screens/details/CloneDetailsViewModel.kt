package com.kuro.app.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
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

    fun openSettings() { _showSettings.value = true }
    fun dismissSettings() { _showSettings.value = false }

    fun setNotifications(v: Boolean) {
        val c = cloneRepo.getClone(cloneId) ?: return
        cloneRepo.updateClone(c.copy(notificationsEnabled = v))
    }
    fun setSeparateData(v: Boolean) {
        val c = cloneRepo.getClone(cloneId) ?: return
        cloneRepo.updateClone(c.copy(separateData = v))
    }
    fun setHidden(v: Boolean) {
        val c = cloneRepo.getClone(cloneId) ?: return
        cloneRepo.updateClone(c.copy(hiddenFromLauncher = v))
    }
    fun setBiometric(v: Boolean) {
        val c = cloneRepo.getClone(cloneId) ?: return
        cloneRepo.updateClone(c.copy(biometricLock = v))
    }

    val clone: StateFlow<CloneProfile?> = combine(cloneRepo.clones, _showDelete) { clones, _ ->
        clones.firstOrNull { it.id == cloneId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), cloneRepo.getClone(cloneId))

    val relatedEvents: StateFlow<List<ActivityEvent>> =
        activityRepo.events.combine(clone) { events, c ->
            events.filter { it.cloneId == (c?.id ?: cloneId) }.sortedByDescending { it.timestampMillis }.take(4)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun askDelete() { _showDelete.value = true }
    fun dismissDelete() { _showDelete.value = false }

    fun confirmDelete(onDone: () -> Unit) {
        val c = cloneRepo.getClone(cloneId)
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

    fun toggleFreeze() {
        val c = cloneRepo.getClone(cloneId) ?: return
        val next = if (c.status == CloneStatus.FROZEN) CloneStatus.ACTIVE else CloneStatus.FROZEN
        cloneRepo.updateClone(c.copy(status = next))
        activityRepo.addEvent(
            ActivityEvent(
                id = "ev-" + System.currentTimeMillis(),
                cloneId = c.id,
                cloneName = c.name,
                type = if (next == CloneStatus.FROZEN) ActivityType.FROZEN else ActivityType.UNFROZEN,
                timestampMillis = System.currentTimeMillis(),
                detail = if (next == CloneStatus.FROZEN) "Frozen from details" else "Unfrozen from details"
            )
        )
    }

    fun mockOpen() {
        val c = cloneRepo.getClone(cloneId) ?: return
        viewModelScope.launch {
            activityRepo.addEvent(
                ActivityEvent(
                    id = "ev-" + System.currentTimeMillis(),
                    cloneId = c.id,
                    cloneName = c.name,
                    type = ActivityType.LAUNCHED,
                    timestampMillis = System.currentTimeMillis(),
                    detail = "Opened isolated space (mock)"
                )
            )
            cloneRepo.updateClone(c.copy(lastLaunchedMillis = System.currentTimeMillis()))
            _showOpened.value = true
        }
    }

    fun dismissOpened() { _showOpened.value = false }
}
