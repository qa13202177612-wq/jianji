package com.jianji.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jianji.app.data.AppSettings
import com.jianji.app.ui.screens.BillsScreen
import com.jianji.app.ui.screens.BudgetScreen
import com.jianji.app.ui.screens.HomeScreen
import com.jianji.app.ui.screens.ProfileScreen
import com.jianji.app.ui.screens.StatsScreen
import com.jianji.app.ui.theme.JianJiTheme
import com.jianji.app.viewmodel.BillViewModel
import com.jianji.app.viewmodel.SettingsViewModel
import com.jianji.app.viewmodel.StatsViewModel

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    TabItem("home", "记账", Icons.Outlined.EditNote),
    TabItem("bills", "账单", Icons.AutoMirrored.Outlined.ReceiptLong),
    TabItem("stats", "统计", Icons.Outlined.PieChart),
    TabItem("profile", "我的", Icons.Outlined.Person)
)

@Composable
fun JianJiRoot() {
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    val darkTheme = when (settings.themeMode) {
        AppSettings.THEME_LIGHT -> false
        AppSettings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    JianJiTheme(darkTheme = darkTheme) {
        val billVm: BillViewModel = viewModel()
        val statsVm: StatsViewModel = viewModel()
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomBar = tabs.any { it.route == currentRoute }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
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
                                        imageVector = tab.icon,
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
                startDestination = "home",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("home") {
                    HomeScreen(
                        billVm = billVm,
                        settings = settings,
                        onOpenBudget = { navController.navigate("budget") }
                    )
                }
                composable("bills") {
                    BillsScreen(billVm = billVm)
                }
                composable("stats") {
                    StatsScreen(statsVm = statsVm)
                }
                composable("profile") {
                    ProfileScreen(
                        settingsVm = settingsVm,
                        billVm = billVm,
                        settings = settings,
                        onOpenBudget = { navController.navigate("budget") }
                    )
                }
                composable("budget") {
                    BudgetScreen(
                        settingsVm = settingsVm,
                        billVm = billVm,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
