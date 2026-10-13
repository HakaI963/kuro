package com.kuro.engine.blackbox

import android.content.Context
import android.os.Build
import com.kuro.engine.CloneHandle
import com.kuro.engine.CloneSource
import com.kuro.engine.EngineStatus
import com.kuro.engine.InstallEvent
import com.kuro.engine.LaunchResult
import com.kuro.engine.VirtualEngine
import com.kuro.engine.allocateUserSlot
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore

const val BLACKBOX_UPSTREAM_COMMIT = "89b59836c66f173756a4ae258cf379a957649820"
const val BLACKBOX_ENGINE_VERSION = "4.0.0"
const val BLACKBOX_SUPPORTED_RANGE = "Android 5.0–14.0+ (API 21+)"

data class EngineSpace(
    val userId: Int,
    val packages: List<String>
)

data class EngineDiagnostics(
    val implClass: String,
    val commit: String,
    val nativeLoaded: Boolean,
    val spaces: List<EngineSpace>,
    val spaceDir: String,
    val spaceBytes: Long,
    val lastError: String?
)

class BlackBoxEngine(
    private val appContext: Context
) : VirtualEngine {

    private val state = MutableStateFlow<EngineStatus>(EngineStatus.Uninitialized)
    override val status: Flow<EngineStatus> = state.asStateFlow()

    @Volatile private var lastError: String? = null

    private fun recordError(message: String) {
        lastError = message
    }

    fun isNativeLoaded(): Boolean = try {
        System.loadLibrary("blackbox")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: Exception) {
        false
    }

    private fun spaceRoot(): File =
        File(appContext.cacheDir.parentFile, "blackbox")

    fun spaceSizeBytes(): Long = try {
        spaceRoot().walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }

    override suspend fun init(): EngineStatus = withContext(Dispatchers.IO) {
        val sdk = Build.VERSION.SDK_INT
        if (sdk < 21) {
            EngineStatus.Incompatible(sdk, "Engine not compatible with Android $sdk on this device")
                .also { state.value = it }
        } else if (!BlackBoxBootstrap.attachAttempted) {
            EngineStatus.Error("Engine attach did not run during app startup")
                .also { state.value = it }
        } else {
            BlackBoxBootstrap.attachError?.let { attachFailure ->
                recordError(attachFailure)
                EngineStatus.Error(attachFailure).also { state.value = it }
            } ?: try {
                BlackBoxCore.get()
                if (!isNativeLoaded()) {
                    recordError("Native library libblackbox failed to load")
                    EngineStatus.Error("Native library libblackbox failed to load")
                        .also { state.value = it }
                } else {
                    EngineStatus.Ready.also { state.value = it }
                }
            } catch (e: Throwable) {
                recordError(e.message ?: e.toString())
                EngineStatus.Error(e.message ?: e.toString()).also { state.value = it }
            }
        }
    }

    override suspend fun installIntoSpace(
        source: CloneSource,
        cloneName: String,
        colorTag: Long,
        onEvent: (InstallEvent) -> Unit
    ): Result<CloneHandle> = withContext(Dispatchers.IO) {
        if (source is CloneSource.ApkFile) {
            onEvent(InstallEvent.Failed("APK-file import is a later stage"))
            return@withContext Result.failure(
                UnsupportedOperationException("APK-file import is a later stage")
            )
        }
        val packageName = (source as CloneSource.InstalledApp).packageName
        try {
            onEvent(InstallEvent.Progress(0.05f, "Resolving source package"))
            val core = BlackBoxCore.get()
            val usedSlots = try {
                core.users.map { it.id }.toSet()
            } catch (e: Exception) {
                recordError(e.message ?: e.toString())
                onEvent(InstallEvent.Failed("Engine user query failed: ${e.message}"))
                return@withContext Result.failure(e)
            }
            val userId = allocateUserSlot(usedSlots)
            onEvent(InstallEvent.Progress(0.15f, "Creating engine user slot"))
            try {
                core.createUser(userId)
            } catch (e: Exception) {
                recordError(e.message ?: e.toString())
                onEvent(InstallEvent.Failed("Engine user creation failed: ${e.message}"))
                return@withContext Result.failure(e)
            }
            onEvent(InstallEvent.Progress(0.3f, "Installing into engine"))
            val install = try {
                core.installPackageAsUser(packageName, userId)
            } catch (e: Exception) {
                recordError(e.message ?: e.toString())
                onEvent(InstallEvent.Failed("Engine install threw: ${e.message}"))
                return@withContext Result.failure(e)
            }
            if (!install.success) {
                val msg = install.msg ?: "Engine install failed"
                recordError(msg)
                runCatching { core.deleteUser(userId) }
                onEvent(InstallEvent.Failed(msg))
                return@withContext Result.failure(IllegalStateException(msg))
            }
            onEvent(InstallEvent.Progress(0.85f, "Verifying install"))
            val verified = try {
                core.isInstalled(packageName, userId)
            } catch (e: Exception) {
                recordError(e.message ?: e.toString())
                false
            }
            if (!verified) {
                val msg = "Engine reports $packageName as not installed after install"
                recordError(msg)
                onEvent(InstallEvent.Failed(msg))
                return@withContext Result.failure(IllegalStateException(msg))
            }
            onEvent(InstallEvent.Done)
            Result.success(CloneHandle(cloneId = "u$userId", packageName = packageName, userId = userId))
        } catch (e: Exception) {
            recordError(e.message ?: e.toString())
            onEvent(InstallEvent.Failed(e.message ?: "Install failed"))
            Result.failure(e)
        }
    }

    override suspend fun launch(handle: CloneHandle): LaunchResult = withContext(Dispatchers.IO) {
        try {
            val started = BlackBoxCore.get().launchApk(handle.packageName, handle.userId)
            if (started) LaunchResult.Started
            else {
                val msg = "Engine refused to launch ${handle.packageName} (user ${handle.userId})"
                recordError(msg)
                LaunchResult.Failed(msg)
            }
        } catch (e: Exception) {
            recordError(e.message ?: e.toString())
            LaunchResult.Failed(e.message ?: "Launch failed")
        }
    }

    override suspend fun listSpacePackages(): List<CloneHandle> = withContext(Dispatchers.IO) {
        try {
            val core = BlackBoxCore.get()
            core.users.flatMap { user ->
                core.getInstalledPackages(0, user.id)
                    .map { CloneHandle(cloneId = "u${user.id}", packageName = it.packageName, userId = user.id) }
            }
        } catch (e: Exception) {
            recordError(e.message ?: e.toString())
            emptyList()
        }
    }

    override suspend fun remove(handle: CloneHandle): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val core = BlackBoxCore.get()
            core.uninstallPackageAsUser(handle.packageName, handle.userId)
            runCatching { core.deleteUser(handle.userId) }
            if (core.isInstalled(handle.packageName, handle.userId)) {
                val msg = "Engine still reports ${handle.packageName} as installed after remove"
                recordError(msg)
                Result.failure(IllegalStateException(msg))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            recordError(e.message ?: e.toString())
            Result.failure(e)
        }
    }

    suspend fun diagnostics(): EngineDiagnostics = withContext(Dispatchers.IO) {
        val spaces = try {
            val core = BlackBoxCore.get()
            core.users.map { user ->
                val pkgs = try {
                    core.getInstalledPackages(0, user.id).map { it.packageName }
                } catch (e: Exception) {
                    recordError(e.message ?: e.toString())
                    emptyList()
                }
                EngineSpace(user.id, pkgs)
            }
        } catch (e: Exception) {
            recordError(e.message ?: e.toString())
            emptyList()
        }
        EngineDiagnostics(
            implClass = "top.niunaijun.blackbox.BlackBoxCore (via BlackBoxEngine)",
            commit = BLACKBOX_UPSTREAM_COMMIT,
            nativeLoaded = isNativeLoaded(),
            spaces = spaces,
            spaceDir = spaceRoot().absolutePath,
            spaceBytes = spaceSizeBytes(),
            lastError = lastError
        )
    }
}
