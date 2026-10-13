package com.kuro.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface EngineStatus {
    data object Uninitialized : EngineStatus
    data object Ready : EngineStatus
    data class Incompatible(val androidVersion: Int, val message: String) : EngineStatus
    data class Error(val message: String) : EngineStatus
}

sealed interface CloneSource {
    data class InstalledApp(val packageName: String) : CloneSource
    data class ApkFile(val uri: String) : CloneSource
}

data class CloneHandle(
    val cloneId: String,
    val packageName: String,
    val userId: Int = -1
)

/** Smallest non-negative engine user slot not in [used]. Slot 0 is the owner slot. */
fun allocateUserSlot(used: Set<Int>): Int {
    var candidate = 1
    while (candidate in used) candidate++
    return candidate
}

sealed interface InstallEvent {
    data class Progress(val fraction: Float, val message: String) : InstallEvent
    data object Done : InstallEvent
    data class Failed(val message: String) : InstallEvent
}

sealed interface LaunchResult {
    data object Started : LaunchResult
    data class NotReady(val message: String) : LaunchResult
    data class Failed(val message: String) : LaunchResult
}

interface VirtualEngine {
    val status: Flow<EngineStatus>
    suspend fun init(): EngineStatus
    suspend fun installIntoSpace(
        source: CloneSource,
        cloneName: String,
        colorTag: Long,
        onEvent: (InstallEvent) -> Unit
    ): Result<CloneHandle>
    suspend fun launch(handle: CloneHandle): LaunchResult
    suspend fun listSpacePackages(): List<CloneHandle>
    suspend fun remove(handle: CloneHandle): Result<Unit>
}

class NoOpEngine(
    private val reason: String = "Engine backend not available in this build",
    private val fixedStatus: EngineStatus? = null
) : VirtualEngine {
    private val state = MutableStateFlow<EngineStatus>(EngineStatus.Uninitialized)
    override val status: Flow<EngineStatus> = state.asStateFlow()

    override suspend fun init(): EngineStatus {
        state.value = fixedStatus ?: EngineStatus.Uninitialized
        return state.value
    }

    private fun notReady(): String = reason

    override suspend fun installIntoSpace(
        source: CloneSource,
        cloneName: String,
        colorTag: Long,
        onEvent: (InstallEvent) -> Unit
    ): Result<CloneHandle> {
        onEvent(InstallEvent.Failed(notReady()))
        return Result.failure(IllegalStateException(notReady()))
    }

    override suspend fun launch(handle: CloneHandle): LaunchResult =
        LaunchResult.NotReady(notReady())

    override suspend fun listSpacePackages(): List<CloneHandle> = emptyList()

    override suspend fun remove(handle: CloneHandle): Result<Unit> =
        Result.failure(IllegalStateException(notReady()))

    fun stateFlow(): StateFlow<EngineStatus> = state.asStateFlow()
}
