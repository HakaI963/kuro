package com.kuro.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DevicesFold
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KuroSystemTab(
    onOpenTerminalTab: () -> Unit = {},
    onOpenDistroCatalog: () -> Unit = {},
    onOpenClonesTab: () -> Unit = {},
    onOpenSettingsTab: () -> Unit = {},
    onNavigateToInstall: (com.kuro.app.core.data.Distro) -> Unit = {},
    onOpenTerminal: (String, Boolean) -> Unit = { _, _ -> },
    engineReady: Boolean? = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "kuro-home")
    val morph by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(4000), repeatMode = RepeatMode.Reverse),
        label = "bg"
    )
    val bg = lerp(Color(0xFF0A0E14), Color(0xFF151D2A), morph)
    Box(modifier = modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Icon(
                imageVector = Icons.Rounded.DevicesFold,
                contentDescription = "Kuro",
                modifier = Modifier.size(80.dp).graphicsLayer { alpha = 0.9f },
                tint = Color(0xFF7C6CFF)
            )
            Text("Kuro", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 8.dp))
            Text("Isolated Linux & App Spaces", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(40.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { DashboardCard(title = "Containers", icon = Icons.Rounded.Terminal, color = Color(0xFF49D6C2), onClick = onOpenTerminalTab) }
                item { DashboardCard(title = "App Clones", icon = Icons.Rounded.Apps, color = Color(0xFF5DB2FF), onClick = onOpenClonesTab) }
                item { DashboardCard(title = "Settings", icon = Icons.Rounded.Settings, color = Color(0xFFE0E0E0), onClick = onOpenSettingsTab) }
                item { DashboardCard(title = "System Info", icon = Icons.Rounded.Info, color = Color(0xFFFF6B6B), onClick = onOpenSettingsTab) }
            }
            Spacer(modifier = Modifier.weight(1f))
            val (statusText, statusColor) = when (engineReady) {
                true -> "Engine Status: Ready" to Color(0xFF3DDC97)
                false -> "Engine Status: Unavailable" to Color(0xFFFF6B6B)
                null -> "Engine Status: Setting up..." to Color(0xFFE0A93E)
            }
            Text(statusText, color = statusColor, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DashboardCard(title: String, icon: ImageVector, color: Color, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().height(140.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2534)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}
