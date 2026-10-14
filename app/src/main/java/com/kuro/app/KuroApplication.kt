package com.kuro.app

import android.app.Application
import android.content.Context
import com.kuro.app.core.crash.CrashLog
import com.kuro.app.engine.EngineBootstrap

class KuroApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (base != null) {
            try {
                EngineBootstrap.attach(base)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Step 4b: install first so every later crash (engine init included)
        // is appended to filesDir/crash.log before the system closes the app.
        runCatching { CrashLog.install(filesDir) }
        try {
            EngineBootstrap.create()
        } catch (_: Exception) {
        }
        com.kuro.app.di.ServiceLocator.init(this)
    }
}
