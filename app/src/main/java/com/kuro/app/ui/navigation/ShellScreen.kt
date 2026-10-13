package com.kuro.app.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val density = LocalDensity.current
    val slidePx = with(density) { MotionTokens.TAB_SLIDE_DP.dp.roundToPx() }

    AppShell(
        current = tab,
        onSelect = vm::select,
        onNewClone = { navController.navigate(Routes.ADD_CLONE) },
        reduceMotion = reduceMotion,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(MotionTokens.REDUCED_MS))
                        .togetherWith(fadeOut(tween(MotionTokens.REDUCED_MS)))
                } else {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(
                        tween(MotionTokens.TAB_MS, easing = MotionTokens.Emphasized)
                    ) { dir * slidePx } + fadeIn(tween(MotionTokens.TAB_MS)))
                        .togetherWith(
                            slideOutHorizontally(
                                tween(MotionTokens.TAB_MS, easing = MotionTokens.Emphasized)
                            ) { -dir * slidePx } + fadeOut(tween(MotionTokens.TAB_MS))
                        )
                        .using(SizeTransform(clip = false))
                }
            },
            label = "tabs"
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
