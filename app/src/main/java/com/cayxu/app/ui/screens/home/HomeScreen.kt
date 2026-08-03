package com.cayxu.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.*

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Điểm kiểm tra CỨNG thứ 2, độc lập với CayXuApp.onCreate() - cố tình đặt ở đây thay vì
    // gộp chung 1 chỗ, để việc patch/xoá điểm gọi trong CayXuApp không đủ vô hiệu hoá toàn
    // bộ cơ chế (ai vào được tới Home tức đã có key hợp lệ nên fingerprint phải khớp).
    LaunchedEffect(Unit) {
        com.cayxu.app.util.IntegrityGuard.assertValidOrCrash(context)
    }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            Toast.makeText(context, "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại", Toast.LENGTH_LONG).show()
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    val info = uiState.info

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
            // Header: Mã máy
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(com.cayxu.app.R.drawable.ic_app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("CAYXU", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(uiState.androidId, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                }
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("android_id", uiState.androidId))
                    Toast.makeText(context, "Đã sao chép mã máy", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Sao chép", tint = Primary)
                }
                IconButton(onClick = { /* TODO: thông báo */ }) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(16.dp))

            if (info != null) {
                PremiumCard(info)
            }

            Spacer(Modifier.height(20.dp))

            // Menu 4 nút, xếp thành 1 HÀNG DUY NHẤT (trước đây là lưới 2x2, giờ gọn hơn).
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuButton(
                    icon = Icons.Filled.Widgets,
                    label = "Tiện ích",
                    accentColor = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f)
                ) { navController.navigate(com.cayxu.app.ui.navigation.Routes.UTILITIES) { launchSingleTop = true } }
                MenuButton(
                    icon = Icons.Filled.Settings,
                    label = "Cài đặt",
                    accentColor = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f)
                ) { navController.navigate(com.cayxu.app.ui.navigation.Routes.SETTINGS) }
                MenuButton(
                    icon = Icons.Filled.Assignment,
                    label = "Nhiệm vụ",
                    accentColor = Color(0xFF16A34A),
                    modifier = Modifier.weight(1f)
                ) { navController.navigate("tasks") { launchSingleTop = true } }
                MenuButton(
                    icon = Icons.Filled.AccountBalanceWallet,
                    label = "Ví",
                    accentColor = Color(0xFFF97316),
                    modifier = Modifier.weight(1f)
                ) { navController.navigate("wallet") { launchSingleTop = true } }
            }

            Spacer(Modifier.height(20.dp))

            IncomeCard(navController)

            Spacer(Modifier.height(24.dp))

            SectionHeader("Hoạt động gần đây") { navController.navigate("wallet") { launchSingleTop = true } }
            Spacer(Modifier.height(10.dp))
            if (uiState.recentActivities.isEmpty()) {
                EmptyActivityCard()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.recentActivities.forEach { activity ->
                        val icon = when (activity.kind) {
                            RecentActivityKind.TIKTOK_LINKED -> Icons.Filled.MusicNote
                            RecentActivityKind.FACEBOOK_LINKED -> Icons.Filled.Facebook
                        }
                        val color = if (activity.isHealthy) SuccessGreen else DangerRed
                        HistoryRow(icon, color, activity.title, activity.subtitle, "", activity.timeLabel, activity.isHealthy)
                    }
                }
            }

            Spacer(Modifier.height(90.dp))
        }
}

@Composable
private fun PremiumCard(info: com.cayxu.app.data.model.VerifyKeyResponse) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF418DFC), Color(0xFF7950F6))))
        ) {
            Image(
                painter = painterResource(com.cayxu.app.R.drawable.ic_crown_premium),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 22.dp, y = (-18).dp)
                    .size(width = 150.dp, height = 130.dp)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Text("GÓI", color = Color(0xFFE0D4FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text("Premium", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF22C55E).copy(alpha = 0.2f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Còn hạn", color = Color(0xFF4ADE80), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${info.daysLeft ?: 0} ngày", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hết hạn: ${info.expiresAt ?: "--"}",
                    color = Color(0xFFE5DCFF),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .heightIn(min = 88.dp)
            .background(CardWhite, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(accentColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = accentColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun IncomeCard(navController: NavController) {
    // "Thu nhập hôm nay" + biểu đồ theo nền tảng giờ lấy THẬT từ GolikeSession.todayIncome/
    // platformStats (đọc từ GET /api/statistics/report) - không còn số liệu mẫu/biểu đồ giả
    // 7 ngày nữa. Biểu đồ giờ là cột ngang theo TỪNG nền tảng (facebook/tiktok/...) vì GoLike
    // trả thu nhập hôm nay theo nền tảng, không trả theo từng ngày trong quá khứ.
    val isGolikeLoggedIn by com.cayxu.app.ui.screens.golike.GolikeSession.isLoggedIn
    val todayIncome by com.cayxu.app.ui.screens.golike.GolikeSession.todayIncome
    val platformStats by com.cayxu.app.ui.screens.golike.GolikeSession.platformStats

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Thu nhập hôm nay", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))

            if (!isGolikeLoggedIn) {
                Text(
                    "Cần đăng nhập GoLike để xem thu nhập hôm nay",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        navController.navigate(com.cayxu.app.ui.navigation.Routes.GOLIKE_LOGIN) { launchSingleTop = true }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Đăng nhập GoLike")
                }
            } else {
                Text("+${todayIncome}đ", color = SuccessGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                val earningPlatforms = platformStats.filter { it.pendingCoin > 0 }.sortedByDescending { it.pendingCoin }
                if (earningPlatforms.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    val maxCoin = earningPlatforms.first().pendingCoin.coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        earningPlatforms.forEach { stat ->
                            PlatformIncomeBar(
                                platform = com.cayxu.app.ui.screens.golike.golikePlatformDisplayName(stat.platform),
                                color = com.cayxu.app.ui.screens.golike.golikePlatformColor(stat.platform),
                                amount = stat.pendingCoin,
                                fraction = stat.pendingCoin.toFloat() / maxCoin.toFloat()
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("Chưa có nền tảng nào có thu nhập hôm nay", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

/** 1 dòng trong biểu đồ cột ngang - tên nền tảng + thanh màu dài theo tỉ lệ + số tiền thật. */
@Composable
private fun PlatformIncomeBar(platform: String, color: Color, amount: Long, fraction: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(platform, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("+${amount}đ", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(8.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Row(
            modifier = Modifier.clickable { onSeeAll() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Xem tất cả", color = Primary, fontSize = 13.sp)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TaskRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, reward: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).background(InfoBlueBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Text(reward, color = SuccessGreen, fontSize = 12.sp)
            }
            Button(
                onClick = { /* TODO: thực hiện nhiệm vụ */ },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = InfoBlueBg, contentColor = Primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Làm ngay", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EmptyActivityCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("Chưa có hoạt động nào gần đây", color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HistoryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, title: String, subtitle: String, amount: String, time: String, isPositive: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (amount.isNotBlank()) {
                    Text(amount, color = if (isPositive) SuccessGreen else DangerRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text(time, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
