package com.cayxu.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.navigation.Routes

private val FigmaBg = Color(0xFFF9FAFB)
private val FigmaCardBg = Color(0xFFFFFFFF)
private val FigmaBorder = Color(0xFFEEF1F5)
private val FigmaTextPrimary = Color(0xFF1C1C1E)
private val FigmaTextSecondary = Color(0xFF6B7280)

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
        id = "nhiemvucheo",
        name = "Nhiemvucheo",
        subtitle = "Nhiệm vụ tăng sub, view, tương tác đa kênh",
        icon = Icons.Filled.SwapHoriz,
        iconBg = Color(0xFFEAF4FF),
        iconTint = Color(0xFF2563EB),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Nhiemvucheo")) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "tuongtaccheo",
        name = "Tuongtaccheo",
        subtitle = "Nhiệm vụ tương tác chéo giữa các nền tảng",
        icon = Icons.Filled.Favorite,
        iconBg = Color(0xFFFDF2F8),
        iconTint = Color(0xFFEC4899),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Tuongtaccheo")) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "tuongtaccheo_tiktok",
        name = "Tuongtaccheo TikTok",
        subtitle = "Nhiệm vụ tương tác chéo TikTok kiếm xu",
        icon = Icons.Filled.MusicNote,
        iconBg = Color(0xFFF1F5F9),
        iconTint = Color(0xFF0F172A),
        onClick = { navController, _ ->
            navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "xsmm",
        name = "XSMM",
        subtitle = "Nhiệm vụ tăng tương tác Facebook, TikTok",
        icon = Icons.Filled.CheckCircle,
        iconBg = Color(0xFFF0FDF4),
        iconTint = Color(0xFF16A34A),
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
        subtitle = "Nhiệm vụ tương tác kiếm tiền mạng xã hội đa kênh",
        icon = Icons.Filled.Star,
        iconBg = Color(0xFFFEF3C7),
        iconTint = Color(0xFFF59E0B),
        onClick = { navController, context ->
            com.cayxu.app.ui.screens.golike.GolikeSession.restore(context)
            navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
        }
    )
)

/**
 * MÀN HÌNH NHIỆM VỤ (TASKS SCREEN)
 * Chuẩn hóa tinh gọn theo chuẩn Figma:
 * - Header tiêu đề "Nhiệm vụ" (30.sp), Subtitle "Chọn việc phù hợp với bạn" (Đã bỏ nút bộ lọc)
 * - Ô tìm kiếm nền tảng & nhiệm vụ
 * - Tiêu đề nhóm "Dành cho bạn"
 * - Card nền tảng tinh gọn: Icon bo tròn 12.dp + Tên + Phụ đề + Mũi tên điều hướng >
 * - Đã xóa sạch 5 thành phần thừa theo yêu cầu
 */
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
            .background(FigmaBg)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. PAGE HEADING (ĐÃ BỎ NÚT BỘ LỌC 3 GẠCH)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Nhiệm vụ",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Chọn việc phù hợp với bạn",
                    fontSize = 14.sp,
                    color = Color(0xFF8E8E93)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. SEARCH FIELD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FigmaCardBg)
                    .border(1.dp, FigmaBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Tìm kiếm",
                        tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Tìm nền tảng hoặc nhiệm vụ",
                                fontSize = 14.5.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.5.sp,
                                color = FigmaTextPrimary,
                                fontWeight = FontWeight.Normal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", fontSize = 14.sp, color = Color(0xFF8E8E93))
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Giọng nói",
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. SECTION HEADER: "Dành cho bạn"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dành cho bạn",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary
                )
                Text(
                    text = "${filteredList.size} nền tảng",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0A84FF)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. PLATFORM CARDS (TINH GỌN CHUẨN FIGMA)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredList.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.onClick(navController, context) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Bên trái: Icon nền tảng bo góc 12.dp
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(item.iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = item.iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Ở giữa: Tên nền tảng + Phụ đề ngắn gọn
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FigmaTextPrimary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = item.subtitle,
                                    fontSize = 13.sp,
                                    color = FigmaTextSecondary,
                                    lineHeight = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Bên phải: Mũi tên điều hướng >
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
