package com.huffcart.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.huffcart.app.ui.screens.GameScreen
import com.huffcart.app.ui.screens.LibraryScreen
import com.huffcart.app.ui.screens.SettingsScreen

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private const val ROUTE_LIBRARY = "library"

private val topDestinations = listOf(
    TopDestination(route = "library", label = "游戏库", icon = Icons.AutoMirrored.Filled.List),
    TopDestination(route = "settings", label = "设置", icon = Icons.Filled.Settings),
)

@Composable
fun HuffcartApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in topDestinations.map { it.route }) {
                NavigationBar {
                    topDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    popUpTo(ROUTE_LIBRARY) { saveState = true }
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_LIBRARY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    onPlay = { name ->
                        navController.navigate("game/${android.net.Uri.encode(name)}")
                    },
                )
            }
            composable("settings") { SettingsScreen() }
            composable(
                route = "game/{rom}",
                arguments = listOf(navArgument("rom") { type = NavType.StringType }),
            ) { entry ->
                val rom = entry.arguments?.getString("rom").orEmpty()
                GameScreen(romName = android.net.Uri.decode(rom), onExit = { navController.popBackStack() })
            }
        }
    }
}
