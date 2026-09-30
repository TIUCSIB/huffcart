package com.huffcart.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
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
import com.huffcart.app.ui.library.GenreCatalog
import com.huffcart.app.ui.library.LibraryState
import com.huffcart.app.ui.library.rememberLibraryState
import com.huffcart.app.ui.screens.CategoriesScreen
import com.huffcart.app.ui.screens.CategoryScreen
import com.huffcart.app.ui.screens.DetailScreen
import com.huffcart.app.ui.screens.GameScreen
import com.huffcart.app.ui.screens.HomeScreen
import com.huffcart.app.ui.screens.KeyMappingScreen
import com.huffcart.app.ui.screens.LibraryScreen
import com.huffcart.app.ui.screens.SettingsScreen
import com.huffcart.app.ui.screens.SplashScreen

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_HOME = "home"
private const val ROUTE_LIBRARY = "library"

private val topDestinations = listOf(
    TopDestination(route = ROUTE_HOME, label = "首页", icon = Icons.Filled.Home),
    TopDestination(route = ROUTE_LIBRARY, label = "游戏库", icon = Icons.Filled.SportsEsports),
    TopDestination(route = "mine", label = "我的", icon = Icons.Filled.Person),
)

@Composable
fun HuffcartApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val libraryState = rememberLibraryState()

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
                                    popUpTo(ROUTE_HOME) { saveState = true }
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
            startDestination = ROUTE_SPLASH,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ROUTE_SPLASH) {
                SplashScreen(
                    onDone = {
                        navController.navigate(ROUTE_HOME) {
                            popUpTo(ROUTE_SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(ROUTE_HOME) {
                HomeScreen(
                    state = libraryState,
                    onOpenDetail = { name ->
                        navController.navigate("detail/${android.net.Uri.encode(name)}")
                    },
                    onOpenCategories = { navController.navigate("categories") },
                )
            }
            composable(ROUTE_LIBRARY) {
                LibraryScreen(
                    state = libraryState,
                    onOpenDetail = { name ->
                        navController.navigate("detail/${android.net.Uri.encode(name)}")
                    },
                    onOpenCategories = { navController.navigate("categories") },
                )
            }
            composable("mine") {
                SettingsScreen(
                    onOpenKeyMapping = { navController.navigate("keymapping") },
                )
            }
            composable("categories") {
                CategoriesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGenre = { genre ->
                        navController.navigate("category/${genre.name}")
                    },
                )
            }
            composable(
                route = "category/{genre}",
                arguments = listOf(navArgument("genre") { type = NavType.StringType }),
            ) { entry ->
                val genre = entry.arguments?.getString("genre")
                    ?.let { runCatching { GenreCatalog.Genre.valueOf(it) }.getOrNull() }
                if (genre == null) {
                    navController.popBackStack()
                } else {
                    CategoryScreen(
                        genre = genre,
                        state = libraryState,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { name ->
                            navController.navigate("detail/${android.net.Uri.encode(name)}")
                        },
                    )
                }
            }
            composable(
                route = "detail/{rom}",
                arguments = listOf(navArgument("rom") { type = NavType.StringType }),
            ) { entry ->
                val rom = entry.arguments?.getString("rom").orEmpty()
                DetailScreen(
                    romName = android.net.Uri.decode(rom),
                    state = libraryState,
                    onBack = { navController.popBackStack() },
                    onStartGame = {
                        navController.navigate("game/${android.net.Uri.encode(android.net.Uri.decode(rom))}")
                    },
                    onRemoved = { navController.popBackStack() },
                )
            }
            composable("keymapping") {
                KeyMappingScreen(onBack = { navController.popBackStack() })
            }
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
