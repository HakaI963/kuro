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
import com.kuro.app.engine.CloneEngine
import com.kuro.app.engine.ProvisioningManager
import com.kuro.app.engine.UnavailableCloneEngine
import com.kuro.app.engine.WorkProfileCloneEngine
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _engine = MutableStateFlow<CloneEngine>(
        UnavailableCloneEngine("Work profile not set up")
    )
    val engine: StateFlow<CloneEngine> = _engine.asStateFlow()

    private var initialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        appContext = app
        database = Room.databaseBuilder(app, KuroDatabase::class.java, "kuro.db")
            .fallbackToDestructiveMigration()
            .build()
        installedAppRepository = RealInstalledAppRepository(app)
        cloneRepository = RoomCloneRepository(database, app)
        activityRepository = RoomActivityRepository(database)
        settingsRepository = InMemorySettingsRepository()
        initialized = true
        refreshEngine()
    }

    fun refreshEngine() {
        val ctx = appContext ?: return
        scope.launch(Dispatchers.IO) {
            val state = ProvisioningManager.currentState(ctx)
            _engine.value = when (state) {
                is com.kuro.app.domain.models.ProvisioningState.Active ->
                    WorkProfileCloneEngine(ctx)
                is com.kuro.app.domain.models.ProvisioningState.NotSetup ->
                    UnavailableCloneEngine("Work profile not set up. Open Settings to provision an isolated space (work profile).")
                is com.kuro.app.domain.models.ProvisioningState.ProfileExistsNotOwner ->
                    UnavailableCloneEngine("A work profile already exists but is owned by another app. Only one work profile is allowed per device.")
                is com.kuro.app.domain.models.ProvisioningState.Blocked ->
                    UnavailableCloneEngine("Work profile unavailable: ${state.reason}. See Settings for details.")
                else ->
                    UnavailableCloneEngine("Checking work profile status…")
            }
        }
    }

    fun initForPreview() {
        if (initialized) return
        // Preview only: no Context available. Unit tests construct repositories directly.
    }
}
