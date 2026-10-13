package com.kuro.app.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.data.containers.ContainerStatus
import com.kuro.app.data.containers.ContainerStore
import com.kuro.app.data.containers.LinuxContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TerminalViewModel : ViewModel() {
    val containers: StateFlow<List<LinuxContainer>> = ContainerStore.containers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun stop(id: String) {
        viewModelScope.launch { ContainerStore.setStatus(id, ContainerStatus.STOPPED) }
    }

    fun delete(id: String) {
        viewModelScope.launch { ContainerStore.remove(id) }
    }

    fun stopAll() {
        viewModelScope.launch { ContainerStore.stopAll() }
    }
}
