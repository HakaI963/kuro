package com.kuro.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.AppSettings
import com.kuro.app.domain.models.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel : ViewModel() {
    private val repo = ServiceLocator.settingsRepository
    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setTheme(mode: ThemeMode) = repo.update { it.copy(themeMode = mode) }
    fun setDynamic(v: Boolean) = repo.update { it.copy(dynamicColor = v) }
    fun setReduceMotion(v: Boolean) = repo.update { it.copy(reduceMotion = v) }
    fun setDefaultToLibrary(v: Boolean) = repo.update { it.copy(defaultToLibrary = v) }
    fun setAutoFreeze(v: Boolean) = repo.update { it.copy(autoFreezeIdle = v) }
    fun setShowHidden(v: Boolean) = repo.update { it.copy(showHiddenClones = v) }
    fun setAppLock(v: Boolean) = repo.update { it.copy(appLockEnabled = v, biometricUnlock = if (!v) false else it.biometricUnlock) }
    fun setBiometric(v: Boolean) = repo.update { it.copy(biometricUnlock = v) }
}
