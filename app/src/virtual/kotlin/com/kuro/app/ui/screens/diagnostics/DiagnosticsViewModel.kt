package com.kuro.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.engine.blackbox.BlackBoxEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(DiagnosticsUiState())
    val ui: StateFlow<DiagnosticsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = DiagnosticsUiState(loading = true)
            val engine = ServiceLocator.virtualEngine
            if (engine is BlackBoxEngine) {
                val d = engine.diagnostics()
                _ui.value = DiagnosticsUiState(
                    loading = false,
                    data = DiagnosticsData(
                        implClass = d.implClass,
                        commit = d.commit,
                        nativeLoaded = d.nativeLoaded,
                        spaces = d.spaces.map { SpaceRow(it.userId, it.packages) },
                        spaceDir = d.spaceDir,
                        spaceBytes = d.spaceBytes,
                        lastError = d.lastError
                    )
                )
            } else {
                _ui.value = DiagnosticsUiState(loading = false, stub = true)
            }
        }
    }
}
