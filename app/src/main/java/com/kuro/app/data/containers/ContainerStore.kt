package com.kuro.app.data.containers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Step 4 (Stage 2 shell): in-memory container + session registry.
 * No persistence, no engine — the list is honestly empty until Step 5
 * (proot) / Step 6 (chroot) implement the container engine.
 * Session persistence rule (Step 5+): foreground service until manual stop.
 */
enum class ContainerType { PROOT, CHROOT }

enum class ContainerStatus { RUNNING, STOPPED }

data class LinuxContainer(
    val id: String,
    val name: String,
    val distroId: String,
    val distroName: String,
    val type: ContainerType,
    val status: ContainerStatus,
    val sizeBytes: Long,
    val withGui: Boolean,
    val bridgeEnabled: Boolean
)

object ContainerStore {
    private val _containers = MutableStateFlow<List<LinuxContainer>>(emptyList())
    val containers: StateFlow<List<LinuxContainer>> = _containers.asStateFlow()

    val installedCount: Int get() = _containers.value.size
    val runningCount: Int
        get() = _containers.value.count { it.status == ContainerStatus.RUNNING }

    /** Step 5+ will add real containers here. */
    fun add(container: LinuxContainer) {
        _containers.update { it + container }
    }

    fun remove(id: String) {
        _containers.update { list -> list.filterNot { it.id == id } }
    }

    /** "Stop all sessions": clears RUNNING flags. Honest no-op when empty. */
    fun stopAll() {
        _containers.update { list ->
            list.map { it.copy(status = ContainerStatus.STOPPED) }
        }
    }

    fun setStatus(id: String, status: ContainerStatus) {
        _containers.update { list ->
            list.map { if (it.id == id) it.copy(status = status) else it }
        }
    }
}
