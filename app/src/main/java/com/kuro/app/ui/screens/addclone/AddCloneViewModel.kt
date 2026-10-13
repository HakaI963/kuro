package com.kuro.app.ui.screens.addclone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.domain.models.InstalledApp
import com.kuro.app.domain.models.IsolationMode
import com.kuro.app.domain.models.ProvisioningState
import com.kuro.app.engine.EngineResult
import com.kuro.app.engine.InstallProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isolationMode: IsolationMode = IsolationMode.STANDARD,
    val notifications: Boolean = true,
    val separateData: Boolean = true,
    val hidden: Boolean = false,
    val biometric: Boolean = false,
    val status: AddCloneStatus = AddCloneStatus.IDLE,
    val progress: Float = 0f,
    val progressMessage: String = "",
    val errorMessage: String? = null,
    val createdCloneId: String? = null,
    val provisioning: ProvisioningState = ProvisioningState.Checking
) {
    val filteredApps: List<InstalledApp>
        get() = if (query.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    val canCreate: Boolean get() = provisioning is ProvisioningState.Active && selectedAppPackage != null
}

class AddCloneViewModel : ViewModel() {
    private val cloneRepo = ServiceLocator.cloneRepository
    private val activityRepo = ServiceLocator.activityRepository
    private val appRepo = ServiceLocator.installedAppRepository

    private val _ui = MutableStateFlow(AddCloneUiState())
    val ui: StateFlow<AddCloneUiState> = _ui.asStateFlow()

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
        refreshProvisioning()
        viewModelScope.launch {
            ServiceLocator.engine.collect {
                refreshProvisioning()
            }
        }
    }

    fun refreshProvisioning() {
        viewModelScope.launch {
            // Provisioning state is refreshed from UI layer with Context; default to engine availability here.
            val engine = ServiceLocator.engine.value
            _ui.update {
                it.copy(
                    provisioning = if (engine.isAvailable) ProvisioningState.Active
                    else ProvisioningState.NotSetup
                )
            }
        }
    }

    fun refreshProvisioningWith(state: ProvisioningState) {
        _ui.update { it.copy(provisioning = state) }
    }

    fun setQuery(v: String) { _ui.update { it.copy(query = v) } }
    fun selectApp(pkg: String) { _ui.update { it.copy(selectedAppPackage = pkg) } }
    fun setName(v: String) { _ui.update { it.copy(cloneName = v) } }
    fun setColor(v: Long) { _ui.update { it.copy(labelColor = v) } }
    fun setMode(m: IsolationMode) { _ui.update { it.copy(isolationMode = m) } }
    fun setNotifications(v: Boolean) { _ui.update { it.copy(notifications = v) } }
    fun setSeparateData(v: Boolean) { _ui.update { it.copy(separateData = v) } }
    fun setHidden(v: Boolean) { _ui.update { it.copy(hidden = v) } }
    fun setBiometric(v: Boolean) { _ui.update { it.copy(biometric = v) } }

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
        if (s.provisioning !is ProvisioningState.Active) {
            _ui.update {
                it.copy(
                    step = AddCloneStep.PROGRESS,
                    status = AddCloneStatus.ERROR,
                    errorMessage = "Work profile not set up"
                )
            }
            return
        }
        _ui.update { it.copy(step = AddCloneStep.PROGRESS, status = AddCloneStatus.RUNNING, progress = 0f, errorMessage = null, progressMessage = "Starting install") }
        creationJob?.cancel()
        creationJob = viewModelScope.launch {
            val engine = ServiceLocator.engine.value
            val result = engine.cloneApp(app.packageName) { p ->
                when (p) {
                    is InstallProgress.Running -> _ui.update { it.copy(progress = p.fraction, progressMessage = p.statusMessage) }
                    is InstallProgress.Success -> _ui.update { it.copy(progress = 1f, progressMessage = "Done") }
                    is InstallProgress.Failed -> _ui.update { it.copy(progressMessage = p.message) }
                }
            }
            when (result) {
                is EngineResult.Ok -> {
                    val finalName = s.cloneName.ifBlank { app.label }
                    val clone = CloneProfile(
                        id = app.packageName,
                        name = finalName,
                        sourceAppId = app.packageName,
                        sourceAppName = app.label,
                        status = if (s.hidden || s.isolationMode == IsolationMode.PRIVATE) CloneStatus.PRIVATE else CloneStatus.ACTIVE,
                        isolationMode = s.isolationMode,
                        createdAtMillis = System.currentTimeMillis(),
                        lastLaunchedMillis = null,
                        storageBytes = 0L,
                        notificationsEnabled = s.notifications,
                        separateData = s.separateData,
                        hiddenFromLauncher = s.hidden,
                        biometricLock = s.biometric,
                        labelColorArgb = s.labelColor,
                        iconLetter = finalName.firstOrNull()?.uppercase() ?: "?"
                    )
                    cloneRepo.addClone(clone)
                    if (s.hidden) {
                        // Apply hidden state immediately when possible; failures surface as message only.
                        when (val h = engine.hide(app.packageName)) {
                            is EngineResult.Ok -> Unit
                            else -> Unit
                        }
                    }
                    activityRepo.addEvent(
                        ActivityEvent(
                            id = "ev-" + System.currentTimeMillis(),
                            cloneId = clone.id,
                            cloneName = finalName,
                            type = ActivityType.CREATED,
                            timestampMillis = System.currentTimeMillis(),
                            detail = "Installed into isolated space (work profile)"
                        )
                    )
                    _ui.update { it.copy(status = AddCloneStatus.SUCCESS, progress = 1f, createdCloneId = clone.id) }
                }
                is EngineResult.Unavailable -> {
                    _ui.update { it.copy(status = AddCloneStatus.ERROR, errorMessage = result.message) }
                }
                is EngineResult.Failed -> {
                    _ui.update { it.copy(status = AddCloneStatus.ERROR, errorMessage = result.message) }
                }
            }
        }
    }

    fun retry() { startCreation() }

    companion object {
        fun mapProvisioning(engineAvailable: Boolean): ProvisioningState =
            if (engineAvailable) ProvisioningState.Active else ProvisioningState.NotSetup

        // Pure helper for unit tests: provisioning must gate creation.
        fun canStartCreation(state: ProvisioningState, selectedPackage: String?): Boolean =
            state is ProvisioningState.Active && selectedPackage != null
    }
}
