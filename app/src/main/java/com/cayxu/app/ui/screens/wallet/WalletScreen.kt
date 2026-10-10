package com.cayxu.app.ui.screens.wallet

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.screens.golike.GolikeAccountsStore
import com.cayxu.app.ui.screens.golike.GolikeSession
import com.cayxu.app.ui.screens.xsmm.XsmmAccountStore
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import com.cayxu.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

/**
 * MÀN HÌNH VÍ ĐIỂM (WALLET SCREEN)
 * - 100% Dữ liệu THỰC TẾ từ các nền tảng XSMM, GoLike, Tuongtaccheo
 * - Thiết kế chuẩn Swiss Clean Minimalist
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(navController: NavController) {
    val context = LocalContext.current
    var showWithdrawDialog by remember { mutableStateOf(false) }

    // Đọc số liệu THỰC TẾ từ các kho lưu trữ
    val xsmmPoints = remember { XsmmAccountStore.getPoints(context) }
    val isXsmmLogged = remember { XsmmAccountStore.isLoggedIn(context) }
    val xsmmUsername = remember { XsmmAccountStore.getUsername(context) }

    val golikeBalance = remember { GolikeAccountsStore.getBalance(context) }
    val isGolikeLogged = remember { GolikeAccountsStore.isLoggedIn(context) }
    val golikeUsername = remember { GolikeAccountsStore.getUsername(context) }

    val ttcAccounts = remember { TtcAccountsStore.getAccounts(context) }
    val ttcCoins = remember(ttcAccounts) { ttcAccounts.sumOf { it.coins } }

    val totalPoints = xsmmPoints + golikeBalance + ttcCoins
    val formattedTotal = remember(totalPoints) {
        NumberFormat.getInstance(Locale.US).format(totalPoints)
    }

    // Hoạt động tài khoản thực tế
    val tiktokAccounts = remember { TikTokAccountsStore.getAccounts(context) }
    val facebookAccounts = remember { FacebookAccountsStore.getAccounts(context) }

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

            // 1. TIÊU ĐỀ
            Text(
                text = "Ví điểm",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. THẺ TỔNG ĐIỂM THẬT (SAPPHIRE COBALT)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tổng điểm & xu tích lũy",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium
                        )

                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = formattedTotal,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tổng hợp từ XSMM, GoLike và Tuongtaccheo",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. HAI NÚT HÀNH ĐỘNG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = { showWithdrawDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rút điểm",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                        runCatching { context.startActivity(intent) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = CardWhite),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShoppingCart,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nạp / Mua key",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 4. TIÊU ĐỀ: CHI TIẾT TỪNG NỀN TẢNG
            Text(
                text = "Chi tiết số dư nền tảng",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Danh sách số dư thật từng dịch vụ
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Thẻ XSMM
                PlatformBalanceCard(
                    title = "XSMM",
                    subtitle = if (isXsmmLogged) "@$xsmmUsername (Đã kết nối)" else "Chưa đăng nhập tài khoản",
                    balanceText = "${NumberFormat.getInstance(Locale.US).format(xsmmPoints)} điểm",
                    icon = Icons.Outlined.CheckCircle,
                    iconBg = Color(0xFFEFF6FF),
                    iconTint = Color(0xFF2563EB),
                    onClick = {
                        XsmmSession.restore(context)
                        if (isXsmmLogged) {
                            navController.navigate(Routes.XSMM_ACCOUNT) { launchSingleTop = true }
                        } else {
                            navController.navigate(Routes.XSMM_LOGIN) { launchSingleTop = true }
                        }
                    }
                )

                // Thẻ GoLike
                PlatformBalanceCard(
                    title = "GoLike",
                    subtitle = if (isGolikeLogged) "@$golikeUsername (Đã kết nối)" else "Chưa đăng nhập tài khoản",
                    balanceText = "${NumberFormat.getInstance(Locale.US).format(golikeBalance)} đ",
                    icon = Icons.Outlined.Star,
                    iconBg = Color(0xFFFEF3C7),
                    iconTint = Color(0xFFD97706),
                    onClick = {
                        GolikeSession.restore(context)
                        navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
                    }
                )

                // Thẻ TTC
                PlatformBalanceCard(
                    title = "Tuongtaccheo",
                    subtitle = "${ttcAccounts.size} tài khoản đã thêm",
                    balanceText = "${NumberFormat.getInstance(Locale.US).format(ttcCoins)} xu",
                    icon = Icons.Outlined.FavoriteBorder,
                    iconBg = Color(0xFFFDF2F8),
                    iconTint = Color(0xFFDB2777),
                    onClick = {
                        navController.navigate(Routes.simpleTaskPlatform("Tuongtaccheo")) { launchSingleTop = true }
                    }
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 5. TIÊU ĐỀ: TÀI KHOẢN VÀ HOẠT ĐỘNG
            Text(
                text = "Tài khoản liên kết",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (tiktokAccounts.isEmpty() && facebookAccounts.isEmpty()) {
                // Empty State chuẩn Swiss
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(InfoBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Chưa có tài khoản liên kết",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Thêm tài khoản TikTok hoặc Facebook để bắt đầu cày điểm thưởng.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    tiktokAccounts.take(3).forEach { acc ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF1F5F9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF0F172A),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "@${acc.handle.ifBlank { acc.displayName.ifBlank { "TikTok" } }}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Tài khoản TikTok",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = "Đang kết nối",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Primary
                                )
                            }
                        }
                    }

                    facebookAccounts.take(2).forEach { acc ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            border = BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFEFF6FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ThumbUp,
                                            contentDescription = null,
                                            tint = Color(0xFF1877F2),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = acc.name.ifBlank { acc.uid },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Tài khoản Facebook",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = if (acc.isLive) "Hoạt động" else "Cần kiểm tra",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (acc.isLive) Primary else DangerRed
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Dialog hướng dẫn rút tiền
        if (showWithdrawDialog) {
            AlertDialog(
                onDismissRequest = { showWithdrawDialog = false },
                title = {
                    Text(
                        text = "Rút điểm / Xu",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "Số dư được tích lũy trực tiếp tại các nền tảng XSMM, GoLike và Tuongtaccheo. Vui lòng bấm vào từng nền tảng tương ứng để thực hiện rút tiền về MoMo hoặc Ngân hàng của bạn.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showWithdrawDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Đã hiểu", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
private fun PlatformBalanceCard(
    title: String,
    subtitle: String,
    balanceText: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = balanceText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
        }
    }
}
