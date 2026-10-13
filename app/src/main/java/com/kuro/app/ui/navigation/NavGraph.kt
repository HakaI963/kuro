package com.kuro.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.kuro.app.ui.screens.home.HomeScreen
import com.kuro.app.ui.screens.library.LibraryScreen
import com.kuro.app.ui.screens.onboarding.OnboardingScreen
import com.kuro.app.ui.screens.settings.SettingsScreen
import com.kuro.app.ui.screens.splash.SplashScreen

@Composable
fun KuroNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val enterT = if (reduceMotion) fadeIn(tween(0)) else fadeIn(tween(250)) +
        slideInHorizontally(tween(250)) { it / 6 }
    val exitT = if (reduceMotion) fadeOut(tween(0)) else fadeOut(tween(200))

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier,
        enterTransition = { enterT },
        exitTransition = { exitT },
        popEnterTransition = { fadeIn(tween(if (reduceMotion) 0 else 200)) },
        popExitTransition = { fadeOut(tween(if (reduceMotion) 0 else 200)) }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(reduceMotion = reduceMotion, onDone = { completed ->
                if (completed) {
                    val def = ServiceLocator.settingsRepository.settings.value.defaultToLibrary
                    navController.navigate(if (def) Routes.LIBRARY else Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                } else {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinish = {
                val def = ServiceLocator.settingsRepository.settings.value.defaultToLibrary
                navController.navigate(if (def) Routes.LIBRARY else Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            AppShell(
                current = BottomDestination.Home,
                onSelect = { navigateBottom(navController, it) },
                onNewClone = { navController.navigate(Routes.ADD_CLONE) }
            ) {
                HomeScreen(
                    onOpenClone = { navController.navigate(Routes.cloneDetails(it)) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) },
                    onImportApk = { navController.navigate(Routes.ADD_CLONE) },
                    onViewLibrary = { navController.navigate(Routes.LIBRARY) }
                )
            }
        }
        composable(Routes.LIBRARY) {
            AppShell(
                current = BottomDestination.Library,
                onSelect = { navigateBottom(navController, it) },
                onNewClone = { navController.navigate(Routes.ADD_CLONE) }
            ) {
                LibraryScreen(
                    onOpenClone = { navController.navigate(Routes.cloneDetails(it)) },
                    onNewClone = { navController.navigate(Routes.ADD_CLONE) }
                )
            }
        }
        composable(Routes.ACTIVITY) {
            AppShell(
                current = BottomDestination.Activity,
                onSelect = { navigateBottom(navController, it) },
                onNewClone = { navController.navigate(Routes.ADD_CLONE) }
            ) {
                ActivityScreen()
            }
        }
        composable(Routes.SETTINGS) {
            AppShell(
                current = BottomDestination.Settings,
                onSelect = { navigateBottom(navController, it) },
                onNewClone = { navController.navigate(Routes.ADD_CLONE) }
            ) {
                SettingsScreen()
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
                    }
                )
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
