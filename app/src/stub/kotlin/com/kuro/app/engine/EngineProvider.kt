package com.kuro.app.engine

import android.content.Context
import com.kuro.engine.NoOpEngine
import com.kuro.engine.VirtualEngine

object EngineProvider {
    fun provide(@Suppress("UNUSED_PARAMETER") context: Context): VirtualEngine =
        NoOpEngine("Stub build: virtual engine backend not included")
}
