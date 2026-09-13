package com.cayxu.app.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cayxu.app.R
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.model.VerifyKeyResponse
import com.cayxu.app.ui.navigation.Routes

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val savedKey = remember { securePrefs.getKey().orEmpty() }

    var showNotifDialog by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F5F8))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        // 1. TOP HEADER: LOGO THƯƠNG HIỆU & NÚT CHUÔNG THÔNG BÁO
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "AUTOLUNEX",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(38.dp)
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

            // Nút chuông thông báo (bấm vào hiện popup)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    .clickable { showNotifDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Thông báo",
                    tint = Color(0xFF5B6B85),
                    modifier = Modifier.size(20.dp)
                )
                // Chấm đỏ thông báo
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

        Spacer(modifier = Modifier.height(14.dp))

        // 2. THẺ BẢN QUYỀN DIGITAL BANKING (Thu nhỏ chiều dọc, đẩy sát lên trên)
        DigitalBankingKeyCard(
            key = savedKey,
            info = info,
            onCopyKey = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("AutoLunex Key", savedKey))
                Toast.makeText(context, "Đã sao chép mã key thành công", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 3. THAO TÁC NHANH (QUICK ACTIONS GRID 8 NÚT)
        Text(
            text = "Thao tác nhanh",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0B1730)
        )

        Spacer(modifier = Modifier.height(12.dp))

        QuickActionsGrid(navController = navController, context = context)

        Spacer(modifier = Modifier.height(20.dp))

        // 4. HOẠT ĐỘNG GẦN ĐÂY / THỐNG KÊ
        RecentActivitiesCard(uiState = uiState, navController = navController)

        Spacer(modifier = Modifier.height(18.dp))
    }

    // POPUP THÔNG BÁO HỆ THỐNG
    if (showNotifDialog) {
        AlertDialog(
            onDismissRequest = { showNotifDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF1D4ED8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thông báo hệ thống",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NotifPopupItem(
                        icon = Icons.Outlined.VerifiedUser,
                        title = "Đăng nhập thành công",
                        time = "Vừa xong",
                        desc = "Bạn đã đăng nhập thành công vào AutoLunex."
                    )
                    NotifPopupItem(
                        icon = Icons.Outlined.CardMembership,
                        title = "Bản quyền VIP kích hoạt",
                        time = "Đang hiệu lực",
                        desc = "Key bản quyền đang hoạt động bình thường trên thiết bị."
                    )
                    NotifPopupItem(
                        icon = Icons.Outlined.CheckCircle,
                        title = "Tài khoản mạng xã hội",
                        time = "Hôm nay",
                        desc = "Hệ thống kết nối và kiểm tra bảo mật ổn định 100%."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifDialog = false }) {
                    Text("Đóng", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun NotifPopupItem(
    icon: ImageVector,
    title: String,
    time: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE3FBFD)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF1D4ED8),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B1730)
                )
                Text(
                    text = time,
                    fontSize = 11.sp,
                    color = Color(0xFF8E9BB0)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Color(0xFF5B6B85)
            )
        }
    }
}

/**
 * THẺ BẢN QUYỀN DIGITAL BANKING (Thu gọn chiều dọc, layout nút Sao chép nằm ngang chuẩn)
 */
@Composable
private fun DigitalBankingKeyCard(
    key: String,
    info: VerifyKeyResponse?,
    onCopyKey: () -> Unit
) {
    var isKeyVisible by remember { mutableStateOf(false) }

    val daysLeft = info?.daysLeft ?: 30
    val expiresAt = info?.expiresAt ?: "2026-10-15"
    val packageName = info?.packageName ?: "PRO"

    // Format hiển thị key ẩn: LUNE •••• •••• P30D
    val maskedKey = remember(key) {
        if (key.length >= 8) {
            "${key.take(4)} •••• •••• ${key.takeLast(4)}"
        } else if (key.isNotEmpty()) {
            "${key.take(2)} •••• •••• ${key.takeLast(2)}"
        } else {
            "•••• •••• •••• ••••"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
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
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column {
            // Hàng 1: Tên gói & Trạng thái hoạt động
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
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = packageName,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA9BEE0)
                    )
                }

                // Status pill "● Tài khoản hoạt động"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2ED591).copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF35D48A))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Tài khoản hoạt động",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7CF0B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hàng 2: Mã Key + Nút Con mắt Ẩn/Hiện
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isKeyVisible) key else maskedKey,
                    fontSize = if (isKeyVisible && key.length > 16) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { isKeyVisible = !isKeyVisible },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = "Ẩn/Hiện",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(color = Color.White.copy(alpha = 0.12f), thickness = 0.8.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Hàng 3: Hạn sử dụng & Nút Sao chép chuẩn hàng ngang
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hạn sử dụng",
                        fontSize = 11.sp,
                        color = Color(0xFFA9BEE0)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (daysLeft > 0) "Còn $daysLeft ngày ($expiresAt)" else expiresAt,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Nút Sao chép (Chuẩn hàng ngang, không bao giờ bị vỡ chữ dọc)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                        .clickable { onCopyKey() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Sao chép",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Sao chép",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * LƯỚI THAO TÁC NHANH (8 nút dạng lưới 4x2)
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(4).forEach { item ->
                QuickActionButton(item = item, modifier = Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(15.dp))
            .clickable { item.onClick() }
            .padding(vertical = 12.dp, horizontal = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE3FBFD)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = Color(0xFF1D4ED8),
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = item.label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF0B1730),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * THẺ HOẠT ĐỘNG GẦN ĐÂY / THỐNG KÊ
 */
@Composable
private fun RecentActivitiesCard(uiState: HomeUiState, navController: NavController) {
    var selectedFilter by remember { mutableStateOf("all") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hoạt động cày xu",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0B1730)
                )

                Text(
                    text = "Xem tất cả →",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D4ED8),
                    modifier = Modifier.clickable {
                        navController.navigate(Routes.WALLET) { launchSingleTop = true }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bộ lọc
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF7F9FB))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp))
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

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.recentActivities.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2ED591),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Hệ thống sẵn sàng cày xu tự động",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5B6B85)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.recentActivities.forEach { activity ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE3FBFD)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (activity.kind == RecentActivityKind.TIKTOK_LINKED) Icons.Filled.MusicNote else Icons.Filled.Facebook,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activity.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0B1730)
                                )
                                Text(
                                    text = activity.subtitle,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF8E9BB0)
                                )
                            }
                            Text(
                                text = "Hoàn tất",
                                fontSize = 11.5.sp,
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
            .clip(RoundedCornerShape(15.dp))
            .background(if (isSelected) Color.White else Color.Transparent)
            .shadow(if (isSelected) 1.dp else 0.dp, RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color(0xFF0B1730) else Color(0xFF5B6B85)
        )
    }
}
