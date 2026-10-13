package com.kuro.app.data.mock

import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.AppSettings
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.InstalledApp
import com.kuro.app.domain.repositories.ActivityRepository
import com.kuro.app.domain.repositories.CloneRepository
import com.kuro.app.domain.repositories.InstalledAppRepository
import com.kuro.app.domain.repositories.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MockInstalledAppRepository : InstalledAppRepository {
    private val _apps = MutableStateFlow(MockData.installedApps)
    override val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()
}

class MockCloneRepository : CloneRepository {
    private val _clones = MutableStateFlow(MockData.initialClones)
    override val clones: StateFlow<List<CloneProfile>> = _clones.asStateFlow()

    override fun getClone(id: String): CloneProfile? = _clones.value.firstOrNull { it.id == id }

    override fun addClone(clone: CloneProfile) {
        _clones.update { it + clone }
    }

    override fun updateClone(clone: CloneProfile) {
        _clones.update { list -> list.map { if (it.id == clone.id) clone else it } }
    }

    override fun deleteClone(id: String) {
        _clones.update { list -> list.filterNot { it.id == id } }
    }

    override fun setAll(clones: List<CloneProfile>) {
        _clones.value = clones
    }
}

class MockActivityRepository : ActivityRepository {
    private val _events = MutableStateFlow(MockData.initialActivity)
    override val events: StateFlow<List<ActivityEvent>> = _events.asStateFlow()

    override fun addEvent(event: ActivityEvent) {
        _events.update { listOf(event) + it }
    }
}

class MockSettingsRepository : SettingsRepository {
    private val _settings = MutableStateFlow(AppSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
    }
}
