package com.bitbot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bitbot.ui.components.PanelType
import com.bitbot.ui.screens.PanelHostScreen
import com.bitbot.ui.screens.home.HomeScreen
import com.bitbot.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object PanelHost : Screen("panel_host/{initialPanel}") {
        fun createRoute(initialPanel: String = "PILOT"): String = "panel_host/$initialPanel"
    }
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPilot = { navController.navigate(Screen.PanelHost.createRoute("PILOT")) },
                onNavigateToData = { navController.navigate(Screen.PanelHost.createRoute("DATA")) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.PanelHost.route,
            arguments = listOf(navArgument("initialPanel") { defaultValue = "PILOT" })
        ) { backStackEntry ->
            val initialPanelName = backStackEntry.arguments?.getString("initialPanel") ?: "PILOT"
            val initialPanel = try { PanelType.valueOf(initialPanelName) } catch (_: Exception) { PanelType.PILOT }
            PanelHostScreen(
                onNavigateBack = { navController.popBackStack() },
                initialPanel = initialPanel
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
