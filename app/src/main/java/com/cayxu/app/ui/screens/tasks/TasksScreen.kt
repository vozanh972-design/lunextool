package com.cayxu.app.ui.screens.tasks

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*

private data class FigmaTaskPlatform(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val onClick: (NavController, android.content.Context) -> Unit
)

private val figmaPlatforms = listOf(
    FigmaTaskPlatform(
        id = "instagram",
        name = "Instagram",
        description = "Theo dõi và thích bài viết để nhận điểm.",
        icon = Icons.Outlined.PhotoCamera,
        iconBg = Color(0xFFFDF2F8),
        iconTint = Color(0xFFE1306C),
        onClick = { navController, _ ->
            navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
        }
    ),
    FigmaTaskPlatform(
        id = "tiktok",
        name = "TikTok",
        description = "Xem và tương tác với video yêu thích.",
        icon = Icons.Outlined.MusicNote,
        iconBg = Color(0xFF0F172A),
        iconTint = Color.White,
        onClick = { navController, _ ->
            navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK) { launchSingleTop = true }
        }
    ),
    FigmaTaskPlatform(
        id = "facebook",
        name = "Facebook",
        description = "Thích và theo dõi những trang mới.",
        icon = Icons.Outlined.ThumbUp,
        iconBg = Color(0xFFEFF6FF),
        iconTint = Color(0xFF1877F2),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Facebook")) { launchSingleTop = true }
        }
    ),
    FigmaTaskPlatform(
        id = "youtube",
        name = "YouTube",
        description = "Xem video và đăng ký kênh sáng tạo.",
        icon = Icons.Outlined.PlayArrow,
        iconBg = Color(0xFFFEF2F2),
        iconTint = Color(0xFFEF4444),
        onClick = { _, context ->
            Toast.makeText(context, "Nhiệm vụ YouTube đang được tối ưu", Toast.LENGTH_SHORT).show()
        }
    ),
    FigmaTaskPlatform(
        id = "twitter",
        name = "X / Twitter",
        description = "Theo dõi và thích bài đăng cộng đồng.",
        icon = Icons.Outlined.Tag,
        iconBg = Color(0xFFF1F5F9),
        iconTint = Color(0xFF0F172A),
        onClick = { _, context ->
            Toast.makeText(context, "Nhiệm vụ X / Twitter đang được tối ưu", Toast.LENGTH_SHORT).show()
        }
    )
)

/**
 * MÀN HÌNH DANH SÁCH NHIỆM VỤ — CHUẨN FIGMA 100% (SCREEN 14)
 */
@Composable
fun TasksScreen(navController: NavController) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(searchQuery) {
        figmaPlatforms.filter { item ->
            searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.description.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Ô TÌM KIẾM CHUẨN FIGMA SCREEN 14
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Tìm kiếm",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Tìm nhiệm vụ, nền tảng...",
                                fontSize = 15.sp,
                                color = TextSecondary.copy(alpha = 0.8f)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. TIÊU ĐỀ: DÀNH CHO BẠN (FIGMA SCREEN 14)
            Text(
                text = "Dành cho bạn",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. GRID 2 CỘT CHUẨN FIGMA (INSTAGRAM, TIKTOK, FACEBOOK, YOUTUBE, X/TWITTER)
            val chunkedItems = filteredList.chunked(2)
            chunkedItems.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            FigmaPlatformGridCard(
                                item = item,
                                onClick = { item.onClick(navController, context) }
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FigmaPlatformGridCard(
    item: FigmaTaskPlatform,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Hàng Icon + Mũi tên >
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(item.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.name,
                        tint = item.iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.description,
                fontSize = 12.5.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
