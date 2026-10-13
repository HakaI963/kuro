package com.kuro.app.engine

import android.content.Context
import com.kuro.engine.blackbox.BlackBoxBootstrap

object EngineBootstrap {
    fun attach(base: Context) = BlackBoxBootstrap.attach(base)
    fun create() = BlackBoxBootstrap.create()
}
