package com.kuro.app.engine

import android.content.Context
import android.os.Build
import com.kuro.engine.EngineStatus
import com.kuro.engine.NoOpEngine
import com.kuro.engine.VirtualEngine

object EngineProvider {
    fun provide(@Suppress("UNUSED_PARAMETER") context: Context): VirtualEngine =
        NoOpEngine(
            reason = "Stub build: virtual engine backend not included",
            fixedStatus = EngineStatus.Incompatible(
                Build.VERSION.SDK_INT,
                "Stub build: virtual engine backend not included"
            )
        )
}
