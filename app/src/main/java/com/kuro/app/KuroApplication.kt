package com.kuro.app

import android.app.Application
import android.content.Context
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
        try {
            EngineBootstrap.create()
        } catch (_: Exception) {
        }
        com.kuro.app.di.ServiceLocator.init(this)
    }
}
