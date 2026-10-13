package com.kuro.app.engine

class UnavailableCloneEngine(
    override val availabilityMessage: String
) : CloneEngine {
    override val isAvailable: Boolean = false
    private fun unavailable() = EngineResult.Unavailable(availabilityMessage)

    override suspend fun listManagedPackages(): List<String> = emptyList()
    override suspend fun cloneApp(packageName: String, onProgress: (InstallProgress) -> Unit): EngineResult = unavailable()
    override suspend fun freeze(packageName: String): EngineResult = unavailable()
    override suspend fun unfreeze(packageName: String): EngineResult = unavailable()
    override suspend fun hide(packageName: String): EngineResult = unavailable()
    override suspend fun unhide(packageName: String): EngineResult = unavailable()
    override suspend fun remove(packageName: String): EngineResult = unavailable()
    override suspend fun open(packageName: String): EngineResult = unavailable()
}
