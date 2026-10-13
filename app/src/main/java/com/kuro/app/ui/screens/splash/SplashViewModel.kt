package com.kuro.app.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SplashViewModel : ViewModel() {
    private val settingsRepo = ServiceLocator.settingsRepository

    val onboardingCompleted = settingsRepo.settings
        .map { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    var ready = false
        private set

    fun onSplashShown(onDone: (completed: Boolean) -> Unit) {
        viewModelScope.launch {
            delay(900)
            ready = true
            onDone(settingsRepo.settings.value.onboardingCompleted)
        }
    }
}
