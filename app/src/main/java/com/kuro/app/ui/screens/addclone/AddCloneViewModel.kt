package com.kuro.app.ui.screens.addclone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.domain.models.InstalledApp
import com.kuro.engine.CloneSource
import com.kuro.engine.EngineStatus
import com.kuro.engine.InstallEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddCloneStep { SOURCE, OPTIONS, PROGRESS }
enum class AddCloneStatus { IDLE, RUNNING, SUCCESS, ERROR }

data class AddCloneUiState(
    val step: AddCloneStep = AddCloneStep.SOURCE,
    val query: String = "",
    val installedApps: List<InstalledApp> = emptyList(),
    val appsLoading: Boolean = true,
    val selectedAppPackage: String? = null,
    val cloneName: String = "",
    val labelColor: Long = 0xFF7C6CFF,
    val status: AddCloneStatus = AddCloneStatus.IDLE,
    val progress: Float = 0f,
    val progressMessage: String = "",
    val errorMessage: String? = null,
    val createdCloneId: String? = null,
    val engineStatus: EngineStatus = EngineStatus.Uninitialized
) {
    val filteredApps: List<InstalledApp>
        get() = if (query.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    val engineReady: Boolean get() = engineStatus is EngineStatus.Ready
    val canCreate: Boolean get() = engineReady && selectedAppPackage != null
}

class AddCloneViewModel : ViewModel() {
    private val cloneRepo = ServiceLocator.cloneRepository
    private val activityRepo = ServiceLocator.activityRepository
    private val appRepo = ServiceLocator.installedAppRepository
    private val settingsRepo = ServiceLocator.settingsRepository

    private val _ui = MutableStateFlow(AddCloneUiState())
    val ui: StateFlow<AddCloneUiState> = _ui.asStateFlow()

    val reduceMotion = settingsRepo.settings
        .combine(appRepo.apps) { s, _ -> s.reduceMotion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val apps = appRepo.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var creationJob: Job? = null

    init {
        viewModelScope.launch {
            appRepo.apps.collect { list ->
                _ui.update { it.copy(installedApps = list, appsLoading = false) }
            }
        }
        viewModelScope.launch {
            appRepo.refresh()
        }
        viewModelScope.launch {
            ServiceLocator.engineStatus.collect { status ->
                _ui.update { it.copy(engineStatus = status) }
            }
        }
    }

    fun setQuery(v: String) { _ui.update { it.copy(query = v) } }
    fun selectApp(pkg: String) { _ui.update { it.copy(selectedAppPackage = pkg) } }
    fun setName(v: String) { _ui.update { it.copy(cloneName = v) } }
    fun setColor(v: Long) { _ui.update { it.copy(labelColor = v) } }

    fun refreshApps() {
        viewModelScope.launch {
            _ui.update { it.copy(appsLoading = true) }
            appRepo.refresh()
            _ui.update { it.copy(appsLoading = false) }
        }
    }

    fun goToOptions(): Boolean {
        val s = _ui.value
        if (s.selectedAppPackage == null) return false
        if (s.cloneName.isBlank()) {
            val app = s.installedApps.firstOrNull { it.packageName == s.selectedAppPackage }
            _ui.update { it.copy(step = AddCloneStep.OPTIONS, cloneName = app?.label ?: "") }
        } else {
            _ui.update { it.copy(step = AddCloneStep.OPTIONS) }
        }
        return true
    }

    fun backToSource() { _ui.update { it.copy(step = AddCloneStep.SOURCE) } }
    fun backToOptions() {
        creationJob?.cancel()
        _ui.update { it.copy(step = AddCloneStep.OPTIONS, status = AddCloneStatus.IDLE, progress = 0f, errorMessage = null) }
    }

    fun selectedApp(): InstalledApp? {
        val s = _ui.value
        return s.installedApps.firstOrNull { it.packageName == s.selectedAppPackage }
    }

    fun startCreation() {
        val s = _ui.value
        val app = selectedApp() ?: return
        if (s.engineStatus !is EngineStatus.Ready) {
            _ui.update {
                it.copy(
                    step = AddCloneStep.PROGRESS,
                    status = AddCloneStatus.ERROR,
                    errorMessage = "Engine not ready"
                )
            }
            return
        }
        _ui.update { it.copy(step = AddCloneStep.PROGRESS, status = AddCloneStatus.RUNNING, progress = 0f, errorMessage = null, progressMessage = "Starting install") }
        creationJob?.cancel()
        creationJob = viewModelScope.launch {
            val engine = ServiceLocator.virtualEngine
            val result = engine.installIntoSpace(
                CloneSource.InstalledApp(app.packageName),
                s.cloneName.ifBlank { app.label },
                s.labelColor
            ) { event ->
                when (event) {
                    is InstallEvent.Progress -> _ui.update { it.copy(progress = event.fraction, progressMessage = event.message) }
                    is InstallEvent.Done -> _ui.update { it.copy(progress = 1f, progressMessage = "Done") }
                    is InstallEvent.Failed -> _ui.update { it.copy(progressMessage = event.message) }
                }
            }
            result.fold(
                onSuccess = { handle ->
                    val finalName = s.cloneName.ifBlank { app.label }
                    val clone = CloneProfile(
                        id = handle.cloneId,
                        name = finalName,
                        sourceAppId = app.packageName,
                        sourceAppName = app.label,
                        status = CloneStatus.ACTIVE,
                        engineUserId = handle.userId,
                        createdAtMillis = System.currentTimeMillis(),
                        lastLaunchedMillis = null,
                        labelColorArgb = s.labelColor,
                        iconLetter = finalName.firstOrNull()?.uppercase() ?: "?"
                    )
                    cloneRepo.addClone(clone)
                    activityRepo.addEvent(
                        ActivityEvent(
                            id = "ev-" + System.currentTimeMillis(),
                            cloneId = clone.id,
                            cloneName = finalName,
                            type = ActivityType.CREATED,
                            timestampMillis = System.currentTimeMillis(),
                            detail = "Installed inside Kuro space"
                        )
                    )
                    _ui.update { it.copy(status = AddCloneStatus.SUCCESS, progress = 1f, createdCloneId = clone.id) }
                },
                onFailure = { e ->
                    _ui.update { it.copy(status = AddCloneStatus.ERROR, errorMessage = e.message) }
                }
            )
        }
    }

    fun retry() { startCreation() }

    companion object {
        fun canStartCreation(status: EngineStatus, selectedPackage: String?): Boolean =
            status is EngineStatus.Ready && selectedPackage != null
    }
}
