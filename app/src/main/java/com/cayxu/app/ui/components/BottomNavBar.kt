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
import androidx.compose.ui.text.font.FontWeight
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
 * - 4 tab chia đều đối xứng 100% bằng weight(1f), loại bỏ hoàn toàn hiện tượng xô lệch vị trí.
 * - Viên thuốc màu đen (#0F172A) căn tâm chính xác tuyệt đối với từng tab.
 * - Trượt vật lý spring êm ái, mượt mà, không giật lag do layout reflow.
 */
@Composable
fun CayXuBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Tìm index của tab đang active
    val activeIndex = remember(currentRoute) {
        val idx = floatingBottomItems.indexOfFirst {
            it.route == currentRoute || (it.route == Routes.UTILITIES && currentRoute == Routes.WALLET)
        }
        if (idx >= 0) idx else 0
    }

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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth
                val tabCount = floatingBottomItems.size
                val tabSlotWidth = totalWidth / tabCount

                // Chiều rộng viên thuốc theo từng tab khi active để ôm trọn chữ đẹp mắt
                val targetPillWidth = when (activeIndex) {
                    0 -> 106.dp // Trang chủ
                    1 -> 104.dp // Nhiệm vụ
                    2 -> 98.dp  // Tiện ích
                    3 -> 90.dp  // Hồ sơ
                    else -> 100.dp
                }

                // Tọa độ tâm của tab active -> Tọa độ X của viên thuốc luôn căn giữa hoàn hảo
                val targetCenterX = tabSlotWidth * activeIndex + (tabSlotWidth / 2)
                val targetPillX = targetCenterX - (targetPillWidth / 2)

                // Animation trượt vật lý liên tục (X position & Width) mượt như nhung
                val animatedPillX by animateDpAsState(
                    targetValue = targetPillX,
                    animationSpec = spring(
                        dampingRatio = 0.82f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "pillSlideX"
                )

                val animatedPillWidth by animateDpAsState(
                    targetValue = targetPillWidth,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "pillSlideWidth"
                )

                // 🚀 KHỐI VIÊN THUỐC TRƯỢT VẬT LÝ DUY NHẤT TRÊN THANH
                Box(
                    modifier = Modifier
                        .offset(x = animatedPillX)
                        .width(animatedPillWidth)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color(0xFF0F172A))
                )

                // CÁC NÚT TAB NẰM ĐỐI XỨNG CÂN BẰNG PHÍA TRÊN KHỐI TRƯỢT
                Row(
                    modifier = Modifier.fillMaxSize(),
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
                                .weight(1f)
                                .fillMaxHeight()
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
                            Row(
                                modifier = Modifier
                                    .height(46.dp)
                                    .padding(horizontal = 8.dp),
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
