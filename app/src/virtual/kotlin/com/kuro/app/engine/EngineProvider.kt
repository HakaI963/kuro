package com.kuro.app.engine

import android.content.Context
import com.kuro.engine.VirtualEngine
import com.kuro.engine.blackbox.BlackBoxEngine

object EngineProvider {
    fun provide(context: Context): VirtualEngine =
        BlackBoxEngine(context.applicationContext)
}
