package com.kuro.app.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActivityUiState(
    val events: List<ActivityEvent> = emptyList(),
    val isLoading: Boolean = true
)

class ActivityViewModel : ViewModel() {
    private val repo = ServiceLocator.activityRepository
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<ActivityUiState> = combine(repo.events, _isLoading) { events, loading ->
        // Real Room events only. Fresh install starts empty by design.
        ActivityUiState(events.sortedByDescending { it.timestampMillis }, loading && events.isEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    init {
        // Mark loaded once the Room flow emits.
        _isLoading.value = false
    }
}
