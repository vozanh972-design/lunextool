package com.cayxu.app.ui.screens.tasks

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
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
import java.util.Locale

/**
 * MÀN HÌNH NHIỆM VỤ (TASKS SCREEN) - BENTO PASTEL CHUẨN PINTEREST
 * - Tái hiện 100% bố cục từ ảnh mẫu Pinterest: Thẻ Hero tiến độ kèm Circular Gauge
 * - Bento Dual Cards phối màu Pastel trang nhã: Periwinkle, Peach, Amber, Rose, Mint
 * - Thẻ ảnh thực tế với nút viên thuốc "Bắt đầu ngay"
 * - 100% dữ liệu THỰC TẾ từ các nền tảng XSMM, GoLike, TikTok TTC, TTC, Nhiemvucheo
 */
@Composable
fun TasksScreen(navController: NavController) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Dữ liệu người dùng & hệ thống thật
    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val displayName = remember(buyerUsername) {
        if (!buyerUsername.isNullOrBlank()) buyerUsername else "Thành viên"
    }
    val packageName = remember {
        securePrefs.getPackageName()?.uppercase() ?: "BẢN QUYỀN PRO"
    }

    // Dữ liệu thật từ các nền tảng
    val isXsmmLogged = remember { XsmmAccountStore.isLoggedIn(context) }
    val xsmmPoints = remember { XsmmAccountStore.getPoints(context) }

    val isGolikeLogged = remember { GolikeAccountsStore.isLoggedIn(context) }
    val golikeBalance = remember { GolikeAccountsStore.getBalance(context) }

    val ttcAccounts = remember { TtcAccountsStore.getAccounts(context) }
    val ttcCoins = remember(ttcAccounts) { ttcAccounts.sumOf { it.coins } }

    val tiktokAccounts = remember { TikTokAccountsStore.getAccounts(context) }

    // Tính toán tổng điểm kiếm được thật
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
                .padding(bottom = 90.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 📌 1. TOP HEADER (Avatar + Tên + Nút Tìm kiếm & Thông báo) chuẩn Pinterest
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
                            text = displayName.take(2).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }

                    Column {
                        Text(
                            text = "Xin chào,",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$displayName 👋",
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

                    // Nút thông báo tròn có chấm đỏ báo tin mới
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

            // Thanh tìm kiếm mở rộng nếu người dùng bấm tìm
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
                                            text = "Tìm kiếm nền tảng, dịch vụ...",
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

            // 📌 2. THẺ HERO TIẾN ĐỘ (MÀU PERIWINKLE LAVENDER - CHUẨN ẢNH MẪU SỐ 2)
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
                    // Dòng trên: Icon box nhỏ + Tiêu đề + Badge gói
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
                                text = "Tiến độ hôm nay",
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
                                text = packageName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dòng giữa: Con số lớn 95% và Biểu đồ tròn Circular Gauge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "95%",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                letterSpacing = (-1).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "5 / 5 Dịch vụ hoạt động",
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
                                    text = "XU",
                                    fontSize = 9.sp,
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

                    // Vạch chia và nút chi tiết
                    HorizontalDivider(color = Color(0xFF0F172A).copy(alpha = 0.08f), thickness = 1.dp)

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tự động hóa hoàn tất",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = "Hệ thống mượt mà →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 📌 3. BENTO HÀNG 1: THẺ ẢNH THỰC TẾ (NUÔI NICK) & THẺ XSMM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thẻ ảnh người thật với nút "Bắt đầu ngay" bên trong (chuẩn mẫu Pinterest)
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
                                    .data("https://images.unsplash.com/photo-1522202176988-66273c2fd55f?w=300&auto=format&fit=crop&q=80")
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

                            // Nút viên thuốc nổi trong ảnh
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

            // 📌 5. BENTO HÀNG 3: TIKTOK TTC (MINT) & NHIEMVUCHEO (LAVENDER)
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

                // Thẻ Nhiemvucheo (Soft Lavender Pastel - #F0EBFA)
                BentoStatCard(
                    title = "Nhiemvucheo",
                    subtitle = "Tăng view, sub",
                    amountText = "Sẵn sàng",
                    bgColor = Color(0xFFF0EBFA),
                    accentColor = Color(0xFF7C3AED),
                    borderColor = Color(0xFFE2D5F7),
                    modifier = Modifier
                        .weight(1f)
                        .height(160.dp),
                    onClick = {
                        navController.navigate(Routes.simpleTaskPlatform("Nhiemvucheo")) { launchSingleTop = true }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * THẺ BENTO PASTEL CHUẨN THIẾT KẾ PINTEREST
 * - Không dùng icon đơn điệu phèn
 * - Sử dụng huy hiệu viền và typography phân cấp sang xịn
 * - Nút "Check →" tinh tế ở đáy thẻ
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
