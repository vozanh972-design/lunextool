package com.cayxu.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.InfoBlueBg
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextSecondary

data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomItems = listOf(
    NavItem(Routes.HOME, "Trang chủ", Icons.Filled.Home),
    NavItem(Routes.TASKS, "Nhiệm vụ", Icons.Filled.TaskAlt),
    NavItem(Routes.WALLET, "Ví", Icons.Filled.AccountBalanceWallet),
    NavItem(Routes.ACCOUNT, "Tài khoản", Icons.Filled.Person)
)

@Composable
fun CayXuBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar(containerColor = CardWhite) {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Primary,
                    selectedTextColor = Primary,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = InfoBlueBg
                )
            )
        }
    }
}
