package com.kuro.app.ui.screens.addclone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.data.mock.MockData
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.ActivityType
import com.kuro.app.domain.models.CloneProfile
import com.kuro.app.domain.models.CloneStatus
import com.kuro.app.domain.models.InstalledApp
import com.kuro.app.domain.models.IsolationMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class AddCloneStep { SOURCE, OPTIONS, PROGRESS }
enum class AddCloneStatus { IDLE, RUNNING, SUCCESS, ERROR }

data class AddCloneUiState(
    val step: AddCloneStep = AddCloneStep.SOURCE,
    val sourceTab: Int = 0,
    val installedApps: List<InstalledApp> = MockData.installedApps,
    val apkApps: List<InstalledApp> = MockData.apkSamples,
    val selectedAppId: String? = null,
    val cloneName: String = "",
    val labelColor: Long = 0xFF7C6CFF,
    val isolationMode: IsolationMode = IsolationMode.STANDARD,
    val notifications: Boolean = true,
    val separateData: Boolean = true,
    val hidden: Boolean = false,
    val biometric: Boolean = false,
    val status: AddCloneStatus = AddCloneStatus.IDLE,
    val progress: Float = 0f,
    val progressStep: Int = 0,
    val createdCloneId: String? = null
)

class AddCloneViewModel : ViewModel() {
    private val cloneRepo = ServiceLocator.cloneRepository
    private val activityRepo = ServiceLocator.activityRepository

    private val _ui = MutableStateFlow(AddCloneUiState())
    val ui: StateFlow<AddCloneUiState> = _ui.asStateFlow()

    private var creationJob: Job? = null

    fun selectApp(id: String) { _ui.update { it.copy(selectedAppId = id) } }
    fun setTab(i: Int) { _ui.update { it.copy(sourceTab = i) } }
    fun setName(v: String) { _ui.update { it.copy(cloneName = v) } }
    fun setColor(v: Long) { _ui.update { it.copy(labelColor = v) } }
    fun setMode(m: IsolationMode) { _ui.update { it.copy(isolationMode = m) } }
    fun setNotifications(v: Boolean) { _ui.update { it.copy(notifications = v) } }
    fun setSeparateData(v: Boolean) { _ui.update { it.copy(separateData = v) } }
    fun setHidden(v: Boolean) { _ui.update { it.copy(hidden = v) } }
    fun setBiometric(v: Boolean) { _ui.update { it.copy(biometric = v) } }

    fun goToOptions(): Boolean {
        val s = _ui.value
        if (s.selectedAppId == null) return false
        if (s.cloneName.isBlank()) {
            val app = (s.installedApps + s.apkApps).firstOrNull { it.id == s.selectedAppId }
            _ui.update { it.copy(step = AddCloneStep.OPTIONS, cloneName = app?.name ?: "") }
        } else {
            _ui.update { it.copy(step = AddCloneStep.OPTIONS) }
        }
        return true
    }

    fun backToSource() { _ui.update { it.copy(step = AddCloneStep.SOURCE) } }
    fun backToOptions() {
        creationJob?.cancel()
        _ui.update { it.copy(step = AddCloneStep.OPTIONS, status = AddCloneStatus.IDLE, progress = 0f, progressStep = 0) }
    }

    fun selectedApp(): InstalledApp? {
        val s = _ui.value
        return (s.installedApps + s.apkApps).firstOrNull { it.id == s.selectedAppId }
    }

    fun startCreation(simulateError: Boolean = false) {
        val s = _ui.value
        val app = selectedApp() ?: return
        _ui.update { it.copy(step = AddCloneStep.PROGRESS, status = AddCloneStatus.RUNNING, progress = 0f, progressStep = 0) }
        creationJob?.cancel()
        creationJob = viewModelScope.launch {
            val steps = 3
            for (i in 0 until steps) {
                _ui.update { it.copy(progressStep = i, progress = i / 3f) }
                delay(900)
                _ui.update { it.copy(progress = (i + 1) / 3f) }
            }
            if (simulateError) {
                _ui.update { it.copy(status = AddCloneStatus.ERROR) }
                return@launch
            }
            val finalName = s.cloneName.ifBlank { app.name }
            val id = "clone-" + UUID.randomUUID().toString().take(8)
            val clone = CloneProfile(
                id = id,
                name = finalName,
                sourceAppId = app.id,
                sourceAppName = app.name,
                status = if (s.isolationMode == IsolationMode.PRIVATE || s.hidden) CloneStatus.PRIVATE else CloneStatus.ACTIVE,
                isolationMode = s.isolationMode,
                createdAtMillis = System.currentTimeMillis(),
                lastLaunchedMillis = null,
                storageBytes = app.sizeBytes + 40_000_000L,
                notificationsEnabled = s.notifications,
                separateData = s.separateData,
                hiddenFromLauncher = s.hidden,
                biometricLock = s.biometric,
                labelColorArgb = s.labelColor,
                iconLetter = finalName.firstOrNull()?.uppercase() ?: app.iconLetter
            )
            cloneRepo.addClone(clone)
            activityRepo.addEvent(
                ActivityEvent(
                    id = "ev-" + System.currentTimeMillis(),
                    cloneId = id,
                    cloneName = finalName,
                    type = ActivityType.CREATED,
                    timestampMillis = System.currentTimeMillis(),
                    detail = "Profile prepared (mock)"
                )
            )
            _ui.update { it.copy(status = AddCloneStatus.SUCCESS, progress = 1f, createdCloneId = id) }
        }
    }

    fun retry() { startCreation(simulateError = false) }
}
