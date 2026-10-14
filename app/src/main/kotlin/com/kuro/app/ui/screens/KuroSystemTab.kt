package com.kuro.app.ui.screens

import android.os.StatFs
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.DevicesFold
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuro.app.core.data.Distro
import com.kuro.app.core.utils.RootUtils
import com.kuro.app.core.utils.ThemeMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class DotSpec(
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float,
    val speed: Float,
    val phase: Float,
    val drift: Float,
    val alpha: Float,
    val accent: Boolean
)

@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val dots = remember {
        val rnd = kotlin.random.Random(7)
        List(24) { i ->
            DotSpec(
                xFrac = rnd.nextFloat(),
                yFrac = rnd.nextFloat(),
                radiusDp = 1.5f + rnd.nextFloat() * 3f,
                speed = 0.5f + rnd.nextFloat(),
                phase = rnd.nextFloat() * (2f * PI.toFloat()),
                drift = 0.3f + rnd.nextFloat() * 0.7f,
                alpha = 0.10f + rnd.nextFloat() * 0.08f,
                accent = i % 2 == 0
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "bg-dots")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color(0xFF0A0E14))
            val driftPx = 30.dp.toPx()
            val twoPi = 2f * PI.toFloat()
            dots.forEach { dot ->
                val angle = dot.phase + progress * twoPi * dot.speed
                val cx = size.width * dot.xFrac + driftPx * dot.drift * cos(angle)
                val cy = size.height * dot.yFrac + driftPx * dot.drift * sin(angle)
                val base = if (dot.accent) Color(0xFF6366F1) else Color(0xFF94A3B8)
                val a = if (dot.accent) dot.alpha else dot.alpha * 0.8f
                drawCircle(
                    color = base.copy(alpha = a),
                    radius = dot.radiusDp.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

@Composable
fun KuroLogoAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "logo")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    Box(
        modifier = modifier
            .size(120.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color(0xFF6366F1).copy(alpha = 0.8f),
                startAngle = rotation,
                sweepAngle = 120f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
        }
        Icon(
            imageVector = Icons.Rounded.DevicesFold,
            contentDescription = "Kuro",
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF6366F1).copy(alpha = 0.8f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KuroHomeScreen(
    onOpenTerminalTab: () -> Unit = {},
    onOpenDistroCatalog: () -> Unit = {},
    onOpenClonesTab: () -> Unit = {},
    onOpenSettingsTab: () -> Unit = {},
    onNavigateToInstall: (Distro) -> Unit = {},
    onOpenTerminal: (String, Boolean) -> Unit = { _, _ -> },
    engineReady: Boolean? = true,
    onThemeChanged: ((ThemeMode) -> Unit)? = null,
    currentTheme: ThemeMode = ThemeMode.SYSTEM,
    modifier: Modifier = Modifier
) {
    var showWhatIs by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    var rooted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        rooted = withContext(Dispatchers.IO) {
            runCatching { RootUtils.isRootAvailable() }.getOrDefault(false)
        }
    }
    val storageText = remember {
        try {
            val sf = StatFs(context.filesDir.path)
            val free = sf.availableBlocksLong * sf.blockSizeLong
            val total = sf.blockCountLong * sf.blockSizeLong
            "%.1f GB free of %.1f GB".format(free / 1073741824.0, total / 1073741824.0)
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    val ctaInteraction = remember { MutableInteractionSource() }
    val ctaPressed by ctaInteraction.collectIsPressedAsState()
    val ctaScale by animateFloatAsState(
        targetValue = if (ctaPressed) 0.95f else 1f,
        animationSpec = spring(),
        label = "cta-press"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E14))
    ) {
        AnimatedBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            KuroLogoAnimation()
            Text(
                "Kuro",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF8FAFC)
            )
            Text(
                "Isolated Linux & App Spaces",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showWhatIs = true },
                interactionSource = ctaInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 280.dp)
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = ctaScale
                        scaleY = ctaScale
                    },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                )
            ) {
                Text(
                    "Explore Kuro",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.height(312.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false
            ) {
                item {
                    DashboardCard(
                        title = "Get Distro",
                        icon = Icons.Rounded.Download,
                        color = Color(0xFF10B981),
                        onClick = onOpenDistroCatalog
                    )
                }
                item {
                    DashboardCard(
                        title = "My Containers",
                        icon = Icons.Rounded.Terminal,
                        color = Color(0xFF6366F1),
                        onClick = onOpenTerminalTab
                    )
                }
                item {
                    DashboardCard(
                        title = "App Clones",
                        icon = Icons.Rounded.Apps,
                        color = Color(0xFFF59E0B),
                        onClick = onOpenClonesTab
                    )
                }
                item {
                    DashboardCard(
                        title = "Tools",
                        icon = Icons.Rounded.Settings,
                        color = Color(0xFF94A3B8),
                        onClick = onOpenSettingsTab
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val (statusText, statusColor) = when (engineReady) {
                true -> "Engine Status: Ready" to Color(0xFF10B981)
                null -> "Engine Status: Setting up..." to Color(0xFFF59E0B)
                false -> "Engine Status: Unavailable" to Color(0xFF94A3B8)
            }
            Text(statusText, color = statusColor, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(48.dp))
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(28.dp)
                .clickable { showSheet = true },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(2.dp))
            )
        }
    }

    if (showWhatIs) {
        val engineLine = when (engineReady) {
            true -> "Engine: Ready"
            null -> "Engine: Setting up..."
            false -> "Engine: Unavailable"
        }
        AlertDialog(
            onDismissRequest = { showWhatIs = false },
            confirmButton = {
                TextButton(onClick = { showWhatIs = false }) {
                    Text("Close", color = Color(0xFF6366F1))
                }
            },
            containerColor = Color(0xFF151D2A),
            titleContentColor = Color(0xFFF8FAFC),
            textContentColor = Color(0xFF94A3B8),
            title = { Text("What is Kuro") },
            text = {
                Text(
                    "Kuro v${com.kuro.app.BuildConfig.VERSION_NAME}\n" +
                        "Package: com.kuro.app\n" +
                        "Isolated Linux terminals + app clones on device.\n" +
                        engineLine
                )
            }
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF101620),
            contentColor = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Quick settings", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF8FAFC))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Theme", color = Color(0xFF94A3B8), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    ).forEach { (mode, label) ->
                        val selected = currentTheme == mode
                        TextButton(
                            onClick = { onThemeChanged?.invoke(mode) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (selected) Color(0xFFF8FAFC) else Color(0xFF94A3B8)
                            )
                        ) {
                            Text(if (selected) "✓ $label" else label, fontSize = 13.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Root access", color = Color(0xFF94A3B8), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        when (rooted) {
                            true -> "Rooted"
                            false -> "Not rooted"
                            null -> "Checking..."
                        },
                        color = if (rooted == true) Color(0xFF10B981) else Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Storage", color = Color(0xFF94A3B8), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(storageText, color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(),
        label = "card-press"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D2A)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = Color(0xFFF8FAFC), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KuroSystemTab(
    onOpenTerminalTab: () -> Unit = {},
    onOpenDistroCatalog: () -> Unit = {},
    onOpenClonesTab: () -> Unit = {},
    onOpenSettingsTab: () -> Unit = {},
    onNavigateToInstall: (Distro) -> Unit = {},
    onOpenTerminal: (String, Boolean) -> Unit = { _, _ -> },
    engineReady: Boolean? = true,
    onThemeChanged: ((ThemeMode) -> Unit)? = null,
    currentTheme: ThemeMode = ThemeMode.SYSTEM,
    modifier: Modifier = Modifier
) {
    KuroHomeScreen(
        onOpenTerminalTab = onOpenTerminalTab,
        onOpenDistroCatalog = onOpenDistroCatalog,
        onOpenClonesTab = onOpenClonesTab,
        onOpenSettingsTab = onOpenSettingsTab,
        onNavigateToInstall = onNavigateToInstall,
        onOpenTerminal = onOpenTerminal,
        engineReady = engineReady,
        onThemeChanged = onThemeChanged,
        currentTheme = currentTheme,
        modifier = modifier
    )
}
