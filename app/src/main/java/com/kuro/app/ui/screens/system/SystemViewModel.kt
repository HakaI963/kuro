package com.kuro.app.ui.screens.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.core.system.SystemMetrics
import com.kuro.app.core.system.SystemMetricsReader
import com.kuro.app.data.containers.ContainerStore
import com.kuro.app.data.containers.LinuxContainer
import com.kuro.app.di.ServiceLocator
import com.kuro.app.domain.models.ActivityEvent
import com.kuro.app.domain.models.CloneProfile
import com.kuro.engine.EngineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SystemViewModel : ViewModel() {
    private val _metrics = MutableStateFlow<SystemMetrics?>(null)
    val metrics: StateFlow<SystemMetrics?> = _metrics.asStateFlow()

    val clones: StateFlow<List<CloneProfile>> = ServiceLocator.cloneRepository.clones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<ActivityEvent>> = ServiceLocator.activityRepository.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val engineStatus: StateFlow<EngineStatus> = ServiceLocator.engineStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngineStatus.Uninitialized)

    val containers: StateFlow<List<LinuxContainer>> = ContainerStore.containers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh(appFilesDir: File) {
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                SystemMetricsReader.read(appFilesDir)
            }
            _metrics.value = read
        }
    }
}
