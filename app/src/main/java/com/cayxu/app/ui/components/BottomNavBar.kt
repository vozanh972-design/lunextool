package com.cayxu.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * THANH ĐIỀU HƯỚNG NỔI HIỆU ỨNG TRƯỢT VẬT LÝ LIÊN TỤC (PHYSICAL CONTINUOUS SLIDING INDICATOR DOCK)
 * - Một khối viên thuốc màu đá than đen (#0F172A) trượt mượt mà xuyên suốt giữa các tab theo tọa độ offset
 * - Tự động co giãn bề ngang (width) và dịch chuyển tọa độ x theo tab được bấm
 * - Tên chức năng xuất hiện êm ái khi tab active, tab khác giữ icon thanh lịch
 */
@Composable
fun CayXuBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val density = LocalDensity.current

    // Lưu trữ tọa độ X và chiều rộng của từng tab
    val tabPositions = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }

    // Tìm index của tab đang active
    val activeIndex = remember(currentRoute) {
        val idx = floatingBottomItems.indexOfFirst {
            it.route == currentRoute || (it.route == Routes.UTILITIES && currentRoute == Routes.WALLET)
        }
        if (idx >= 0) idx else 0
    }

    val currentTabPos = tabPositions[activeIndex]
    val targetOffsetX = currentTabPos?.first ?: 0.dp
    val targetWidth = currentTabPos?.second ?: 46.dp

    // Animation trượt vật lý liên tục (X position & Width)
    val animatedOffsetX by animateDpAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillSlideX"
    )

    val animatedPillWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillSlideWidth"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // 🚀 KHỐI VIÊN THUỐC TRƯỢT VẬT LÝ DUY NHẤT TRÊN THANH
                if (tabPositions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .offset(x = animatedOffsetX)
                            .width(animatedPillWidth)
                            .height(46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(Color(0xFF0F172A))
                    )
                }

                // CÁC NÚT TAB NẰM PHÍA TRÊN KHỐI TRƯỢT
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    floatingBottomItems.forEachIndexed { index, item ->
                        val isSelected = activeIndex == index

                        val animatedIconTint by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pillNavTint"
                        )

                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(23.dp))
                                .onGloballyPositioned { coordinates ->
                                    val xInParent = with(density) { coordinates.positionInParent().x.toDp() }
                                    val widthInParent = with(density) { coordinates.size.width.toDp() }
                                    tabPositions[index] = Pair(xInParent, widthInParent)
                                }
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
                                }
                                .padding(horizontal = if (isSelected) 16.dp else 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(21.dp),
                                    tint = animatedIconTint
                                )

                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            expandHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                                    exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                            shrinkHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = item.label,
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
