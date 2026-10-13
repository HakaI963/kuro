package com.kuro.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
import kotlinx.coroutines.delay
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
    val isLoading: Boolean = true
)

class LibraryViewModel : ViewModel() {
    private val cloneRepo = ServiceLocator.cloneRepository
    private val appRepo = ServiceLocator.installedAppRepository
    private val activityRepo = ServiceLocator.activityRepository

    private val _query = MutableStateFlow("")
    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    private val _isLoading = MutableStateFlow(true)
    private val _showSheetFor = MutableStateFlow<String?>(null)

    val showSheetFor: StateFlow<String?> = _showSheetFor
    val sources = appRepo.apps

    val uiState: StateFlow<LibraryUiState> = combine(
        cloneRepo.clones, _query, _filter, _isLoading
    ) { clones, query, filter, loading ->
        var list = when (filter) {
            LibraryFilter.ALL -> clones
            LibraryFilter.RUNNING -> clones.filter { it.status == CloneStatus.ACTIVE || it.status == CloneStatus.SYNCING }
            LibraryFilter.FROZEN -> clones.filter { it.status == CloneStatus.FROZEN }
            LibraryFilter.PRIVATE -> clones.filter { it.status == CloneStatus.PRIVATE || it.isolationMode.name == "PRIVATE" }
        }
        if (query.isNotBlank()) {
            list = list.filter { it.name.contains(query, ignoreCase = true) }
        }
        LibraryUiState(query, filter, clones, list, loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            delay(650)
            _isLoading.value = false
        }
    }

    fun onQuery(v: String) { _query.value = v }
    fun onFilter(f: LibraryFilter) { _filter.value = f }
    fun openSheet(id: String) { _showSheetFor.value = id }
    fun closeSheet() { _showSheetFor.value = null }

    fun toggleFreeze(id: String) {
        val c = cloneRepo.getClone(id) ?: return
        val next = if (c.status == CloneStatus.FROZEN) CloneStatus.ACTIVE else CloneStatus.FROZEN
        cloneRepo.updateClone(c.copy(status = next))
        activityRepo.addEvent(
            com.kuro.app.domain.models.ActivityEvent(
                id = "ev-" + System.currentTimeMillis(),
                cloneId = c.id,
                cloneName = c.name,
                type = if (next == CloneStatus.FROZEN) com.kuro.app.domain.models.ActivityType.FROZEN else com.kuro.app.domain.models.ActivityType.UNFROZEN,
                timestampMillis = System.currentTimeMillis(),
                detail = if (next == CloneStatus.FROZEN) "Frozen from library" else "Unfrozen from library"
            )
        )
    }

    fun delete(id: String) {
        val c = cloneRepo.getClone(id)
        cloneRepo.deleteClone(id)
        if (c != null) {
            activityRepo.addEvent(
                com.kuro.app.domain.models.ActivityEvent(
                    id = "ev-" + System.currentTimeMillis(),
                    cloneId = c.id,
                    cloneName = c.name,
                    type = com.kuro.app.domain.models.ActivityType.DELETED,
                    timestampMillis = System.currentTimeMillis(),
                    detail = "Deleted from library"
                )
            )
        }
        closeSheet()
    }
}
