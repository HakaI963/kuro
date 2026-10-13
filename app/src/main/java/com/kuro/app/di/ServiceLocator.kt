package com.kuro.app.di

import android.content.Context
import com.kuro.app.data.mock.MockActivityRepository
import com.kuro.app.data.mock.MockCloneRepository
import com.kuro.app.data.mock.MockInstalledAppRepository
import com.kuro.app.data.mock.MockSettingsRepository
import com.kuro.app.domain.repositories.ActivityRepository
import com.kuro.app.domain.repositories.CloneRepository
import com.kuro.app.domain.repositories.InstalledAppRepository
import com.kuro.app.domain.repositories.SettingsRepository

object ServiceLocator {
    lateinit var installedAppRepository: InstalledAppRepository
        private set
    lateinit var cloneRepository: CloneRepository
        private set
    lateinit var activityRepository: ActivityRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    private var initialized = false

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (initialized) return
        installedAppRepository = MockInstalledAppRepository()
        cloneRepository = MockCloneRepository()
        activityRepository = MockActivityRepository()
        settingsRepository = MockSettingsRepository()
        initialized = true
    }

    fun initForPreview() {
        if (initialized) return
        installedAppRepository = MockInstalledAppRepository()
        cloneRepository = MockCloneRepository()
        activityRepository = MockActivityRepository()
        settingsRepository = MockSettingsRepository()
        initialized = true
    }
}
