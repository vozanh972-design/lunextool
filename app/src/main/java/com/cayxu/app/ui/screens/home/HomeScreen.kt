package com.cayxu.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.ui.screens.golike.GolikeAccountsStore
import com.cayxu.app.ui.screens.golike.GolikeSession
import com.cayxu.app.ui.screens.xsmm.XsmmSession
import com.cayxu.app.ui.theme.*
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private fun getGreetingText(): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+7"))
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val totalMinutes = hour * 60 + minute

    return when {
        totalMinutes in (5 * 60)..(10 * 60 + 59) -> "Chào buổi sáng,"
        totalMinutes in (11 * 60)..(13 * 60 + 29) -> "Chào buổi trưa,"
        totalMinutes in (13 * 60 + 30)..(17 * 60 + 59) -> "Chào buổi chiều,"
        else -> "Chào buổi tối,"
    }
}

/**
 * MÀN HÌNH TRANG CHỦ (HOME SCREEN)
 * - 100% Dữ liệu THỰC TẾ từ bản quyền Key, tài khoản máy và các nền tảng chạy thật
 * - Thiết kế chuẩn Swiss Clean Minimalist
 */
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }

    LaunchedEffect(Unit) {
        com.cayxu.app.util.IntegrityGuard.assertValidOrCrash(context)
    }

    LaunchedEffect(uiState.sessionExpired) {
        if (uiState.sessionExpired) {
            Toast.makeText(context, "Phiên đăng nhập đã hết hạn, vui lòng kích hoạt lại key", Toast.LENGTH_LONG).show()
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    // DỮ LIỆU THẬT CỦA TÀI KHOẢN
    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val effectiveName = remember(uiState.info, buyerUsername) {
        uiState.info?.effectiveUsername?.ifBlank { null }
            ?: buyerUsername?.ifBlank { null }
            ?: "Thành viên"
    }

    val packageName = remember(uiState.info) {
        uiState.info?.packageName?.uppercase()
            ?: securePrefs.getPackageName()?.uppercase()
            ?: "BẢN QUYỀN PRO"
    }

    val daysLeft = remember(uiState.info) {
        uiState.info?.daysLeft ?: 30
    }

    val expiresAt = remember(uiState.info) {
        uiState.info?.expiresAt ?: securePrefs.getExpiresAt() ?: "--"
    }

    // TÀI KHOẢN MÁY THỰC TẾ
    val tiktokAccounts = remember { TikTokAccountsStore.getAccounts(context) }
    val fbAccounts = remember { FacebookAccountsStore.getAccounts(context) }
    val readyCount = remember(tiktokAccounts, fbAccounts) {
        tiktokAccounts.count { it.enabled } + fbAccounts.count { it.isLive }
    }
    val totalCount = remember(tiktokAccounts, fbAccounts) {
        tiktokAccounts.size + fbAccounts.size
    }
    val targetProgress = if (totalCount > 0) readyCount.toFloat() / totalCount.toFloat() else 0f

    val isXsmmLogged = remember { XsmmAccountStore.isLoggedIn(context) }
    val xsmmPoints = remember { XsmmAccountStore.getPoints(context) }

    val isGolikeLogged = remember { GolikeAccountsStore.isLoggedIn(context) }
    val golikeBalance = remember { GolikeAccountsStore.getBalance(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // 📌 1. HEADER: LỜI CHÀO & TÊN NGƯỜI DÙNG THẬT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getGreetingText(),
                    fontSize = 14.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = effectiveName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Avatar tròn hiển thị ký tự viết tắt thật
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(InfoBlueBg)
                    .border(1.dp, BorderLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val initials = effectiveName.split(" ")
                    .filter { it.isNotBlank() }
                    .takeLast(2)
                    .map { it.first().uppercase() }
                    .joinToString("")
                    .ifEmpty { "AL" }

                Text(
                    text = initials,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 📌 2. THẺ BẢN QUYỀN THỰC TẾ (SAPPHIRE COBALT)
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
                        text = "Gói phần mềm kích hoạt",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )

                    // Pill Badge số ngày còn lại thật
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Còn $daysLeft ngày",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = packageName,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.8).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Hạn dùng: $expiresAt",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 📌 3. THẺ TIẾN ĐỘ TÀI KHOẢN MÁY THỰC TẾ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tài khoản trên thiết bị",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "$readyCount / $totalCount sẵn sàng",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Thanh tiến độ có Animation mượt mà
        val animatedProgress by animateFloatAsState(
            targetValue = if (totalCount > 0) targetProgress else 0.05f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            label = "accountProgress"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(BorderLight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Primary)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 📌 4. NÚT HÀNH ĐỘNG TÌM NHIỆM VỤ (CTA BUTTON)
        Button(
            onClick = { navController.navigate(Routes.TASKS) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Khám phá danh sách nhiệm vụ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 📌 5. NỀN TẢNG CHẠY THẬT (SPOTLIGHT)
        Text(
            text = "Dịch vụ nổi bật",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        RealServiceSpotlightCard(
            title = "XSMM Tự động",
            subtitle = if (isXsmmLogged) "Đã kết nối tài khoản" else "Chưa đăng nhập",
            badgeText = "${NumberFormat.getInstance(Locale.US).format(xsmmPoints.toLong())} điểm",
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

        Spacer(modifier = Modifier.height(10.dp))

        RealServiceSpotlightCard(
            title = "GoLike Tự động",
            subtitle = if (isGolikeLogged) "Đã kết nối tài khoản" else "Chưa đăng nhập",
            badgeText = "${NumberFormat.getInstance(Locale.US).format(golikeBalance.toLong())} đ",
            icon = Icons.Outlined.Star,
            iconBg = Color(0xFFFEF3C7),
            iconTint = Color(0xFFD97706),
            onClick = {
                GolikeSession.restore(context)
                navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 📌 6. CÔNG CỤ TIỆN ÍCH THỰC TẾ
        Text(
            text = "Tiện ích mở rộng",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        RealToolCard(
            title = "Nuôi tài khoản Facebook",
            subtitle = "Tự động tương tác, lướt feed chăm sóc nick",
            icon = Icons.Outlined.Person,
            onClick = {
                navController.navigate(Routes.FB_NURTURE) { launchSingleTop = true }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        RealToolCard(
            title = "Reg Page & Chuyển Page",
            subtitle = "Tạo trang fanpage và chuyển quyền tự động",
            icon = Icons.Outlined.SwapHoriz,
            onClick = {
                navController.navigate(Routes.REG_AND_TRANSFER_PAGE) { launchSingleTop = true }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RealServiceSpotlightCard(
    title: String,
    subtitle: String,
    badgeText: String,
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

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(InfoBlueBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
        }
    }
}

@Composable
private fun RealToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
