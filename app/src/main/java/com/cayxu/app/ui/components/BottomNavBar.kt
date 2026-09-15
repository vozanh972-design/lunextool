package com.cayxu.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cayxu.app.ui.navigation.Routes

import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Widgets

data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val bottomItems = listOf(
    NavItem(Routes.HOME, "Trang chủ", Icons.Outlined.Home),
    NavItem(Routes.TASKS, "Nhiệm vụ", Icons.Outlined.Assignment),
    NavItem(Routes.UTILITIES, "Tiện ích", Icons.Outlined.Widgets),
    NavItem(Routes.ACCOUNT, "Tài khoản", Icons.Outlined.Person)
)

@Composable
fun CayXuBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val cobalt600 = Color(0xFF1D4ED8)
    val textTertiary = Color(0xFF8E9BB0)
    val indicatorBg = Color(0xFFEFF6FF)
    val borderColor = Color(0xFFE2E8F0)

    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Surface(
        color = Color.White,
        shape = shape,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        NavigationBar(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            modifier = Modifier
                .height(68.dp)
        ) {
            bottomItems.forEach { item ->
                val isSelected = currentRoute == item.route
                NavigationBarItem(
                    selected = isSelected,
                    onClick = {
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label
                        )
                    },
                    label = {
                        Text(
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = cobalt600,
                        selectedTextColor = cobalt600,
                        unselectedIconColor = textTertiary,
                        unselectedTextColor = textTertiary,
                        indicatorColor = indicatorBg
                    )
                )
            }
        }
    }
}
