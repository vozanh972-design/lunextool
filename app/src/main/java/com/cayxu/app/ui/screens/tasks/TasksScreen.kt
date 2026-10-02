package com.cayxu.app.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
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
private val FigmaTextSecondary = Color(0xFF8E8E93)
private val FigmaBrandBlue = Color(0xFF0A84FF)
private val FigmaBadgeBg = Color(0xFFEAF4FF)

private data class TaskPlatformItem(
    val id: String,
    val platformName: String,
    val title: String,
    val metadata: String,
    val reward: String,
    val category: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val onClick: (NavController, android.content.Context) -> Unit
)

private val taskItems = listOf(
    TaskPlatformItem(
        id = "xsmm",
        platformName = "XSMM · TikTok & Facebook",
        title = "Tự động tương tác Facebook & TikTok",
        metadata = "Theo dõi · Thích bài · 1 phút",
        reward = "+60 điểm",
        category = "Theo dõi",
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
        platformName = "Golike · Kiếm tiền mạng xã hội",
        title = "Kiếm tiền TikTok, Facebook, Instagram",
        metadata = "Tương tác đa kênh · Nhận thưởng",
        reward = "+80 điểm",
        category = "Tương tác",
        icon = Icons.Filled.Star,
        iconBg = Color(0xFFFEF3C7),
        iconTint = Color(0xFFF59E0B),
        onClick = { navController, context ->
            com.cayxu.app.ui.screens.golike.GolikeSession.restore(context)
            navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "nhiemvucheo",
        platformName = "Nhiemvucheo · Đa kênh",
        title = "Tăng sub, like, view tương tác đa kênh",
        metadata = "Thích bài · 2 phút",
        reward = "+50 điểm",
        category = "Thích bài",
        icon = Icons.Filled.SwapHoriz,
        iconBg = Color(0xFFEAF4FF),
        iconTint = Color(0xFF2563EB),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Nhiemvucheo")) { launchSingleTop = true }
        }
    ),
    TaskPlatformItem(
        id = "tuongtaccheo",
        platformName = "Tuongtaccheo · Mạng xã hội",
        title = "Nhiệm vụ tương tác chéo uy tín",
        metadata = "Theo dõi · 3 phút",
        reward = "+40 điểm",
        category = "Theo dõi",
        icon = Icons.Filled.Favorite,
        iconBg = Color(0xFFFDF2F8),
        iconTint = Color(0xFFEC4899),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Tuongtaccheo")) { launchSingleTop = true }
        }
    )
)

/**
 * MÀN HÌNH KHÁM PHÁ NHIỆM VỤ (TASKS SCREEN)
 * Chuẩn 100% thiết kế Figma "Khám phá nhiệm vụ":
 * - Header: Tiêu đề "Nhiệm vụ" (30.sp), Subtitle "Chọn việc phù hợp với bạn", Icon Tune
 * - Ô tìm kiếm: "Tìm nền tảng hoặc nhiệm vụ", Icon Search, Icon Mic
 * - Thanh bộ lọc Filter Chips: "Tất cả", "Theo dõi", "Thích bài", "Tương tác"
 * - Danh sách nhiệm vụ "Dành cho bạn": Card bo góc 18.dp, Badge "+50 điểm" chuẩn Figma
 * - Khung lưu ý Task Tip: Icon khiên bảo mật + Lời khuyên kiểm duyệt
 */
@Composable
fun TasksScreen(navController: NavController) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Tất cả") }
    val filters = listOf("Tất cả", "Theo dõi", "Thích bài", "Tương tác")

    val filteredList = remember(searchQuery, selectedFilter) {
        taskItems.filter { item ->
            val matchFilter = selectedFilter == "Tất cả" || item.category == selectedFilter
            val matchQuery = searchQuery.isBlank() ||
                    item.platformName.contains(searchQuery, ignoreCase = true) ||
                    item.title.contains(searchQuery, ignoreCase = true)
            matchFilter && matchQuery
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

            // 1. PAGE HEADING (CHUẨN FIGMA)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
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
                        color = FigmaTextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FigmaCardBg)
                        .border(1.dp, FigmaBorder, RoundedCornerShape(12.dp))
                        .clickable { /* Mở bộ lọc nâng cao */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Bộ lọc",
                        tint = FigmaTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. SEARCH FIELD (CHUẨN FIGMA)
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
                        tint = FigmaTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Tìm nền tảng hoặc nhiệm vụ",
                                fontSize = 14.5.sp,
                                color = FigmaTextSecondary
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
                            Text("✕", fontSize = 14.sp, color = FigmaTextSecondary)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Giọng nói",
                            tint = FigmaTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. FILTERS (CHIPS CHUẨN FIGMA)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filterText ->
                    val isSelected = filterText == selectedFilter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) FigmaBrandBlue else FigmaCardBg)
                            .then(
                                if (!isSelected) Modifier.border(1.dp, FigmaBorder, RoundedCornerShape(999.dp))
                                else Modifier
                            )
                            .clickable { selectedFilter = filterText }
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filterText,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else FigmaTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 4. SECTION HEADER: "Dành cho bạn" + "12 nhiệm vụ"
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
                    color = FigmaBrandBlue
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. TASK CARDS (CHUẨN FIGMA 18.dp RADIUS, PADDING 14.dp, BADGE +40 điểm)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredList.forEach { item ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FigmaBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.onClick(navController, context) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon nền tảng
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
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

                            Spacer(modifier = Modifier.width(12.dp))

                            // Chi tiết nhiệm vụ
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.platformName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = FigmaTextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = FigmaTextPrimary,
                                    lineHeight = 20.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.metadata,
                                    fontSize = 12.sp,
                                    color = FigmaTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Reward Badge (+40 điểm / +50 điểm)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(FigmaBadgeBg)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.reward,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FigmaBrandBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 6. TASK TIP (CHUẨN FIGMA)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FigmaBadgeBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = FigmaBrandBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "LunexTool chỉ đề xuất nhiệm vụ đã được kiểm duyệt và phù hợp với tài khoản của bạn.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF3B82F6),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
