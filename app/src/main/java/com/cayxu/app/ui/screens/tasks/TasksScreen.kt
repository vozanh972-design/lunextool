package com.cayxu.app.ui.screens.tasks

import android.content.Context
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
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.screens.golike.GolikeAccountsStore
import com.cayxu.app.ui.screens.golike.GolikeSession
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import com.cayxu.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

private data class RealTaskPlatform(
    val id: String,
    val name: String,
    val description: String,
    val badgeProducer: (Context) -> String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val onClick: (NavController, Context) -> Unit
)

private val realPlatforms = listOf(
    RealTaskPlatform(
        id = "xsmm",
        name = "XSMM",
        description = "Tăng tương tác Facebook, TikTok đa kênh tự động.",
        badgeProducer = { ctx ->
            if (XsmmAccountStore.isLoggedIn(ctx)) {
                val pts = XsmmAccountStore.getPoints(ctx)
                "${NumberFormat.getInstance(Locale.US).format(pts.toLong())} điểm"
            } else {
                "Chưa kết nối"
            }
        },
        icon = Icons.Outlined.CheckCircle,
        iconBg = Color(0xFFEFF6FF),
        iconTint = Color(0xFF2563EB),
        onClick = { navController, ctx ->
            XsmmSession.restore(ctx)
            if (XsmmAccountStore.isLoggedIn(ctx)) {
                navController.navigate(Routes.XSMM_ACCOUNT) { launchSingleTop = true }
            } else {
                navController.navigate(Routes.XSMM_LOGIN) { launchSingleTop = true }
            }
        }
    ),
    RealTaskPlatform(
        id = "golike",
        name = "GoLike",
        description = "Làm nhiệm vụ kiếm tiền mạng xã hội TikTok, Instagram.",
        badgeProducer = { ctx ->
            if (GolikeAccountsStore.isLoggedIn(ctx)) {
                val bal = GolikeAccountsStore.getBalance(ctx)
                "${NumberFormat.getInstance(Locale.US).format(bal.toLong())} đ"
            } else {
                "Chưa kết nối"
            }
        },
        icon = Icons.Outlined.Star,
        iconBg = Color(0xFFFEF3C7),
        iconTint = Color(0xFFD97706),
        onClick = { navController, ctx ->
            GolikeSession.restore(ctx)
            navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
        }
    ),
    RealTaskPlatform(
        id = "tuongtaccheo_tiktok",
        name = "TikTok TTC",
        description = "Tự động nhận job và chạy tương tác TikTok qua Accessibility.",
        badgeProducer = { ctx ->
            val count = TikTokAccountsStore.getAccounts(ctx).size
            if (count > 0) "$count tài khoản" else "Chưa có acc"
        },
        icon = Icons.Outlined.PlayCircle,
        iconBg = Color(0xFFF1F5F9),
        iconTint = Color(0xFF0F172A),
        onClick = { navController, _ ->
            navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK) { launchSingleTop = true }
        }
    ),
    RealTaskPlatform(
        id = "tuongtaccheo",
        name = "Tuongtaccheo",
        description = "Trao đổi sub, like, tương tác đa kênh tự động.",
        badgeProducer = { ctx ->
            val count = TtcAccountsStore.getAccounts(ctx).size
            if (count > 0) "$count tài khoản" else "Sẵn sàng"
        },
        icon = Icons.Outlined.FavoriteBorder,
        iconBg = Color(0xFFFDF2F8),
        iconTint = Color(0xFFDB2777),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Tuongtaccheo")) { launchSingleTop = true }
        }
    ),
    RealTaskPlatform(
        id = "nhiemvucheo",
        name = "Nhiemvucheo",
        description = "Tăng sub, view, tương tác đa kênh tự động nhanh chóng.",
        badgeProducer = { _ -> "Sẵn sàng" },
        icon = Icons.Outlined.Sync,
        iconBg = Color(0xFFE0E7FF),
        iconTint = Color(0xFF4F46E5),
        onClick = { navController, _ ->
            navController.navigate(Routes.simpleTaskPlatform("Nhiemvucheo")) { launchSingleTop = true }
        }
    )
)

/**
 * MÀN HÌNH DANH SÁCH NHIỆM VỤ (TASKS SCREEN)
 * - 100% Chức năng THỰC TẾ của AutoLunex
 * - Thiết kế chuẩn Swiss Clean Minimalist
 */
@Composable
fun TasksScreen(navController: NavController) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(searchQuery) {
        realPlatforms.filter { item ->
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

            // 1. Ô TÌM KIẾM CHUẨN SWISS
            Surface(
                shape = RoundedCornerShape(14.dp),
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
                                text = "Tìm dịch vụ, nền tảng nhiệm vụ...",
                                fontSize = 15.sp,
                                color = TextSecondary.copy(alpha = 0.7f)
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

            // 2. TIÊU ĐỀ SECTION
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nền tảng nhiệm vụ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.3).sp
                )

                Text(
                    text = "${filteredList.size} dịch vụ",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. GRID 2 CỘT HIỂN THỊ CÁC DỊCH VỤ THẬT
            val chunkedItems = filteredList.chunked(2)
            chunkedItems.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            RealPlatformGridCard(
                                item = item,
                                badgeText = item.badgeProducer(context),
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
private fun RealPlatformGridCard(
    item: RealTaskPlatform,
    badgeText: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
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
                    tint = TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = item.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.description,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Badge trạng thái thật
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(InfoBlueBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
