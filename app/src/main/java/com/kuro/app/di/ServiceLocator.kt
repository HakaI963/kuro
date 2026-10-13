package com.kuro.app.di

import android.content.Context
import androidx.room.Room
import com.kuro.app.data.clones.RoomActivityRepository
import com.kuro.app.data.clones.RoomCloneRepository
import com.kuro.app.data.installed.RealInstalledAppRepository
import com.kuro.app.data.local.KuroDatabase
import com.kuro.app.data.settings.InMemorySettingsRepository
import com.kuro.app.domain.repositories.ActivityRepository
import com.kuro.app.domain.repositories.CloneRepository
import com.kuro.app.domain.repositories.InstalledAppRepository
import com.kuro.app.domain.repositories.SettingsRepository
import com.kuro.app.engine.EngineProvider
import com.kuro.engine.VirtualEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ServiceLocator {
    lateinit var installedAppRepository: InstalledAppRepository
        private set
    lateinit var cloneRepository: CloneRepository
        private set
    lateinit var activityRepository: ActivityRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var database: KuroDatabase
        private set
    lateinit var virtualEngine: VirtualEngine
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _engineStatus = MutableStateFlow<com.kuro.engine.EngineStatus>(
        com.kuro.engine.EngineStatus.Uninitialized
    )
    val engineStatus: StateFlow<com.kuro.engine.EngineStatus> = _engineStatus.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        database = Room.databaseBuilder(app, KuroDatabase::class.java, "kuro.db")
            .fallbackToDestructiveMigration()
            .build()
        installedAppRepository = RealInstalledAppRepository(app)
        cloneRepository = RoomCloneRepository(database, app, engineName = "blackbox-stage1")
        activityRepository = RoomActivityRepository(database)
        settingsRepository = InMemorySettingsRepository()
        virtualEngine = EngineProvider.provide(app)
        initialized = true
        scope.launch(Dispatchers.IO) {
            virtualEngine.status.collect { _engineStatus.value = it }
        }
        scope.launch(Dispatchers.IO) {
            _engineStatus.value = virtualEngine.init()
        }
    }

    fun initForPreview() {
        if (initialized) return
    }
}
