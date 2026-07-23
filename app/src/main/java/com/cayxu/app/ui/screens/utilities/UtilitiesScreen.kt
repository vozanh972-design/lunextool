package com.cayxu.app.ui.screens.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.navigation.goHome
import com.cayxu.app.ui.theme.*

private data class UtilityTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

private val utilityTools = listOf(
    UtilityTool(
        "Liên kết tài khoản",
        "Thêm & quản lý tài khoản TikTok, Facebook",
        Icons.Filled.Link,
        listOf(Color(0xFF418DFC), Color(0xFF7950F6))
    ),
    UtilityTool(
        "Kiểm tra tài khoản",
        "Check nhanh trạng thái hoạt động",
        Icons.Filled.FactCheck,
        listOf(Color(0xFF34D399), Color(0xFF059669))
    ),
    UtilityTool(
        "Bạn bè & Giới thiệu",
        "Mời bạn bè, nhận thưởng giới thiệu",
        Icons.Filled.Diversity3,
        listOf(Color(0xFFF472B6), Color(0xFFEC4899))
    ),
    UtilityTool(
        "Mã máy & Thiết bị",
        "Xem thông tin thiết bị đang dùng",
        Icons.Filled.PhoneAndroid,
        listOf(Color(0xFFFBBF24), Color(0xFFF97316))
    ),
    UtilityTool(
        "Trợ giúp & Hỗ trợ",
        "Câu hỏi thường gặp, liên hệ hỗ trợ",
        Icons.Filled.SupportAgent,
        listOf(Color(0xFF9D5CE8), Color(0xFF7C3AED))
    ),
    UtilityTool(
        "Cài đặt",
        "Cấu hình ứng dụng, giao diện",
        Icons.Filled.Tune,
        listOf(Color(0xFF64748B), Color(0xFF334155))
    )
)

@Composable
fun UtilitiesScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.goHome() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Về trang chủ", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("Tiện ích", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Mọi công cụ hữu ích của CayXu, gói gọn trong một nơi",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(20.dp))

        utilityTools.chunked(2).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowTools.forEach { tool ->
                    UtilityToolCard(
                        tool = tool,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (tool.title) {
                                "Cài đặt" -> navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                                else -> { /* TODO: gắn màn hình tương ứng khi backend/tính năng sẵn sàng */ }
                            }
                        }
                    )
                }
                // Nếu hàng cuối lẻ 1 ô, thêm khoảng trống để giữ lưới cân đối 2 cột.
                if (rowTools.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(78.dp))
    }
}

@Composable
private fun UtilityToolCard(tool: UtilityTool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .height(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(tool.gradientColors))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(tool.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(
                        tool.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tool.subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
