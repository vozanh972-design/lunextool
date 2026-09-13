package com.cayxu.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cayxu.app.R
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.model.VerifyKeyResponse
import com.cayxu.app.ui.navigation.Routes
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val savedKey = remember { securePrefs.getKey().orEmpty() }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F5F8)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1D4ED8))
        }
        return
    }

    val info = uiState.info
    val dateFormat = remember { SimpleDateFormat("EEEE, dd 'tháng' MM", Locale("vi", "VN")) }
    val todayDate = remember { dateFormat.format(Date()).replaceFirstChar { it.uppercase() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F5F8))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        // 1. TOP HEADER: LOGO THƯƠNG HIỆU & NÚT THÔNG BÁO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "AUTOLUNEX",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AUTOLUNEX",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B1730),
                    letterSpacing = 0.5.sp
                )
            }

            // Nút chuông thông báo
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    .clickable { Toast.makeText(context, "Không có thông báo mới", Toast.LENGTH_SHORT).show() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Thông báo",
                    tint = Color(0xFF5B6B85),
                    modifier = Modifier.size(20.dp)
                )
                // Chấm đỏ
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC23B3B))
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. GREETING TEXT
        Text(
            text = "Đây là tổng quan tài khoản của bạn hôm nay, $todayDate.",
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Color(0xFF5B6B85)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 3. THẺ BẢN QUYỀN DIGITAL BANKING (KEY & HẠN SỬ DỤNG)
        DigitalBankingKeyCard(
            key = savedKey,
            info = info,
            onCopyKey = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AutoLunex Key", savedKey))
                Toast.makeText(context, "Đã sao chép mã key", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(26.dp))

        // 4. THAO TÁC NHANH (QUICK ACTIONS GRID 8 NÚT)
        Text(
            text = "Thao tác nhanh",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0B1730)
        )

        Spacer(modifier = Modifier.height(14.dp))

        QuickActionsGrid(navController = navController, context = context)

        Spacer(modifier = Modifier.height(26.dp))

        // 5. HOẠT ĐỘNG GẦN ĐÂY / THỐNG KÊ (RECENT ACTIVITIES)
        RecentActivitiesCard(uiState = uiState, navController = navController)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * THẺ BẢN QUYỀN DIGITAL BANKING (Thay thế số dư bằng Key và Ngày hết hạn)
 */
@Composable
private fun DigitalBankingKeyCard(
    key: String,
    info: VerifyKeyResponse?,
    onCopyKey: () -> Unit
) {
    var isKeyVisible by remember { mutableStateOf(false) }

    // Tính toán ngày hết hạn / số ngày còn lại
    val daysLeft = info?.daysLeft ?: 30
    val expiresAt = info?.expiresAt ?: "Vĩnh viễn"
    val packageName = info?.packageName ?: "AutoLunex VIP"

    val maskedKey = if (key.length > 8) {
        key.take(4) + " •••• •••• " + key.takeLast(4)
    } else {
        "•••• •••• •••• ••••"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A1730),
                        Color(0xFF0F2148),
                        Color(0xFF16305F),
                        Color(0xFF1D4ED8)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column {
            // Hàng trên: Nhãn gói & Trạng thái hoạt động
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CreditCard,
                        contentDescription = null,
                        tint = Color(0xFFA9BEE0),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = packageName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA9BEE0)
                    )
                }

                // Status pill "● Tài khoản hoạt động"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2ED591).copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF35D48A))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tài khoản hoạt động",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7CF0B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Khối chính: Hiển thị Key + Nút ẩn/hiện
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isKeyVisible) key else maskedKey,
                    fontSize = if (isKeyVisible && key.length > 16) 20.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = { isKeyVisible = !isKeyVisible },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = "Ẩn/Hiện",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Divider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))

            // Hàng dưới: Hạn sử dụng & Nút Sao chép
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hạn sử dụng",
                        fontSize = 12.sp,
                        color = Color(0xFFA9BEE0)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (daysLeft > 0) "Còn $daysLeft ngày ($expiresAt)" else expiresAt,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                // Nút Sao chép key
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
                        .clickable { onCopyKey() }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Sao chép",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sao chép",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * LƯỚI THAO TÁC NHANH (8 nút dạng lưới 4x2 chuẩn phong cách Banking)
 */
@Composable
private fun QuickActionsGrid(navController: NavController, context: Context) {
    val items = listOf(
        QuickActionItem("Nhiệm vụ", Icons.Outlined.Assignment) {
            navController.navigate(Routes.TASKS) { launchSingleTop = true }
        },
        QuickActionItem("Ví tiền", Icons.Outlined.AccountBalanceWallet) {
            navController.navigate(Routes.WALLET) { launchSingleTop = true }
        },
        QuickActionItem("Nuôi nick", Icons.Outlined.TrendingUp) {
            navController.navigate(Routes.NURTURE_SETUP) { launchSingleTop = true }
        },
        QuickActionItem("Tiện ích", Icons.Outlined.Widgets) {
            navController.navigate(Routes.UTILITIES) { launchSingleTop = true }
        },
        QuickActionItem("Lịch sử", Icons.Outlined.History) {
            navController.navigate(Routes.WALLET) { launchSingleTop = true }
        },
        QuickActionItem("Tài khoản", Icons.Outlined.Person) {
            navController.navigate(Routes.ACCOUNT) { launchSingleTop = true }
        },
        QuickActionItem("Cài đặt", Icons.Outlined.Settings) {
            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
        },
        QuickActionItem("CSKH", Icons.Outlined.HeadsetMic) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
            runCatching { context.startActivity(intent) }
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.take(4).forEach { item ->
                QuickActionButton(item = item, modifier = Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.drop(4).take(4).forEach { item ->
                QuickActionButton(item = item, modifier = Modifier.weight(1f))
            }
        }
    }
}

private data class QuickActionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionButton(item: QuickActionItem, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .clickable { item.onClick() }
            .padding(vertical = 14.dp, horizontal = 4.dp)
    ) {
        // Khối icon tròn Cyan/Cobalt nhẹ nhàng
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xFFE3FBFD)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = Color(0xFF1D4ED8),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0B1730),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * THẺ HOẠT ĐỘNG GẦN ĐÂY / THỐNG KÊ (Dòng tiền / Hoạt động)
 */
@Composable
private fun RecentActivitiesCard(uiState: HomeUiState, navController: NavController) {
    var selectedFilter by remember { mutableStateOf("all") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hoạt động cày xu",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B1730)
                )

                Text(
                    text = "Xem tất cả →",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D4ED8),
                    modifier = Modifier.clickable {
                        navController.navigate(Routes.WALLET) { launchSingleTop = true }
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bộ lọc: Cả hai / Facebook / TikTok
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFF7F9FB))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
                    .padding(3.dp)
            ) {
                FilterTabButton(
                    label = "Cả hai",
                    isSelected = selectedFilter == "all",
                    onClick = { selectedFilter = "all" }
                )
                FilterTabButton(
                    label = "Facebook",
                    isSelected = selectedFilter == "facebook",
                    onClick = { selectedFilter = "facebook" }
                )
                FilterTabButton(
                    label = "TikTok",
                    isSelected = selectedFilter == "tiktok",
                    onClick = { selectedFilter = "tiktok" }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.recentActivities.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2ED591),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hệ thống sẵn sàng cày xu tự động",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5B6B85)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.recentActivities.forEach { activity ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0xFFE3FBFD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (activity.kind == RecentActivityKind.TIKTOK_LINKED) Icons.Filled.MusicNote else Icons.Filled.Facebook,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activity.title,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0B1730)
                                )
                                Text(
                                    text = activity.subtitle,
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E9BB0)
                                )
                            }
                            Text(
                                text = "Hoàn tất",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1B8A5A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .shadow(if (isSelected) 1.dp else 0.dp, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color(0xFF0B1730) else Color(0xFF5B6B85)
        )
    }
}
