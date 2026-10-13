package com.kuro.app.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import kotlin.coroutines.resume

/**
 * Real work-profile clone engine. Must be executed by the Kuro instance that is
 * profile owner inside the managed profile. All operations use public Android APIs only:
 * PackageInstaller, DevicePolicyManager, PackageManager. No virtualization, no hooks,
 * no reflection, no root.
 */
class WorkProfileCloneEngine(
    private val context: Context
) : CloneEngine {

    override val isAvailable: Boolean = true
    override val availabilityMessage: String? = null

    private fun dpm() = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
    private fun admin() = ProvisioningManager.adminComponent(context)

    override suspend fun listManagedPackages(): List<String> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
            pkgs.map { it.packageName }.filter { it != context.packageName }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun cloneApp(packageName: String, onProgress: (InstallProgress) -> Unit): EngineResult =
        withContext(Dispatchers.IO) {
            try {
                onProgress(InstallProgress.Running(0.1f, "Resolving source APKs"))
                val pm = context.packageManager
                val appInfo = try {
                    pm.getApplicationInfo(packageName, 0)
                } catch (e: Exception) {
                    onProgress(InstallProgress.Failed("Source app not found"))
                    return@withContext EngineResult.Failed("Source app not found: ${e.message}")
                }
                val apkPaths = mutableListOf<String>().apply {
                    appInfo.sourceDir?.let(::add)
                    appInfo.splitSourceDirs?.let(::addAll)
                }.filter { File(it).exists() }
                if (apkPaths.isEmpty()) {
                    onProgress(InstallProgress.Failed("APK files unavailable"))
                    return@withContext EngineResult.Failed("APK files unavailable for $packageName")
                }
                onProgress(InstallProgress.Running(0.3f, "Opening install session"))
                val installer = pm.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = installer.createSession(params)
                try {
                    apkPaths.forEachIndexed { index, path ->
                        val file = File(path)
                        val name = "split_${index}_${file.name}"
                        installer.openSession(sessionId).use { session ->
                            session.openWrite(name, 0, file.length()).use { out: OutputStream ->
                                FileInputStream(file).use { input ->
                                    val buf = ByteArray(64 * 1024)
                                    var total = 0L
                                    while (true) {
                                        val r = input.read(buf)
                                        if (r < 0) break
                                        out.write(buf, 0, r)
                                        total += r
                                        val frac = 0.3f + 0.5f * ((index + total.toFloat() / file.length()) / apkPaths.size)
                                        onProgress(InstallProgress.Running(frac.coerceIn(0f, 0.9f), "Copying APKs"))
                                    }
                                }
                                session.fsync(out)
                            }
                        }
                    }
                    onProgress(InstallProgress.Running(0.9f, "Committing install"))
                    val committed = commitSession(installer, sessionId, packageName)
                    if (committed) {
                        onProgress(InstallProgress.Success)
                        EngineResult.Ok
                    } else {
                        onProgress(InstallProgress.Failed("Install requires confirmation"))
                        EngineResult.Failed("Install blocked or needs user confirmation inside the work profile")
                    }
                } catch (e: Exception) {
                    try { installer.abandonSession(sessionId) } catch (_: Exception) { }
                    onProgress(InstallProgress.Failed(e.message ?: "Install failed"))
                    EngineResult.Failed(e.message ?: "Install failed")
                }
            } catch (e: Exception) {
                onProgress(InstallProgress.Failed(e.message ?: "Install failed"))
                EngineResult.Failed(e.message ?: "Install failed")
            }
        }

    private suspend fun commitSession(
        installer: PackageInstaller,
        sessionId: Int,
        packageName: String
    ): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_RESULT + "." + sessionId
                putExtra(InstallResultReceiver.EXTRA_PACKAGE, packageName)
                putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            installer.openSession(sessionId).use { session ->
                session.commit(pi.intentSender)
            }
            InstallResultReceiver.await(sessionId) { success ->
                if (cont.isActive) cont.resume(success)
            }
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }

    override suspend fun freeze(packageName: String): EngineResult = withContext(Dispatchers.IO) {
        try {
            val ok = dpm()?.setPackagesSuspended(admin(), arrayOf(packageName), true) ?: arrayOf()
            if (ok.isEmpty()) EngineResult.Ok else EngineResult.Failed("System refused to suspend $packageName")
        } catch (e: SecurityException) {
            EngineResult.Failed("Not profile owner: ${e.message}")
        } catch (e: Exception) {
            EngineResult.Failed(e.message ?: "Suspend failed")
        }
    }

    override suspend fun unfreeze(packageName: String): EngineResult = withContext(Dispatchers.IO) {
        try {
            dpm()?.setPackagesSuspended(admin(), arrayOf(packageName), false)
            EngineResult.Ok
        } catch (e: Exception) {
            EngineResult.Failed(e.message ?: "Unsuspend failed")
        }
    }

    override suspend fun hide(packageName: String): EngineResult = withContext(Dispatchers.IO) {
        try {
            val ok = dpm()?.setApplicationHidden(admin(), packageName, true) ?: false
            if (ok) EngineResult.Ok else EngineResult.Failed("System refused to hide $packageName")
        } catch (e: Exception) {
            EngineResult.Failed(e.message ?: "Hide failed")
        }
    }

    override suspend fun unhide(packageName: String): EngineResult = withContext(Dispatchers.IO) {
        try {
            dpm()?.setApplicationHidden(admin(), packageName, false)
            EngineResult.Ok
        } catch (e: Exception) {
            EngineResult.Failed(e.message ?: "Unhide failed")
        }
    }

    override suspend fun remove(packageName: String): EngineResult = withContext(Dispatchers.IO) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            EngineResult.Failed("Uninstall requires user confirmation inside the work profile")
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            EngineResult.Ok
        } catch (e: Exception) {
            EngineResult.Failed(e.message ?: "Remove failed")
        }
    }

    override suspend fun open(packageName: String): EngineResult = withContext(Dispatchers.IO) {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return@withContext EngineResult.Failed("No launchable activity for $packageName")
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            EngineResult.Ok
        } catch (e: Exception) {
            EngineResult.Failed(e.message ?: "Open failed")
        }
    }

    fun badgedLabel(packageName: String): CharSequence {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getUserBadgedLabel(info.labelRes.let {
                pm.getText(packageName, it, info)
            } ?: pm.getApplicationLabel(info), android.os.Process.myUserHandle())
        } catch (_: Exception) {
            packageName
        }
    }
}
