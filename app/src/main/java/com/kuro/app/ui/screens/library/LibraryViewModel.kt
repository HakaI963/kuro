package com.kuro.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val query: String = "",
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
    private val _isLoading = MutableStateFlow(false)
    private val _showSheetFor = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)

    val showSheetFor: StateFlow<String?> = _showSheetFor
    val sources = appRepo.apps

    val uiState: StateFlow<LibraryUiState> = combine(
        cloneRepo.clones, _query, _isLoading, _message
    ) { clones, query, loading, message ->
        val list = if (query.isBlank()) clones
        else clones.filter { it.name.contains(query, ignoreCase = true) }
        LibraryUiState(query, clones, list, loading, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQuery(v: String) { _query.value = v }
    fun openSheet(id: String) { _showSheetFor.value = id }
    fun closeSheet() { _showSheetFor.value = null }
    fun clearMessage() { _message.value = null }

    fun delete(id: String) {
        viewModelScope.launch {
            val c = cloneRepo.getClone(id)
            if (c != null) {
                val engineResult = ServiceLocator.virtualEngine.remove(
                    com.kuro.engine.CloneHandle(c.id, c.sourceAppId)
                )
                if (engineResult.isFailure) {
                    _message.value = engineResult.exceptionOrNull()?.message
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
