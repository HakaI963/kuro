package com.kuro.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import com.kuro.app.di.ServiceLocator

class OnboardingViewModel : ViewModel() {
    private val settingsRepo = ServiceLocator.settingsRepository

    fun completeOnboarding() {
        settingsRepo.update { it.copy(onboardingCompleted = true) }
    }
}
