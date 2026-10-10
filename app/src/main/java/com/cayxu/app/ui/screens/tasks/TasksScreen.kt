package com.cayxu.app.ui.screens.tasks

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

private data class TaskPlatformItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val onClick: (NavController, android.content.Context) -> Unit
)

private val platformItems = listOf(
    TaskPlatformItem(
        id = "xsmm",
        name = "XSMM",
        subtitle = "Tăng tương tác Facebook, TikTok đa kênh chuyên nghiệp.",
        icon = Icons.Outlined.CheckCircle,
        iconBg = Color(0xFFEFF6FF),
        iconTint = Color(0xFF2563EB),
        onClick = { navController, context ->
            val isXsmmLoggedIn = com.cayxu.app.data.local.XsmmAccountStore.isLoggedIn(context)
            if (isXsmmLoggedIn) {
                com.cayxu.app.ui.screens.xsmm.XsmmSession.restore(context)
            }
            val route = if (isXsmmLoggedIn || com.cayxu.app.ui.screens.xsmm.XsmmSession.isLoggedIn.value) {
                Routes.XSMM_ACCOUNT
            } else {
                Routes.XSMM_LOGIN
            }
            navController.navigate(route) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "golike",
        name = "Golike",
        subtitle = "Nhiệm vụ tương tác kiếm tiền mạng xã hội đa kênh.",
        icon = Icons.Outlined.Star,
        iconBg = Color(0xFFFEF3C7),
        iconTint = Color(0xFFD97706),
        onClick = { navController, context ->
            com.cayxu.app.ui.screens.golike.GolikeSession.restore(context)
            navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "tuongtaccheo_tiktok",
        name = "TikTok TTC",
        subtitle = "Xem và tương tác với video yêu thích nhận thưởng.",
        icon = Icons.Outlined.PlayCircle,
        iconBg = Color(0xFFF1F5F9),
        iconTint = Color(0xFF0F172A),
        onClick = { navController, _ ->
            navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "tuongtaccheo",
        name = "Tuongtaccheo",
        subtitle = "Tương tác chéo giữa các nền tảng mạng xã hội.",
        icon = Icons.Outlined.FavoriteBorder,
        iconBg = Color(0xFFFDF2F8),
        iconTint = Color(0xFFDB2777),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Tuongtaccheo")) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "nhiemvucheo",
        name = "Nhiemvucheo",
        subtitle = "Tăng sub, view, tương tác đa kênh tự động.",
        icon = Icons.Outlined.Sync,
        iconBg = Color(0xFFE0E7FF),
        iconTint = Color(0xFF4F46E5),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Nhiemvucheo")) { launchSingleTop = true }
        }
    )
)

@Composable
fun TasksScreen(navController: NavController) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(searchQuery) {
        platformItems.filter { item ->
            searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.subtitle.contains(searchQuery, ignoreCase = true)
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

            // 1. Ô TÌM KIẾM CHUẨN FIGMA FRAME 14
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
                                color = TextSecondary
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

            // 2. TIÊU ĐỀ: DÀNH CHO BẠN (FIGMA FRAME 14)
            Text(
                text = "Dành cho bạn",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. GRID 2 CỘT CHUẨN FIGMA
            val chunkedItems = filteredList.chunked(2)
            chunkedItems.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            FigmaGridPlatformCard(
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
private fun FigmaGridPlatformCard(
    item: TaskPlatformItem,
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
            // Icon & Chevron row
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
                    tint = TextSecondary.copy(alpha = 0.6f),
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
                text = item.subtitle,
                fontSize = 12.5.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
