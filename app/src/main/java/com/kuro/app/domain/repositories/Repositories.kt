package com.kuro.app.domain.repositories

import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.AppSettings
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.InstalledApp
import kotlinx.coroutines.flow.StateFlow

interface InstalledAppRepository {
    val apps: StateFlow<List<InstalledApp>>
    suspend fun refresh()
    fun getIcon(packageName: String): android.graphics.drawable.Drawable?
}

interface CloneRepository {
    val clones: StateFlow<List<CloneProfile>>
    suspend fun getClone(id: String): CloneProfile?
    suspend fun addClone(clone: CloneProfile)
    suspend fun updateClone(clone: CloneProfile)
    suspend fun deleteClone(id: String)
}

interface ActivityRepository {
    val events: StateFlow<List<ActivityEvent>>
    suspend fun addEvent(event: ActivityEvent)
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}
