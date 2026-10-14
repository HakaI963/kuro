package com.kuro.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.SdStorage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.kuro.app.R
import com.kuro.app.core.system.SharedStorageAccess
import com.kuro.app.core.utils.ThemeMode
import com.kuro.app.ui.components.GlassSettingCard

/**
 * Settings hub (nativecode-style): nav cards open detail pages.
 * Legacy Termux leftover manager on its own page; connection-fix / Prerequisites cards removed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    permissionState: PermissionState,
    onStartService: (Intent) -> Unit,
    onStartActivity: (Intent) -> Unit,
    onNavigateToOnboarding: (() -> Unit)? = null,
    onNavigateToTroubleshooting: (() -> Unit)? = null,
    onNavigateToRootCheck: (() -> Unit)? = null,
    onNavigateToTerminalSettings: (() -> Unit)? = null,
    onNavigateToX11Settings: (() -> Unit)? = null,
    onNavigateToAudioSettings: (() -> Unit)? = null,
    onNavigateToChrootSettings: (() -> Unit)? = null,
    onNavigateToProotSettings: (() -> Unit)? = null,
    onNavigateToLegacyTermuxSettings: (() -> Unit)? = null,
    onThemeChanged: ((ThemeMode) -> Unit)? = null,
    currentTheme: ThemeMode = ThemeMode.SYSTEM
) {
    // Keep unused params so call sites stay stable (permission / theme hooks).
    @Suppress("UNUSED_PARAMETER")
    val _permissionState = permissionState
    @Suppress("UNUSED_PARAMETER")
    val _onStartService = onStartService
    @Suppress("UNUSED_PARAMETER")
    val _onStartActivity = onStartActivity


    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "App",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            // The manifest declares the all-files permission (Android 11+).
            if (SharedStorageAccess.declared(context)) {
                var lifecycleKey by remember { mutableIntStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, e ->
                        if (e == Lifecycle.Event.ON_RESUME) lifecycleKey++
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                val granted = remember(lifecycleKey) { SharedStorageAccess.granted() }
                SettingsNavCard(
                    icon = Icons.Default.SdStorage,
                    title = "Shared storage",
                    subtitle = if (granted) "Allowed: /sdcard and /storage (SD, USB OTG) are mounted in guests"
                    else "Not allowed: guests cannot see /sdcard or USB drives. Tap, then enable All files access",
                    onClick = { SharedStorageAccess.openSettings(context) }
                )
            }
            if (com.kuro.app.core.system.PhantomProcessFixer.applicable()) {
                var showPhantom by remember { mutableStateOf(false) }
                val phantomState = com.kuro.app.core.system.PhantomProcessFixer.state(context)
                SettingsNavCard(
                    icon = Icons.Default.Warning,
                    title = "Process killer fix",
                    subtitle = "Phantom process killer: " + when (phantomState) {
                        com.kuro.app.core.system.PhantomProcessFixer.State.DISABLED -> "Disabled"
                        com.kuro.app.core.system.PhantomProcessFixer.State.ENABLED -> "Enabled. Tap to fix with Root, Shizuku or adb"
                        else -> "Unknown. Tap to fix with Root, Shizuku or adb"
                    },
                    onClick = { showPhantom = true }
                )
                if (showPhantom) {
                    com.kuro.app.ui.components.PhantomProcessDialog(warning = false) { showPhantom = false }
                }
            }
            SettingsNavCard(
                icon = Icons.Default.Terminal,
                title = "Terminal",
                subtitle = "Font zoom, extra keys, and guest shell",
                onClick = { onNavigateToTerminalSettings?.invoke() }
            )
            SettingsNavCard(
                icon = Icons.Default.DisplaySettings,
                title = "X11 Display",
                subtitle = "Scale, fullscreen, input for embedded X11",
                onClick = { onNavigateToX11Settings?.invoke() }
            )
            SettingsNavCard(
                icon = Icons.Default.VolumeUp,
                title = "Audio",
                subtitle = "Host PulseAudio status, start, restart, logs",
                onClick = { onNavigateToAudioSettings?.invoke() }
            )
            SettingsNavCard(
                icon = Icons.Default.Storage,
                title = "Chroot",
                subtitle = "Installed roots, size, kill orphan processes",
                onClick = { onNavigateToChrootSettings?.invoke() }
            )
            SettingsNavCard(
                icon = Icons.Default.Folder,
                title = "PRoot",
                subtitle = "Installed containers and app-storage size",
                onClick = { onNavigateToProotSettings?.invoke() }
            )
            SettingsNavCard(
                icon = Icons.Default.History,
                title = "Legacy Termux",
                subtitle = "Leftover PRoot installs from Kuro ≤ v1.8.0 live in the Termux app",
                onClick = { onNavigateToLegacyTermuxSettings?.invoke() }
            )
            var showThemePicker by remember { mutableStateOf(false) }
            SettingsNavCard(
                icon = Icons.Default.Palette,
                title = "Theme",
                subtitle = "Appearance: " + when (currentTheme) {
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                    else -> "System"
                },
                onClick = { showThemePicker = true }
            )
            if (showThemePicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showThemePicker = false },
                    title = { Text("Theme") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                ThemeMode.SYSTEM to "System",
                                ThemeMode.LIGHT to "Light",
                                ThemeMode.DARK to "Dark"
                            ).forEach { (mode, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onThemeChanged?.invoke(mode) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = currentTheme == mode,
                                        onClick = { onThemeChanged?.invoke(mode) }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, fontSize = 16.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showThemePicker = false }) {
                            Text("Done")
                        }
                    }
                )
            }
            SettingsNavCard(
                icon = Icons.Default.Security,
                title = "Root access",
                subtitle = "Chroot status, su check, rooted helpers",
                onClick = { onNavigateToRootCheck?.invoke() }
            )

            Text(
                "Help",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            GlassSettingCard {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onNavigateToOnboarding != null) {
                        // secondary = cream in dark theme — primary is near-black and vanishes on glass
                        Button(
                            onClick = { onNavigateToOnboarding() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Show onboarding", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onNavigateToTroubleshooting != null) {
                        Button(
                            onClick = { onNavigateToTroubleshooting() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.secondary
                            ),
                            border = BorderStroke(
                                1.5.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Troubleshoot", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // About
            GlassSettingCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "Kuro Logo",
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Kuro",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "v${com.kuro.app.BuildConfig.VERSION_NAME} • embedded terminal & X11",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    // Dark theme primary is near-black (filled surfaces); use secondary (cream) for icons.
    val iconTint = MaterialTheme.colorScheme.secondary
    val titleColor = MaterialTheme.colorScheme.onBackground
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chevronColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)

    GlassSettingCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = subtitleColor
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = chevronColor
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Log.e("Settings", "Error opening URL", e)
    }
}
