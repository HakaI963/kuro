package com.kuro.app.engine

sealed interface InstallProgress {
    data class Running(val fraction: Float, val statusMessage: String) : InstallProgress
    data object Success : InstallProgress
    data class Failed(val message: String) : InstallProgress
}

sealed interface EngineResult {
    data object Ok : EngineResult
    data class Unavailable(val message: String) : EngineResult
    data class Failed(val message: String) : EngineResult
}

interface CloneEngine {
    val isAvailable: Boolean
    val availabilityMessage: String?
    suspend fun listManagedPackages(): List<String>
    suspend fun cloneApp(packageName: String, onProgress: (InstallProgress) -> Unit): EngineResult
    suspend fun freeze(packageName: String): EngineResult
    suspend fun unfreeze(packageName: String): EngineResult
    suspend fun hide(packageName: String): EngineResult
    suspend fun unhide(packageName: String): EngineResult
    suspend fun remove(packageName: String): EngineResult
    suspend fun open(packageName: String): EngineResult
}
