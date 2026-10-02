package com.huffcart.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.huffcart.app.ui.library.GenreCatalog
import com.huffcart.app.ui.library.LibraryState
import com.huffcart.app.ui.library.rememberLibraryState
import com.huffcart.app.ui.screens.AudioSettingsScreen
import com.huffcart.app.ui.screens.CategoriesScreen
import com.huffcart.app.ui.screens.CategoryScreen
import com.huffcart.app.ui.screens.CreateRoomScreen
import com.huffcart.app.ui.screens.DetailScreen
import com.huffcart.app.ui.screens.DisplaySettingsScreen
import com.huffcart.app.ui.screens.GameScreen
import com.huffcart.app.ui.screens.HomeScreen
import com.huffcart.app.ui.screens.KeyMappingScreen
import com.huffcart.app.ui.screens.NetplayScreen
import com.huffcart.app.ui.screens.RoomScreen
import com.huffcart.app.ui.screens.SaveManagementScreen
import com.huffcart.app.ui.screens.SettingsScreen
import com.huffcart.app.ui.screens.SplashScreen
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcOnLightVariant
import com.huffcart.app.ui.theme.HcRed

private data class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_HOME = "home"
private const val ROUTE_NETPLAY = "netplay"

private val topDestinations = listOf(
    TopDestination(route = ROUTE_HOME, label = "首页", icon = Icons.Filled.Home),
    TopDestination(route = ROUTE_NETPLAY, label = "联机", icon = Icons.Filled.Groups),
    TopDestination(route = "mine", label = "我的", icon = Icons.Filled.Person),
)

@Composable
fun HuffcartApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val libraryState = rememberLibraryState()

    // 冒烟自动化通道：am start --ez open_netplay true 直达联机屏
    // （MuMu 外壳会拦截顶部区域点击，adb 脚本无法点中顶栏入口）
    LaunchedEffect(Unit) {
        val fromAdb = (navController.context as? android.app.Activity)
            ?.intent?.getBooleanExtra("open_netplay", false) == true
        if (fromAdb) navController.navigate(ROUTE_NETPLAY)
    }

    Scaffold(
        // 内容 insets 只保留下方（导航条）：顶部由各屏自管（AppTopBar/详情/游戏屏
        // 均自带 statusBars 处理）。默认的 systemBars 会在真机上双重补偿——启动页
        // 四周露底不全屏、M3 TopAppBar 再吃一次状态栏导致顶栏高度异常；模拟器
        // insets=0 掩盖了这个问题（真机 RMX5060 实测顶栏标题下移 79dp）。
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Bottom),
        bottomBar = {
            if (currentRoute in topDestinations.map { it.route }) {
                // 自绘底部导航：米白底融合 + 顶部细分隔线；选中红/未选中灰，无椭圆高亮、无 ripple
                Column {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    // navigationBarsPadding：手势条区域不被底栏内容压住（真机手势导航）
                    Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().background(HcCream)) {
                        topDestinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            val tint = if (selected) HcRed else HcOnLightVariant
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        navController.navigate(destination.route) {
                                            launchSingleTop = true
                                            popUpTo(ROUTE_HOME) { saveState = true }
                                            restoreState = true
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    destination.icon,
                                    contentDescription = destination.label,
                                    tint = tint,
                                    modifier = Modifier.size(26.dp),
                                )
                                Text(
                                    text = destination.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tint,
                                )
                            }
                        }
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
            composable(ROUTE_NETPLAY) {
                NetplayScreen(
                    onOpenRoom = { navController.navigate("room") { launchSingleTop = true } },
                    onCreateRoom = { navController.navigate("netplay/create") },
                )
            }
            composable("netplay/create") {
                CreateRoomScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRoom = { navController.navigate("room") { launchSingleTop = true } },
                )
            }
            composable("mine") {
                SettingsScreen(
                    onOpenKeyMapping = { navController.navigate("keymapping") },
                    onOpenDisplaySettings = { navController.navigate("displaysettings") },
                    onOpenAudioSettings = { navController.navigate("audiosettings") },
                    onOpenSaveManagement = { navController.navigate("savemanagement") },
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
            composable("displaysettings") {
                DisplaySettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("audiosettings") {
                AudioSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("savemanagement") {
                SaveManagementScreen(onBack = { navController.popBackStack() })
            }
            composable("room") {
                RoomScreen(
                    onBack = { navController.popBackStack() },
                    onStartGame = { romName ->
                        navController.navigate("game/${android.net.Uri.encode(romName)}")
                    },
                )
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
