package com.kuro.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuro.app.core.data.DistroRepository
import com.kuro.app.core.data.Distro
import com.kuro.app.core.data.ScriptManager
import com.kuro.app.core.utils.InstallationQueueManager
import com.kuro.app.core.utils.StateManager
import com.kuro.app.ui.components.DistroCard
import com.kuro.app.ui.components.MethodTab
import com.kuro.app.ui.components.MethodTabs
import com.kuro.app.ui.theme.FluxAccentMagenta
import com.kuro.app.ui.theme.GlassWhiteMedium
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import dev.chrisbanes.haze.HazeState

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DistroScreen(
    permissionState: PermissionState,
    hazeState: HazeState,
    onStartService: (android.content.Intent) -> Unit,
    onStartActivity: (android.content.Intent) -> Unit,
    onNavigateToInstall: (com.kuro.app.core.data.Distro) -> Unit,
    onOpenSessions: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    val installState by InstallationQueueManager.installState.collectAsState()
    val stateRefresh by StateManager.refreshTrigger.collectAsState()
    
    // Refresh mechanism to check install status
    val refreshKey = remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshKey.value++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Distro List — installed state = filesystem truth (plan P4-T13); stale
    // prefs without a rootfs on disk keep the card available for Install.
    val installedDistroIds = remember(refreshKey.value, stateRefresh) {
        DistroRepository.supportedDistros
            .filter { com.kuro.app.core.terminal.TerminalLauncher.isDistroInstalledOnFs(context, it.id) }
            .map { it.id }
            .toSet()
    }
    
    val availableDistros = DistroRepository.supportedDistros.filter { 
        !installedDistroIds.contains(it.id)
    }

    var methodTab by remember { mutableStateOf(MethodTab.PROOT) }
    val visibleDistros = DistroRepository.sortForDistroPage(
        availableDistros.filter { distro ->
            if (methodTab == MethodTab.CHROOT) distro.chrootSupported else distro.prootSupported
        }
    )
    val prootCount = availableDistros.count { it.prootSupported }
    val chrootCount = availableDistros.count { it.chrootSupported }

    // Step 7: catalog grid — 2 columns, cards show logo, name and size.
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Available Distros",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                if (onOpenSessions != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onOpenSessions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(text = "Open terminal sessions")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MethodTabs(
                    selected = methodTab,
                    onSelected = { methodTab = it },
                    prootCount = prootCount,
                    chrootCount = chrootCount
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (visibleDistros.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (availableDistros.isEmpty()) {
                            "All available distros are installed!"
                        } else if (methodTab == MethodTab.CHROOT) {
                            "No chroot distros left to install"
                        } else {
                            "No PRoot distros left to install"
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            items(
                items = visibleDistros,
                key = { it.id },
                contentType = { if (it.comingSoon) "soon" else "full" }
            ) { distro ->
                if (distro.comingSoon) {
                    com.kuro.app.ui.components.CompactDistroCard(
                        distro = distro
                    )
                } else {
                    com.kuro.app.ui.components.DistroCard(
                        distro = distro,
                        isInstalled = false,
                        isGlobalInstalling = installState.isInstalling,
                        isCurrentlyInstalling = installState.isInstalling && installState.currentDistroId == distro.id,
                        onInstall = {
                            if (installState.isInstalling && installState.currentDistroId != distro.id) {
                                Toast.makeText(context, "Installation already in progress for another distro", Toast.LENGTH_LONG).show()
                            } else {
                                onNavigateToInstall(distro)
                            }
                        },
                        onUninstall = {},
                        onNavigateToSettings = {},
                        onNavigateToStart = {}
                    )
                }
            }
        }
    }
}
