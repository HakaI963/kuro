package com.kuro.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.minDimension
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuro.app.core.data.Distro
import com.kuro.app.core.data.DistroRepository
import com.kuro.app.core.terminal.SessionRegistry
import com.kuro.app.core.terminal.TerminalLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val HomeBgA = Color(0xFF0A0E14)
private val HomeBgB = Color(0xFF151D2A)
private val CtaStart = Color(0xFF7C6CFF)
private val CtaEnd = Color(0xFF5DB2FF)
private val CardShape = RoundedCornerShape(24.dp)

/**
 * Step 7: Kuro Home — landscape-first dashboard. Animated dark gradient
 * background, orbital K logo + wordmark, and a 2-column grid of action cards.
 * All counts are measured on device (sessions, installed distros, clones);
 * nothing is faked. Opaque root.
 */
@Composable
fun KuroSystemTab(
    onOpenTerminalTab: () -> Unit,
    onOpenDistroCatalog: () -> Unit = {},
    onOpenClonesTab: () -> Unit = {},
    onOpenSettingsTab: () -> Unit = {},
    onNavigateToInstall: (Distro) -> Unit = {},
    onOpenTerminal: (String, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var installedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedIds = withContext(Dispatchers.IO) {
            DistroRepository.supportedDistros
                .filter {
                    runCatching { TerminalLauncher.isDistroInstalledOnFs(context, it.id) }
                        .getOrDefault(false)
                }
                .map { it.id }
        }
    }
    val revision by SessionRegistry.revision.collectAsState()
    val runningSessions = remember(revision) { SessionRegistry.sessions().size }
    val clonesRepo = remember {
        runCatching { com.kuro.app.di.ServiceLocator.cloneRepository }.getOrNull()
    }
    val clones by clonesRepo?.clones?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val cloneCount = clones.size

    Box(modifier = modifier.fillMaxSize()) {
        HomeGradientBackdrop(modifier = Modifier.fillMaxSize())
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeHeader()
            }
            item {
                HomeCard(
                    title = "Explore Kuro",
                    subtitle = if (runningSessions > 0) "$runningSessions live" else "Live shells",
                    icon = Icons.Default.Explore,
                    gradient = Brush.linearGradient(colors = listOf(CtaStart, CtaEnd)),
                    contentColor = Color.White,
                    onClick = onOpenTerminalTab
                )
            }
            item {
                HomeCard(
                    title = "Get Distro",
                    subtitle = "Install Linux",
                    icon = Icons.Default.Download,
                    onClick = onOpenDistroCatalog
                )
            }
            item {
                val names = installedIds.take(2).mapNotNull { id ->
                    DistroRepository.supportedDistros.find { it.id == id }?.name
                }
                HomeCard(
                    title = "My Containers",
                    subtitle = if (installedIds.isEmpty()) "None yet"
                    else "${installedIds.size} installed",
                    icon = Icons.Default.Storage,
                    body = names.joinToString(" · "),
                    onClick = onOpenTerminalTab
                )
            }
            item {
                HomeCard(
                    title = "App Clones",
                    subtitle = if (cloneCount > 0) "$cloneCount clones" else "Clone manager",
                    icon = Icons.Default.Apps,
                    onClick = onOpenClonesTab
                )
            }
        }
    }
}

@Composable
private fun HomeGradientBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "kuro-home-bg")
    val morph by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "bg-morph"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "glow-drift"
    )
    val base = lerp(HomeBgA, HomeBgB, morph)
    Canvas(modifier = modifier) {
        drawRect(base)
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(CtaStart.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * (0.15f + 0.1f * drift), h * 0.12f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * (0.15f + 0.1f * drift), h * 0.12f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(CtaEnd.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(w * (0.9f - 0.1f * drift), h * 0.85f),
                radius = w * 0.6f
            ),
            radius = w * 0.6f,
            center = Offset(w * (0.9f - 0.1f * drift), h * 0.85f)
        )
    }
}

@Composable
private fun HomeHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    ) {
        OrbitalKLogo()
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "Kuro",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Linux & clones, on device",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun OrbitalKLogo() {
    val transition = rememberInfiniteTransition(label = "kuro-orbit")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(5000)),
        label = "orbit"
    )
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f - 4.dp.toPx()
            drawCircle(
                color = CtaStart.copy(alpha = 0.5f),
                radius = r,
                style = Stroke(width = 2.dp.toPx())
            )
            rotate(angle) {
                drawCircle(
                    color = CtaEnd,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f - r)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(colors = listOf(CtaStart, CtaEnd))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "K",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
        }
    }
}

@Composable
private fun HomeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    gradient: Brush? = null,
    contentColor: Color? = null,
    body: String = ""
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "home-card-press"
    )
    val colors = MaterialTheme.colorScheme
    val fg = contentColor ?: colors.onSurface
    val sub = (contentColor ?: colors.onSurfaceVariant).copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CardShape)
            .then(
                if (gradient != null) Modifier.background(gradient)
                else Modifier.background(colors.surfaceContainerHigh)
            )
            .then(
                if (gradient == null) Modifier.border(1.dp, colors.outlineVariant, CardShape)
                else Modifier
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (gradient != null) Color.White else CtaStart,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1
            )
            Text(
                text = if (body.isNotBlank()) body else subtitle,
                fontSize = 12.sp,
                color = sub,
                maxLines = 1
            )
        }
    }
}
