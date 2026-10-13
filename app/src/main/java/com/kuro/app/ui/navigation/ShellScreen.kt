package com.kuro.app.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.kuro.app.ui.screens.activity.ActivityScreen
import com.kuro.app.ui.screens.home.HomeScreen
import com.kuro.app.ui.screens.library.LibraryScreen
import com.kuro.app.ui.screens.settings.SettingsScreen
import com.kuro.app.ui.theme.MotionTokens

@Composable
fun ShellScreen(
    navController: NavHostController,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    vm: ShellViewModel = viewModel()
) {
    val tab by vm.currentTab.collectAsState()
    val direction by vm.direction.collectAsState()

    AppShell(
        current = tab,
        onSelect = vm::select,
        onNewClone = { navController.navigate(Routes.ADD_CLONE) },
        reduceMotion = reduceMotion,
        modifier = modifier
    ) {
        // Step 3c: never cross-fade tabs. Old tab is removed the same frame
        // (plain when, no AnimatedContent). Only the entering content animates:
        // alpha 0->1 + translationX 24dp from navigation direction, 160-200ms,
        // M3 emphasized easing, graphicsLayer only. Opaque root, zero blended frames.
        TabEnterContent(
            tab = tab,
            direction = direction,
            reduceMotion = reduceMotion
        ) { current ->
            val animateIn = vm.shouldAnimateList(current)
            when (current) {
                BottomDestination.Home -> HomeScreen(
                    onOpenClone = { navController.navigate(Routes.cloneDetails(it)) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                    onImportApk = { },
                    onViewLibrary = { vm.select(BottomDestination.Library) },
                    listState = vm.listStateFor(BottomDestination.Home),
                    animateListIn = animateIn,
                    onListShown = { vm.markListSeen(BottomDestination.Home) }
                )
                BottomDestination.Library -> LibraryScreen(
                    onOpenClone = { navController.navigate(Routes.cloneDetails(it)) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                    listState = vm.listStateFor(BottomDestination.Library),
                    animateListIn = animateIn,
                    onListShown = { vm.markListSeen(BottomDestination.Library) }
                )
                BottomDestination.Activity -> ActivityScreen(
                    listState = vm.listStateFor(BottomDestination.Activity),
                    animateListIn = animateIn,
                    onListShown = { vm.markListSeen(BottomDestination.Activity) }
                )
                BottomDestination.Settings -> SettingsScreen(
                    onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                    listState = vm.listStateFor(BottomDestination.Settings),
                    animateListIn = animateIn,
                    onListShown = { vm.markListSeen(BottomDestination.Settings) }
                )
            }
        }
    }
}

@Composable
private fun TabEnterContent(
    tab: BottomDestination,
    direction: Int,
    reduceMotion: Boolean,
    content: @Composable (BottomDestination) -> Unit
) {
    val density = LocalDensity.current
    val slidePx = with(density) { MotionTokens.TAB_SLIDE_DP.dp.roundToPx() }.toFloat()

    // Fresh animatables per tab so exit is instant (old content dropped same frame)
    // and only the entering content animates.
    val alpha = remember(tab) { Animatable(if (reduceMotion) 1f else 0f) }
    val offsetX = remember(tab) {
        Animatable(if (reduceMotion) 0f else direction * slidePx)
    }

    LaunchedEffect(tab) {
        if (reduceMotion) {
            // Reduce motion: 100ms alpha only, no slide.
            alpha.snapTo(0f)
            offsetX.snapTo(0f)
            alpha.animateTo(
                1f,
                animationSpec = tween(MotionTokens.REDUCED_MS)
            )
        } else {
            alpha.snapTo(0f)
            offsetX.snapTo(direction * slidePx)
            // Parallel enter: fade + directional slide, emphasized easing.
            kotlinx.coroutines.launch {
                alpha.animateTo(
                    1f,
                    animationSpec = tween(
                        MotionTokens.TAB_MS,
                        easing = MotionTokens.Emphasized
                    )
                )
            }
            offsetX.animateTo(
                0f,
                animationSpec = tween(
                    MotionTokens.TAB_MS,
                    easing = MotionTokens.Emphasized
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationX = offsetX.value
            }
    ) {
        content(tab)
    }
}
