package com.kuro.app.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticsViewModel : ViewModel() {
    private val _ui = MutableStateFlow(DiagnosticsUiState(loading = false, stub = true))
    val ui: StateFlow<DiagnosticsUiState> = _ui.asStateFlow()

    fun refresh() = Unit
}
