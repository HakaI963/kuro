package com.kuro.app.data.installed

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.kuro.app.domain.models.InstalledApp
import com.kuro.app.domain.repositories.InstalledAppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RealInstalledAppRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : InstalledAppRepository {

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val iconCache = object : LruCache<String, Drawable>(120) {}

    init {
        scope.launch { refresh() }
    }

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcher, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }
        val own = context.packageName
        val seen = LinkedHashMap<String, InstalledApp>()
        for (ri in resolveInfos) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == own) continue
            if (seen.containsKey(pkg)) continue
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo)?.toString() ?: pkg
                val versionName = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0)).versionName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0).versionName
                    }
                } catch (_: Exception) { null } ?: ""
                val versionCode = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0)).longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(pkg, 0).longVersionCode
                        }
                    } else 0L
                } catch (_: Exception) { 0L }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                seen[pkg] = InstalledApp(pkg, label, versionName, versionCode, isSystem)
            } catch (_: Exception) {
                continue
            }
        }
        _apps.value = seen.values.sortedBy { it.label.lowercase() }
    }

    override fun getIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        return try {
            val d = context.packageManager.getApplicationIcon(packageName)
            iconCache.put(packageName, d)
            d
        } catch (_: Exception) {
            null
        }
    }
}
