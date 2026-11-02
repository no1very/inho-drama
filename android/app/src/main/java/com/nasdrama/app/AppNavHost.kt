package com.nasdrama.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import androidx.navigation.navArgument
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.ui.admin.SourcesScreen
import com.nasdrama.app.ui.detail.DetailScreen
import com.nasdrama.app.ui.history.HistoryScreen
import com.nasdrama.app.ui.home.HomeScreen
import com.nasdrama.app.ui.login.LoginScreen
import com.nasdrama.app.ui.logs.LogsScreen
import com.nasdrama.app.ui.mine.MineScreen
import com.nasdrama.app.ui.player.PlayerScreen

/** 路由表 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val HISTORY = "history"
    const val MINE = "mine"
    const val ADMIN_SOURCES = "adminSources"
    const val LOGS = "logs"
    const val DETAIL = "detail/{id}"
    const val PLAYER = "player/{seriesId}?ep={ep}"

    fun detail(id: Int): String = "detail/$id"

    /** ep 传 null 表示交给服务端续播逻辑（continue_episode_id，默认第 1 集） */
    fun player(seriesId: Int, ep: Int? = null): String =
        if (ep != null && ep > 0) "player/$seriesId?ep=$ep" else "player/$seriesId"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomTab(Routes.HISTORY, "历史", Icons.Filled.History, Icons.Outlined.History),
    BottomTab(Routes.MINE, "我的", Icons.Filled.Person, Icons.Outlined.Person)
)

/**
 * 全局导航：底部 NavigationBar 仅在 首页/历史/我的 三个 tab 显示；
 * detail / player / adminSources 为全屏页面。
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val startDestination = Routes.HOME

    // 首次进入：无 token 跳登录页；之后常驻监听 401 登出事件
    LaunchedEffect(Unit) {
        if (AppGraph.settings.tokenOnce().isBlank()) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
        AppGraph.repo.logoutEvents.collect {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in setOf(Routes.HOME, Routes.HISTORY, Routes.MINE)

    Scaffold(
        // inset 避让各自负责：顶部由页面顶栏/封面按钮自行避让（挖孔屏），
        // 底部由 NavigationBar 自避让小白条；这里置零防止 Scaffold 再叠一层
        // 系统栏 padding（无 topBar 时它默认会垫状态栏高度，标题会被压低）
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onOpenPlayer = { seriesId, ep -> navController.navigate(Routes.player(seriesId, ep)) },
                    onOpenDetail = { id -> navController.navigate(Routes.detail(id)) }
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenPlayer = { seriesId, ep -> navController.navigate(Routes.player(seriesId, ep)) }
                )
            }

            composable(Routes.MINE) {
                MineScreen(
                    onOpenSources = { navController.navigate(Routes.ADMIN_SOURCES) },
                    onOpenLogs = { navController.navigate(Routes.LOGS) },
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.ADMIN_SOURCES) {
                SourcesScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LOGS) {
                LogsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.DETAIL) { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull() ?: 0
                DetailScreen(
                    seriesId = id,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { seriesId, ep -> navController.navigate(Routes.player(seriesId, ep)) }
                )
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(
                    navArgument("seriesId") { type = NavType.IntType },
                    navArgument("ep") {
                        type = NavType.IntType
                        defaultValue = -1 // -1 表示未指定，走服务端续播
                    }
                )
            ) { entry ->
                val seriesId = entry.arguments?.getInt("seriesId") ?: 0
                val ep = entry.arguments?.getInt("ep") ?: -1
                PlayerScreen(
                    seriesId = seriesId,
                    initialEp = ep,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
