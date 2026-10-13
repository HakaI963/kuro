package com.kuro.app.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.compose.runtime.getValue
import com.kuro.app.di.ServiceLocator
import com.kuro.app.ui.screens.activity.ActivityScreen
import com.kuro.app.ui.screens.addclone.AddCloneScreen
import com.kuro.app.ui.screens.details.CloneDetailsScreen
import com.kuro.app.ui.screens.diagnostics.DiagnosticsScreen
import com.kuro.app.ui.screens.home.HomeScreen
import com.kuro.app.ui.screens.intro.OrbitalIntro
import com.kuro.app.ui.screens.library.LibraryScreen
import com.kuro.app.ui.screens.settings.SettingsScreen
import com.kuro.app.ui.theme.MotionTokens

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KuroNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    showIntro: Boolean = true,
    onIntroConsumed: () -> Unit = {}
) {
    val density = LocalDensity.current
    val slidePx = with(density) { MotionTokens.NAV_ENTER_SLIDE.roundToPx() }
    fun homeRoute(): String {
        val def = ServiceLocator.settingsRepository.settings.value.defaultToLibrary
        return if (def) Routes.LIBRARY else Routes.HOME
    }

    SharedTransitionLayout(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = if (showIntro) Routes.INTRO else homeRoute(),
            enterTransition = { MotionTokens.navEnter(reduceMotion, slidePx) },
            exitTransition = { MotionTokens.navExit(reduceMotion) },
            popEnterTransition = { MotionTokens.navEnter(reduceMotion, slidePx) },
            popExitTransition = { MotionTokens.navExit(reduceMotion) }
        ) {
            composable(Routes.INTRO) {
                OrbitalIntro(
                    reduceMotion = reduceMotion,
                    onDone = {
                        onIntroConsumed()
                        navController.navigate(homeRoute()) {
                            popUpTo(Routes.INTRO) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.HOME) {
                AppShell(
                    current = BottomDestination.Home,
                    onSelect = { navigateBottom(navController, it) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                    reduceMotion = reduceMotion
                ) {
                    HomeScreen(
                        onOpenClone = { navController.navigate(Routes.cloneDetails(it)) },
                        onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                        onImportApk = { },
                        onViewLibrary = { navController.navigate(Routes.LIBRARY) },
                        sharedScope = if (reduceMotion) null else this@SharedTransitionLayout,
                        animScope = if (reduceMotion) null else this@composable
                    )
                }
            }
            composable(Routes.LIBRARY) {
                AppShell(
                    current = BottomDestination.Library,
                    onSelect = { navigateBottom(navController, it) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                    reduceMotion = reduceMotion
                ) {
                    LibraryScreen(
                        onOpenClone = { navController.navigate(Routes.cloneDetails(it)) },
                        onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                        reduceMotion = reduceMotion,
                        sharedScope = if (reduceMotion) null else this@SharedTransitionLayout,
                        animScope = if (reduceMotion) null else this@composable
                    )
                }
            }
            composable(Routes.ACTIVITY) {
                AppShell(
                    current = BottomDestination.Activity,
                    onSelect = { navigateBottom(navController, it) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                    reduceMotion = reduceMotion
                ) {
                    ActivityScreen()
                }
            }
            composable(Routes.SETTINGS) {
                AppShell(
                    current = BottomDestination.Settings,
                    onSelect = { navigateBottom(navController, it) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                    reduceMotion = reduceMotion
                ) {
                    SettingsScreen(onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) })
                }
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
            composable(
                Routes.CLONE_DETAILS,
                arguments = listOf(navArgument("cloneId") { type = NavType.StringType })
            ) { backStack ->
                val id = backStack.arguments?.getString("cloneId") ?: ""
                DetailsScaffold(onBack = { navController.popBackStack() }) {
                    CloneDetailsScreen(
                        cloneId = id,
                        onBack = { navController.popBackStack() },
                        onDeleted = {
                            navController.popBackStack()
                        },
                        reduceMotion = reduceMotion,
                        sharedScope = if (reduceMotion) null else this@SharedTransitionLayout,
                        animScope = if (reduceMotion) null else this@composable
                    )
                }
            }
        }
    }
}

private fun navigateBottom(nav: NavHostController, dest: BottomDestination) {
    val route = when (dest) {
        BottomDestination.Home -> Routes.HOME
        BottomDestination.Library -> Routes.LIBRARY
        BottomDestination.Activity -> Routes.ACTIVITY
        BottomDestination.Settings -> Routes.SETTINGS
    }
    nav.navigate(route) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
fun currentBottomDestination(nav: NavHostController): BottomDestination {
    val entry by nav.currentBackStackEntryAsState()
    return when (entry?.destination?.route) {
        Routes.LIBRARY -> BottomDestination.Library
        Routes.ACTIVITY -> BottomDestination.Activity
        Routes.SETTINGS -> BottomDestination.Settings
        else -> BottomDestination.Home
    }
}
