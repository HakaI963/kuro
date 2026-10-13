package com.kuro.app.ui.screens.library

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

enum class LibraryFilter { ALL, RUNNING, FROZEN, PRIVATE }

data class LibraryUiState(
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.ALL,
    val clones: List<CloneProfile> = emptyList(),
    val filtered: List<CloneProfile> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

class LibraryViewModel : ViewModel() {
    private val cloneRepo = ServiceLocator.cloneRepository
    private val appRepo = ServiceLocator.installedAppRepository
    private val activityRepo = ServiceLocator.activityRepository

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    private val _isLoading = MutableStateFlow(false)
    private val _showSheetFor = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)

    val showSheetFor: StateFlow<String?> = _showSheetFor
    val sources = appRepo.apps

    val uiState: StateFlow<LibraryUiState> = combine(
        cloneRepo.clones, _query, _filter, _isLoading, _message
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val clones = args[0] as List<CloneProfile>
        val query = args[1] as String
        val filter = args[2] as LibraryFilter
        val loading = args[3] as Boolean
        val message = args[4] as String?
        var list = when (filter) {
            LibraryFilter.ALL -> clones
            LibraryFilter.RUNNING -> clones.filter { it.status == CloneStatus.ACTIVE }
            LibraryFilter.FROZEN -> clones.filter { it.status == CloneStatus.FROZEN }
            LibraryFilter.PRIVATE -> clones.filter { it.status == CloneStatus.PRIVATE }
        }
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        LibraryUiState(query, filter, clones, list, loading, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQuery(v: String) { _query.value = v }
    fun onFilter(f: LibraryFilter) { _filter.value = f }
    fun openSheet(id: String) { _showSheetFor.value = id }
    fun closeSheet() { _showSheetFor.value = null }
    fun clearMessage() { _message.value = null }

    fun toggleFreeze(id: String) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(id) ?: return@launch
            val engine = ServiceLocator.engine.value
            val freezing = c.status != CloneStatus.FROZEN
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
                            detail = if (freezing) "Frozen from library" else "Unfrozen from library"
                        )
                    )
                    _message.value = if (freezing) "Frozen" else "Unfrozen"
                }
                is EngineResult.Unavailable -> _message.value = result.message
                is EngineResult.Failed -> _message.value = result.message
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(id)
            val engine = ServiceLocator.engine.value
            if (c != null) {
                when (val r = engine.remove(c.sourceAppId)) {
                    is EngineResult.Ok, is EngineResult.Unavailable -> {
                        // Unavailable means no profile: still allow removing the Room record
                        // so the UI never gets stuck. Real uninstall happens when possible.
                    }
                    is EngineResult.Failed -> {
                        _message.value = r.message
                    }
                }
            }
            cloneRepo.deleteClone(id)
            if (c != null) {
                activityRepo.addEvent(
                    ActivityEvent(
                        id = "ev-" + System.currentTimeMillis(),
                        cloneId = c.id,
                        cloneName = c.name,
                        type = ActivityType.DELETED,
                        timestampMillis = System.currentTimeMillis(),
                        detail = "Deleted from library"
                    )
                )
            }
            closeSheet()
        }
    }
}
