package com.kuro.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.AppSettings
import com.kuro.app.domain.models.ThemeMode
import com.kuro.engine.EngineStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val repo = ServiceLocator.settingsRepository
    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val engineStatus: StateFlow<EngineStatus> = ServiceLocator.engineStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngineStatus.Uninitialized)

    fun setTheme(mode: ThemeMode) = repo.update { it.copy(themeMode = mode) }
    fun setDynamic(v: Boolean) = repo.update { it.copy(dynamicColor = v) }
    fun setReduceMotion(v: Boolean) = repo.update { it.copy(reduceMotion = v) }
    fun setDefaultToLibrary(v: Boolean) = repo.update { it.copy(defaultToLibrary = v) }

    fun retryEngineInit() {
        viewModelScope.launch {
            ServiceLocator.virtualEngine.init()
        }
    }
}
