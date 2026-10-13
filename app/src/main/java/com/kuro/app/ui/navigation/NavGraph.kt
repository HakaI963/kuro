package com.kuro.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kuro.app.di.ServiceLocator
import com.kuro.app.ui.screens.addclone.AddCloneScreen
import com.kuro.app.ui.screens.details.CloneDetailsScreen
import com.kuro.app.ui.screens.diagnostics.DiagnosticsScreen
import com.kuro.app.ui.screens.intro.OrbitalIntro
import com.kuro.app.ui.screens.terminal.AddDistroScreen
import com.kuro.app.ui.screens.terminal.TerminalSessionScreen
import com.kuro.app.ui.theme.MotionTokens

@Composable
fun KuroNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    showIntro: Boolean = true,
    onIntroConsumed: () -> Unit = {}
) {
    val density = LocalDensity.current
    val pushSlidePx = with(density) { MotionTokens.PUSH_ENTER_SLIDE.roundToPx() }
    fun homeRoute(): String {
        val def = ServiceLocator.settingsRepository.settings.value.defaultToLibrary
        return Routes.SHELL
    }

    NavHost(
        navController = navController,
        startDestination = if (showIntro) Routes.INTRO else homeRoute(),
        modifier = modifier,
        enterTransition = { MotionTokens.pushEnter(reduceMotion, pushSlidePx) },
        exitTransition = { MotionTokens.pushExit(reduceMotion) },
        popEnterTransition = { MotionTokens.popEnter(reduceMotion) },
        popExitTransition = { MotionTokens.popExit(reduceMotion, pushSlidePx) }
    ) {
        composable(Routes.INTRO) {
            OrbitalIntro(
                reduceMotion = reduceMotion,
                onDone = {
                    onIntroConsumed()
                    navController.navigate(Routes.SHELL) {
                        popUpTo(Routes.INTRO) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.SHELL) {
            ShellScreen(navController = navController, reduceMotion = reduceMotion)
        }
        composable(Routes.DIAGNOSTICS) {
            DetailsScaffold(onBack = { navController.popBackStack() }) {
                DiagnosticsScreen()
            }
        }
        composable(Routes.ADD_CLONE) {
            AddCloneScaffold(
                onBack = { navController.popBackStack() },
                content = {
                    AddCloneScreen(
                        onDone = { navController.popBackStack() },
                        onOpenClone = {
                            navController.navigate(Routes.cloneDetails(it)) {
                                popUpTo(Routes.ADD_CLONE) { inclusive = true }
                            }
                        }
                    )
                }
            )
        }
        composable(Routes.ADD_DISTRO) {
            AddCloneScaffold(
                onBack = { navController.popBackStack() },
                title = stringResource(com.kuro.app.R.string.distro_title),
                content = {
                    AddDistroScreen(
                        onDone = { navController.popBackStack() },
                        reduceMotion = reduceMotion
                    )
                }
            )
        }
        composable(
            Routes.TERMINAL_SESSION,
            arguments = listOf(navArgument("containerId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("containerId") ?: ""
            DetailsScaffold(onBack = { navController.popBackStack() }) {
                TerminalSessionScreen(containerId = id)
            }
        }
        composable(
            Routes.CLONE_DETAILS,
            arguments = listOf(navArgument("cloneId") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("cloneId") ?: ""
            DetailsScaffold(onBack = { navController.popBackStack() }) {
                CloneDetailsScreen(
                    cloneId = id,
                    reduceMotion = reduceMotion,
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
