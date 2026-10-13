package com.kuro.app

import android.app.Application

class KuroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.kuro.app.di.ServiceLocator.init(this)
    }
}
