package com.cayxu.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.cayxu.app.ui.navigation.Routes

private data class FloatingNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val floatingBottomItems = listOf(
    FloatingNavItem(Routes.HOME, "Trang chủ", Icons.Outlined.Home),
    FloatingNavItem(Routes.TASKS, "Nhiệm vụ", Icons.Outlined.TaskAlt),
    FloatingNavItem(Routes.UTILITIES, "Tiện ích", Icons.Outlined.GridView),
    FloatingNavItem(Routes.ACCOUNT, "Hồ sơ", Icons.Outlined.Person)
)

/**
 * THANH ĐIỀU HƯỚNG NỔI DẠNG VIÊN THUỐC (FLOATING PILL DOCK)
 * - Tái hiện 100% phong cách Pinterest Bento Dock (không icon đơn điệu, không chữ rườm rà)
 * - Tab được chọn bọc trong nút tròn màu đá than sâu (#0F172A) nổi bật
 * - Hoàn trả chính xác Tab 3 thành "Tiện ích" (Routes.UTILITIES)
 */
@Composable
fun CayXuBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = Color.White.copy(alpha = 0.98f),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                floatingBottomItems.forEach { item ->
                    val isSelected = currentRoute == item.route ||
                            (item.route == Routes.UTILITIES && currentRoute == Routes.WALLET)

                    val animatedBg by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF0F172A) else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "pillNavBg"
                    )

                    val animatedIconTint by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "pillNavTint"
                    )

                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(animatedBg)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(22.dp),
                            tint = animatedIconTint
                        )
                    }
                }
            }
        }
    }
}
