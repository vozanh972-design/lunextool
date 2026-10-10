package com.cayxu.app.ui.screens.home

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.ui.navigation.Routes
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
 * - Đồng bộ 100% phong cách Bento Pastel từ màn Nhiệm vụ:
 *   + Thẻ Hero Tiến độ Periwinkle Lavender kèm Circular Gauge
 *   + Cụm thẻ Bento Pastel (XSMM, GoLike, TTC, TikTok TTC, Nuôi nick ảnh thật)
 * - Bảo toàn 100% logic bản quyền, ViewModel và dữ liệu thực tế
 */
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

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
                .background(Color(0xFFF7F8FA)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    // DỮ LIỆU THẬT CỦA TÀI KHOẢN & BẢN QUYỀN
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

    // DỮ LIỆU CÁC DỊCH VỤ THỰC TẾ
    val isXsmmLogged = remember { XsmmAccountStore.isLoggedIn(context) }
    val xsmmPoints = remember { XsmmAccountStore.getPoints(context) }

    val isGolikeLogged = remember { GolikeAccountsStore.isLoggedIn(context) }
    val golikeBalance = remember { GolikeAccountsStore.getBalance(context) }

    val ttcAccounts = remember { TtcAccountsStore.getAccounts(context) }
    val ttcCoins = remember(ttcAccounts) { ttcAccounts.sumOf { it.coins } }

    val tiktokAccounts = remember { TikTokAccountsStore.getAccounts(context) }
    val fbAccounts = remember { FacebookAccountsStore.getAccounts(context) }
    val readyCount = remember(tiktokAccounts, fbAccounts) {
        tiktokAccounts.count { it.enabled } + fbAccounts.count { it.isLive }
    }
    val totalCount = remember(tiktokAccounts, fbAccounts) {
        tiktokAccounts.size + fbAccounts.size
    }

    // Tổng điểm/xu tích lũy
    val totalAccumulated = xsmmPoints + golikeBalance + ttcCoins
    val formattedTotal = remember(totalAccumulated) {
        if (totalAccumulated >= 1_000_000) {
            String.format(Locale.US, "%.1fM", totalAccumulated / 1_000_000.0)
        } else if (totalAccumulated >= 1_000) {
            String.format(Locale.US, "%.0fk", totalAccumulated / 1_000.0)
        } else {
            totalAccumulated.toString()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 95.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 📌 1. TOP HEADER (Avatar + Lời chào + Tìm kiếm & Chuông thông báo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = effectiveName.take(2).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }

                    Column {
                        Text(
                            text = getGreetingText(),
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$effectiveName 👋",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút tìm kiếm tròn
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                            .clickable { isSearchExpanded = !isSearchExpanded },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Tìm kiếm",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Nút thông báo tròn
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Thông báo",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-3).dp, y = 3.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF43F5E))
                                .border(1.dp, Color.White, CircleShape)
                        )
                    }
                }
            }

            // Thanh tìm kiếm mở rộng
            AnimatedVisibility(visible = isSearchExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Primary),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Tìm kiếm chức năng, dịch vụ...",
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 📌 2. THẺ HERO TIẾN ĐỘ & BẢN QUYỀN (PERIWINKLE LAVENDER)
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDDE3FA)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Dòng trên: Icon box nhỏ + Tiêu đề + Badge ngày còn lại
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.75f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TrendingUp,
                                    contentDescription = null,
                                    tint = Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = packageName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Còn $daysLeft ngày",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dòng giữa: Chỉ số tài khoản máy và Biểu đồ tròn Circular Gauge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val percentReady = if (totalCount > 0) (readyCount * 100 / totalCount) else 95
                            Text(
                                text = "$percentReady%",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$readyCount / $totalCount tài khoản sẵn sàng",
                                fontSize = 12.sp,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Circular Gauge mô phỏng chuẩn ảnh Pinterest
                        Box(
                            modifier = Modifier.size(76.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = Color.White.copy(alpha = 0.45f),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = Color(0xFF2563EB),
                                    startAngle = -90f,
                                    sweepAngle = 310f,
                                    useCenter = false,
                                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "TỔNG XU",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = formattedTotal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Vạch chia và hạn dùng
                    HorizontalDivider(color = Color(0xFF0F172A).copy(alpha = 0.08f), thickness = 1.dp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hạn dùng: $expiresAt",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        Text(
                            text = "Khám phá nhiệm vụ →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            modifier = Modifier.clickable { navController.navigate(Routes.TASKS) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 📌 3. BENTO HÀNG 1: THẺ ẢNH THỰC TẾ (NUÔI TÀI KHOẢN) & THẺ XSMM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thẻ ảnh người thật với nút "Bắt đầu ngay" bên trong
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp)
                        .clickable { navController.navigate(Routes.NURTURE_SETUP) { launchSingleTop = true } }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Nuôi tài khoản",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tự động lướt feed FB & TikTok",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Ảnh thật và nút nổi
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=300&auto=format&fit=crop&q=80")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.15f))
                            )

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.95f),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth(0.9f)
                                    .height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Bắt đầu ngay",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                            }
                        }
                    }
                }

                // Thẻ XSMM Tự Động (Soft Coral Peach Pastel - #FFF1EE)
                BentoStatCard(
                    title = "XSMM",
                    subtitle = "Tăng tương tác",
                    amountText = "${NumberFormat.getInstance(Locale.US).format(xsmmPoints.toLong())} đ",
                    bgColor = Color(0xFFFFF1EE),
                    accentColor = Color(0xFFEA580C),
                    borderColor = Color(0xFFFFDDD6),
                    modifier = Modifier
                        .weight(1f)
                        .height(180.dp),
                    onClick = {
                        XsmmSession.restore(context)
                        if (isXsmmLogged) {
                            navController.navigate(Routes.XSMM_ACCOUNT) { launchSingleTop = true }
                        } else {
                            navController.navigate(Routes.XSMM_LOGIN) { launchSingleTop = true }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 📌 4. BENTO HÀNG 2: GOLIKE (PEACH AMBER) & TUONGTACCHEO (ROSE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thẻ GoLike (Soft Peach Amber - #FFF4E5)
                BentoStatCard(
                    title = "GoLike",
                    subtitle = "Kiếm tiền MXH",
                    amountText = "${NumberFormat.getInstance(Locale.US).format(golikeBalance.toLong())} đ",
                    bgColor = Color(0xFFFFF4E5),
                    accentColor = Color(0xFFD97706),
                    borderColor = Color(0xFFFFE6C7),
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                    onClick = {
                        GolikeSession.restore(context)
                        navController.navigate(Routes.GOLIKE_ACCOUNT) { launchSingleTop = true }
                    }
                )

                // Thẻ Tuongtaccheo (Soft Rose Pastel - #FFEBEF)
                BentoStatCard(
                    title = "Tuongtaccheo",
                    subtitle = "Trao đổi like sub",
                    amountText = "${NumberFormat.getInstance(Locale.US).format(ttcCoins.toLong())} xu",
                    bgColor = Color(0xFFFFEBEF),
                    accentColor = Color(0xFFE11D48),
                    borderColor = Color(0xFFFFD5DE),
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                    onClick = {
                        navController.navigate(Routes.simpleTaskPlatform("Tuongtaccheo")) { launchSingleTop = true }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 📌 5. BENTO HÀNG 3: TIKTOK TTC (MINT) & REG PAGE (SKY)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thẻ TikTok TTC (Soft Mint Pastel - #E5F7F3)
                BentoStatCard(
                    title = "TikTok TTC",
                    subtitle = "Nhận job tự động",
                    amountText = "${tiktokAccounts.size} acc",
                    bgColor = Color(0xFFE5F7F3),
                    accentColor = Color(0xFF0D9488),
                    borderColor = Color(0xFFC7F0E8),
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                    onClick = {
                        navController.navigate(Routes.TUONG_TAC_CHEO_TIKTOK) { launchSingleTop = true }
                    }
                )

                // Thẻ Reg & Chuyển Page (Soft Sky Pastel - #EBF5FF)
                BentoStatCard(
                    title = "Reg Page",
                    subtitle = "Tạo & chuyển quyền",
                    amountText = "Fanpage",
                    bgColor = Color(0xFFEBF5FF),
                    accentColor = Color(0xFF0284C7),
                    borderColor = Color(0xFFCCE4FF),
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                    onClick = {
                        navController.navigate(Routes.REG_AND_TRANSFER_PAGE) { launchSingleTop = true }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * THẺ BENTO PASTEL CHUẨN THIẾT KẾ PINTEREST
 */
@Composable
private fun BentoStatCard(
    title: String,
    subtitle: String,
    amountText: String,
    bgColor: Color,
    accentColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Hàng trên: Huy hiệu tên dịch vụ + Chấm trạng thái live
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = title.take(4).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }

            // Phần giữa: Tên đầy đủ và Số dư nổi bật
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = amountText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    letterSpacing = (-0.5).sp
                )
            }

            // Đáy thẻ: "Check →" chuẩn Pinterest
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Check",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "→",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }
        }
    }
}
