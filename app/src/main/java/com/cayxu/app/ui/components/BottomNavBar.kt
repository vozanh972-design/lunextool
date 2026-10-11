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
 * THANH ĐIỀU HƯỚNG NỔI HIỆU ỨNG ĐÀN HỒI CO GIÃN THÔNG MINH (DYNAMIC ACCORDION SLIDING DOCK)
 * - Tab Active tự động nở rộng gấp 2.4 lần, tự động đẩy các icon còn lại dạt sang 2 bên.
 * - Chữ tiếng Việt ("Trang chủ", "Nhiệm vụ", "Tiện ích", "Hồ sơ") hiển thị 100% đầy đủ, không bao giờ bị cắt xén.
 * - Viên thuốc đen trượt vật lý spring đồng bộ hoàn hảo theo từng frame di chuyển của các icon.
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
            .padding(horizontal = 18.dp, vertical = 8.dp),
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
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth

                // Danh sách animated weights: Tab Active nặng 2.4f, các tab Inactive nặng 1.0f
                val animatedWeights = floatingBottomItems.indices.map { index ->
                    val targetWeight = if (index == activeIndex) 2.4f else 1.0f
                    animateFloatAsState(
                        targetValue = targetWeight,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "weight_$index"
                    ).value
                }

                val totalWeightSum = animatedWeights.sum()

                // Tính toán tọa độ X và chiều rộng của từng tab theo animated weight
                val tabWidths = animatedWeights.map { (it / totalWeightSum) * totalWidth }
                val tabStartXs = animatedWeights.indices.map { i ->
                    if (i == 0) 0.dp else {
                        var sum = 0f
                        for (k in 0 until i) {
                            sum += animatedWeights[k]
                        }
                        (sum / totalWeightSum) * totalWidth
                    }
                }

                val activePillX = tabStartXs[activeIndex] + 3.dp
                val activePillWidth = (tabWidths[activeIndex] - 6.dp).coerceAtLeast(46.dp)

                // 🚀 KHỐI VIÊN THUỐC TRƯỢT VẬT LÝ DUY NHẤT TRÊN THANH
                Box(
                    modifier = Modifier
                        .offset(x = activePillX)
                        .width(activePillWidth)
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(Color(0xFF0F172A))
                )

                // CÁC NÚT TAB TỰ ĐỘNG DỒN DỊCH VÀ CO GIÃN ĐỒNG BỘ
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    floatingBottomItems.forEachIndexed { index, item ->
                        val isSelected = activeIndex == index

                        val animatedIconTint by animateColorAsState(
                            targetValue = if (isSelected) Color.White else Color(0xFF94A3B8),
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "pillNavTint_$index"
                        )

                        val interactionSource = remember { MutableInteractionSource() }

                        Box(
                            modifier = Modifier
                                .weight(animatedWeights[index])
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
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(20.dp),
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
