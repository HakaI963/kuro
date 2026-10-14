package com.ivarna.fluxlinux

import android.app.Application
import android.content.Context
import android.util.Log
import com.ivarna.fluxlinux.core.terminal.TermuxHostPaths
import com.kuro.app.core.crash.CrashLog
import com.kuro.app.di.ServiceLocator
import com.kuro.app.engine.EngineBootstrap

class FluxApp : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // Kuro clone engine (BlackBox): attach in base context like the
        // engine's own sample app. Failures are captured, never crash.
        if (base != null) {
            try {
                EngineBootstrap.attach(base)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Crash log first so engine init failures are recorded.
        runCatching { CrashLog.install(filesDir) }
        try {
            EngineBootstrap.create()
        } catch (_: Exception) {
        }
        runCatching { ServiceLocator.init(this) }
        // nativeLibraryDir moves on every app update; fix host env before ANY host script runs.
        runCatching { TermuxHostPaths.refreshHostEnvIfStale(filesDir, this) }
            .onFailure { Log.w("FluxApp", "host env refresh failed", it) }
    }
}
