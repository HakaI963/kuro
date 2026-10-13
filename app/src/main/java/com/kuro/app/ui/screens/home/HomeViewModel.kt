package com.kuro.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.CloneProfile
import com.kuro.engine.EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val query: String = "",
    val clones: List<CloneProfile> = emptyList(),
    val filtered: List<CloneProfile> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val cloneRepo = ServiceLocator.cloneRepository
    private val settingsRepo = ServiceLocator.settingsRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _isLoading = MutableStateFlow(true)
    private val _loadFailed = MutableStateFlow(false)

    val reduceMotion = settingsRepo.settings
        .combine(_query) { s, _ -> s.reduceMotion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val engineStatus: StateFlow<EngineStatus> = ServiceLocator.engineStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngineStatus.Uninitialized)

    val engineInitializing = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            ServiceLocator.engineStatus.collect { status ->
                if (status !is EngineStatus.Uninitialized) engineInitializing.value = false
            }
        }
        viewModelScope.launch {
            cloneRepo.clones.first()
            _isLoading.value = false
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        cloneRepo.clones, _query, _isLoading, _loadFailed
    ) { clones, query, loading, failed ->
        val filtered = if (query.isBlank()) clones
        else clones.filter { it.name.contains(query, ignoreCase = true) || it.sourceAppName.contains(query, ignoreCase = true) }
        HomeUiState(query, clones, filtered, loading, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            cloneRepo.clones.first()
            _isLoading.value = false
        }
    }

    fun onQueryChange(v: String) { _query.value = v }

    fun retry() {
        _loadFailed.value = false
        _isLoading.value = true
        viewModelScope.launch {
            cloneRepo.clones.first()
            _isLoading.value = false
        }
    }
}
