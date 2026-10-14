package com.kuro.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.kuro.app.core.terminal.TermuxHostPaths
import com.kuro.app.core.crash.CrashLog
import com.kuro.app.di.ServiceLocator
import com.kuro.app.engine.EngineBootstrap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        // Step 8: silent first-launch init (no wizard). Fast path returns in ms
        // when already set up; UI shows "Setting up..." only while WORKING.
        backgroundInit(this)
    }

    private val initExecutor = Executors.newSingleThreadExecutor()
    private fun backgroundInit(app: FluxApp) {
        if (KuroInit.state.value != KuroInit.InitState.IDLE) return
        KuroInit.set(KuroInit.InitState.WORKING)
        initExecutor.execute {
            try {
                // /data/local/kuro tree (root-only; best effort — proot mode works without it).
                runCatching {
                    com.kuro.app.core.root.RootShell.executeSync(
                        com.kuro.app.core.root.ChrootPaths.ENSURE_DIRS_CMD
                    )
                }
                // Embedded host bootstrap (fast path <5ms when already extracted).
                val ok = runCatching {
                    com.kuro.app.core.terminal.TerminalLauncher.prepareHostBlocking(app)
                }.getOrDefault(false)
                KuroInit.set(if (ok) KuroInit.InitState.READY else KuroInit.InitState.FAILED)
            } catch (_: Exception) {
                KuroInit.set(KuroInit.InitState.FAILED)
            }
        }
    }
}

object KuroInit {
    enum class InitState { IDLE, WORKING, READY, FAILED }
    private val _state = MutableStateFlow(InitState.IDLE)
    val state: StateFlow<InitState> = _state.asStateFlow()
    internal fun set(s: InitState) { _state.value = s }
}
