package com.kuro.app.domain.repositories

import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.AppSettings
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.InstalledApp
import kotlinx.coroutines.flow.StateFlow

interface InstalledAppRepository {
    val apps: StateFlow<List<InstalledApp>>
}

interface CloneRepository {
    val clones: StateFlow<List<CloneProfile>>
    fun getClone(id: String): CloneProfile?
    fun addClone(clone: CloneProfile)
    fun updateClone(clone: CloneProfile)
    fun deleteClone(id: String)
    fun setAll(clones: List<CloneProfile>)
}

interface ActivityRepository {
    val events: StateFlow<List<ActivityEvent>>
    fun addEvent(event: ActivityEvent)
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}
