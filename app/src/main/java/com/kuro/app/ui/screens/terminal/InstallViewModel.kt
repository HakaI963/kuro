package com.kuro.app.ui.screens.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuro.app.core.system.StoragePaths
import com.kuro.app.core.system.SystemMetricsReader
import com.kuro.app.data.containers.ContainerRecord
import com.kuro.app.data.containers.ContainerRepository
import com.kuro.app.data.containers.ContainerStore
import com.kuro.app.data.containers.ContainerType
import com.kuro.app.data.containers.LinuxContainer
import com.kuro.app.di.ServiceLocator
import com.kuro.app.engine.proot.ContainerProvisioner
import com.kuro.app.engine.proot.InstallSpec
import com.kuro.app.engine.proot.InstallStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 5: owns one wizard install run. The heavy work lives in
 * ContainerProvisioner (app scope + foreground service), so leaving this
 * screen never kills the download — the Terminal tab observes the same
 * stages. Completion (Room + store) is claimed exactly once.
 */
class InstallViewModel : ViewModel() {
    val stages: StateFlow<Map<String, InstallStage>> = ContainerProvisioner.stages

    fun start(context: Context, spec: InstallSpec) {
        ContainerProvisioner.start(context, spec)
    }

    fun cancel(installId: String) = ContainerProvisioner.cancel(installId)

    fun retry(context: Context, installId: String) =
        ContainerProvisioner.retry(context, installId)

    fun forget(installId: String) = ContainerProvisioner.forget(installId)

    fun completeOnce(context: Context, installId: String, spec: InstallSpec) {
        viewModelScope.launch {
            ContainerProvisioner.claimReady(installId) ?: return@launch
            val app = context.applicationContext
            val distroName = DistroCatalog.displayNameOf(spec.distroId)
            withContext(Dispatchers.IO) {
                runCatching {
                    ContainerRepository(ServiceLocator.database).save(
                        ContainerRecord(
                            id = spec.containerId,
                            name = spec.containerName,
                            distroId = spec.distroId,
                            type = ContainerType.PROOT.name,
                            shell = spec.shell,
                            bridgeEnabled = spec.bridgeEnabled,
                            keepAlive = spec.keepAlive,
                            wakeLock = spec.wakeLock,
                            interfaceMode = spec.interfaceMode,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                }
                val size = SystemMetricsReader.dirSizeNow(
                    StoragePaths.prootRootfsDir(app.filesDir, spec.containerId)
                ) ?: 0L
                ContainerStore.add(
                    LinuxContainer(
                        id = spec.containerId,
                        name = spec.containerName,
                        distroId = spec.distroId,
                        distroName = distroName,
                        type = ContainerType.PROOT,
                        status = com.kuro.app.data.containers.ContainerStatus.STOPPED,
                        sizeBytes = size,
                        withGui = spec.interfaceMode != "TUI",
                        bridgeEnabled = spec.bridgeEnabled,
                        interfaceMode = spec.interfaceMode,
                        wakeLock = spec.wakeLock
                    )
                )
            }
        }
    }
}
