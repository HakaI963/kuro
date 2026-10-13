package com.kuro.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.AppSettings
import com.kuro.app.domain.models.ProvisionReason
import com.kuro.app.domain.models.ProvisioningState
import com.kuro.app.domain.models.ThemeMode
import com.kuro.app.engine.ProvisioningManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val repo = ServiceLocator.settingsRepository
    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _provisioning = MutableStateFlow<ProvisioningState>(ProvisioningState.Checking)
    val provisioning: StateFlow<ProvisioningState> = _provisioning

    fun setTheme(mode: ThemeMode) = repo.update { it.copy(themeMode = mode) }
    fun setDynamic(v: Boolean) = repo.update { it.copy(dynamicColor = v) }
    fun setReduceMotion(v: Boolean) = repo.update { it.copy(reduceMotion = v) }
    fun setDefaultToLibrary(v: Boolean) = repo.update { it.copy(defaultToLibrary = v) }
    fun setAutoFreeze(v: Boolean) = repo.update { it.copy(autoFreezeIdle = v) }
    fun setShowHidden(v: Boolean) = repo.update { it.copy(showHiddenClones = v) }
    fun setAppLock(v: Boolean) = repo.update { it.copy(appLockEnabled = v, biometricUnlock = if (!v) false else it.biometricUnlock) }
    fun setBiometric(v: Boolean) = repo.update { it.copy(biometricUnlock = v) }

    fun refreshProvisioning(context: Context) {
        viewModelScope.launch {
            _provisioning.value = ProvisioningState.Checking
            _provisioning.value = ProvisioningManager.currentState(context)
            ServiceLocator.refreshEngine()
        }
    }

    fun onProvisionResult(ok: Boolean) {
        _provisioning.value = if (ok) {
            ProvisioningState.Active
        } else {
            ProvisioningState.Blocked(ProvisionReason.USER_CANCELLED)
        }
        ServiceLocator.refreshEngine()
    }

    fun provisionIntent(context: Context) = ProvisioningManager.buildProvisionIntent(context)
}
