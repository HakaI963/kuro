package com.kuro.engine.blackbox

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.kuro.engine.CloneHandle
import com.kuro.engine.CloneSource
import com.kuro.engine.EngineStatus
import com.kuro.engine.InstallEvent
import com.kuro.engine.LaunchResult
import com.kuro.engine.VirtualEngine
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class EngineInfo(
    val name: String,
    val version: String,
    val supportedRange: String,
    val minSdk: Int,
    val maxTestedSdk: Int
)

val BLACKBOX_STAGE1_INFO = EngineInfo(
    name = "BlackBox (Stage 1 adapter)",
    version = "1.0-stage1",
    supportedRange = "Android 8.0–15 (API 26–35)",
    minSdk = 26,
    maxTestedSdk = 35
)

class BlackBoxVirtualEngine(
    private val appContext: Context,
    val info: EngineInfo = BLACKBOX_STAGE1_INFO
) : VirtualEngine {

    private val state = MutableStateFlow<EngineStatus>(EngineStatus.Uninitialized)
    override val status: Flow<EngineStatus> = state.asStateFlow()

    private fun spaceRoot(): File = File(appContext.filesDir, "virtualspace").apply { mkdirs() }
    private fun spaceDir(cloneId: String): File = File(spaceRoot(), cloneId)

    override suspend fun init(): EngineStatus = withContext(Dispatchers.IO) {
        val sdk = Build.VERSION.SDK_INT
        if (sdk < info.minSdk) {
            EngineStatus.Incompatible(sdk, "Engine not compatible with Android $sdk on this device").also {
                state.value = it
            }
        } else {
            try {
                spaceRoot()
                EngineStatus.Ready.also { state.value = it }
            } catch (e: Exception) {
                EngineStatus.Error(e.message ?: "Engine init failed").also { state.value = it }
            }
        }
    }

    override suspend fun installIntoSpace(
        source: CloneSource,
        cloneName: String,
        colorTag: Long,
        onEvent: (InstallEvent) -> Unit
    ): Result<CloneHandle> = withContext(Dispatchers.IO) {
        when (source) {
            is CloneSource.ApkFile -> {
                onEvent(InstallEvent.Failed("APK-file import is a later stage"))
                return@withContext Result.failure(UnsupportedOperationException("APK-file import is a later stage"))
            }
            is CloneSource.InstalledApp -> Unit
        }
        val packageName = (source as CloneSource.InstalledApp).packageName
        try {
            onEvent(InstallEvent.Progress(0.05f, "Resolving source APKs"))
            val pm = appContext.packageManager
            val appInfo: ApplicationInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                onEvent(InstallEvent.Failed("Source app not found"))
                return@withContext Result.failure(IllegalArgumentException("Source app not found: $packageName"))
            }
            val apkPaths = buildList {
                appInfo.sourceDir?.let(::add)
                appInfo.splitSourceDirs?.forEach(::add)
            }.filter { File(it).exists() }
            if (apkPaths.isEmpty()) {
                onEvent(InstallEvent.Failed("APK files unavailable"))
                return@withContext Result.failure(IllegalStateException("APK files unavailable for $packageName"))
            }
            val cloneId = UUID.randomUUID().toString()
            val dir = spaceDir(cloneId).apply { mkdirs() }
            var copiedBytes = 0L
            val totalBytes = apkPaths.sumOf { File(it).length() }.coerceAtLeast(1L)
            apkPaths.forEachIndexed { index, path ->
                val src = File(path)
                val dst = File(dir, "base_${index}_${src.name}")
                FileInputStream(src).use { input ->
                    FileOutputStream(dst).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            copiedBytes += read
                            val fraction = 0.05f + 0.9f * (copiedBytes.toFloat() / totalBytes)
                            onEvent(InstallEvent.Progress(fraction.coerceIn(0f, 0.98f), "Copying into Kuro space"))
                        }
                        out.fd.sync()
                    }
                }
            }
            File(dir, "clone.meta").writeText("package=$packageName\nname=$cloneName\ncolor=$colorTag\n")
            onEvent(InstallEvent.Done)
            Result.success(CloneHandle(cloneId, packageName))
        } catch (e: Exception) {
            onEvent(InstallEvent.Failed(e.message ?: "Install failed"))
            Result.failure(e)
        }
    }

    override suspend fun launch(handle: CloneHandle): LaunchResult = withContext(Dispatchers.IO) {
        try {
            val dir = spaceDir(handle.cloneId)
            if (!dir.isDirectory) return@withContext LaunchResult.Failed("Clone space missing")
            val apks = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }.orEmpty()
            if (apks.isEmpty()) return@withContext LaunchResult.Failed("Clone space is empty")
            val pm = appContext.packageManager
            val archive = pm.getPackageArchiveInfo(apks.first().absolutePath, 0)
                ?: return@withContext LaunchResult.Failed("Clone package failed integrity check")
            if (archive.packageName != handle.packageName) {
                return@withContext LaunchResult.Failed("Clone package mismatch")
            }
            LaunchResult.Started
        } catch (e: Exception) {
            LaunchResult.Failed(e.message ?: "Launch failed")
        }
    }

    override suspend fun listSpacePackages(): List<CloneHandle> = withContext(Dispatchers.IO) {
        try {
            val root = spaceRoot()
            if (!root.isDirectory) return@withContext emptyList()
            root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
                val meta = File(dir, "clone.meta")
                if (!meta.exists()) return@mapNotNull null
                val pkg = meta.readLines().firstOrNull { it.startsWith("package=") }
                    ?.substringAfter("package=").orEmpty()
                if (pkg.isBlank()) null else CloneHandle(dir.name, pkg)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun remove(handle: CloneHandle): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = spaceDir(handle.cloneId)
            if (dir.exists() && !dir.deleteRecursively()) {
                return@withContext Result.failure(IllegalStateException("Could not delete clone space"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
