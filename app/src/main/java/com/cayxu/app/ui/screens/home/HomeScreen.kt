package com.cayxu.app.ui.screens.home

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.util.DeviceUtils
import java.util.Calendar
import java.util.TimeZone

private val FigmaScreenBg = Color(0xFFF8FAFC)
private val FigmaCardBg = Color(0xFFFFFFFF)
private val FigmaBorder = Color(0xFFF1F5F9)
private val FigmaTextPrimary = Color(0xFF111827)
private val FigmaTextSecondary = Color(0xFF6B7280)
private val FigmaTextMuted = Color(0xFF9CA3AF)
private val FigmaBlue = Color(0xFF0284C7)
private val FigmaPurple = Color(0xFF7C3AED)
private val FigmaPurpleLight = Color(0xFFF3E8FF)
private val FigmaBlueLight = Color(0xFFE0F2FE)

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
                .background(FigmaScreenBg),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = FigmaBlue)
        }
        return
    }

    val buyerUsername = remember { securePrefs.getBuyerUsername() }
    val deviceId = remember { DeviceUtils.getAndroidId(context) }
    val username = buyerUsername?.trim().orEmpty()
    val displayName = if (username.isNotBlank()) username else "Người dùng"
    val avatarSeed = if (username.isNotBlank()) username else deviceId
    val avatarUrl = remember(avatarSeed) {
        "https://api.dicebear.com/9.x/bottts-neutral/png?seed=${Uri.encode(avatarSeed)}&size=160"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FigmaScreenBg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // 📌 1. HEADER: LỜI CHÀO THÔNG MINH (THEO GIỜ VN) & USERNAME + AVATAR DICEBEAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getGreetingText(),
                    fontSize = 15.sp,
                    color = FigmaTextSecondary,
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayName,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Avatar Dicebear bo tròn 48.dp, viền xám sáng sang trọng
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0))
                    .border(1.5.dp, Color(0xFFE2E8F0), CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 📌 2. THẺ THỐNG KÊ ĐIỂM (BALANCE CARD CHUẨN FIGMA)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, FigmaBorder),
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
                        text = "Điểm hiện có",
                        fontSize = 14.sp,
                        color = FigmaTextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    // Badge bo góc màu tím nhạt "+240 tuần này"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FigmaPurpleLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = FigmaPurple,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+240 tuần này",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FigmaPurple
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "1.280",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary,
                    letterSpacing = (-1).sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "điểm Nexa",
                    fontSize = 13.5.sp,
                    color = FigmaTextMuted,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 📌 3. THẺ TIẾN ĐỘ HÔM NAY (DAILY PROGRESS CARD)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            border = BorderStroke(1.dp, FigmaBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = FigmaBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tiến độ hôm nay",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FigmaTextPrimary
                        )
                    }

                    Text(
                        text = "3/5 nhiệm vụ",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FigmaBlue
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Thanh LinearProgressIndicator bo tròn
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(FigmaBlue)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📌 4. NÚT HÀNH ĐỘNG LỚN (CTA BUTTON: TÌM NHIỆM VỤ PHÙ HỢP)
        Button(
            onClick = { navController.navigate(Routes.TASKS) },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FigmaBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tìm nhiệm vụ phù hợp",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 📌 5. DANH SÁCH "GỢI Ý CHO BẠN" (RECOMMENDED TASKS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gợi ý cho bạn",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = FigmaTextPrimary
            )
            Text(
                text = "Xem tất cả",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = FigmaBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { navController.navigate(Routes.TASKS) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Item 1: Instagram
        RecommendedTaskItem(
            category = "Instagram",
            title = "Theo dõi trang thiết kế...",
            subtitle = "Theo dõi · khoảng 2 phút",
            badgeText = "+40 điểm",
            icon = Icons.Default.CameraAlt,
            iconBg = FigmaBlueLight,
            iconTint = FigmaBlue,
            badgeBg = FigmaBlueLight,
            badgeTint = FigmaBlue,
            onClick = { navController.navigate(Routes.TASKS) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Item 2: TikTok
        RecommendedTaskItem(
            category = "TikTok",
            title = "Thích video mẹo chụp...",
            subtitle = "Thích bài · khoảng 1 phút",
            badgeText = "+25 điểm",
            icon = Icons.Default.PlayArrow,
            iconBg = FigmaPurpleLight,
            iconTint = FigmaPurple,
            badgeBg = FigmaPurpleLight,
            badgeTint = FigmaPurple,
            onClick = { navController.navigate(Routes.TASKS) }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun RecommendedTaskItem(
    category: String,
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    badgeBg: Color,
    badgeTint: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, FigmaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rounded Icon Box
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category,
                    fontSize = 11.5.sp,
                    color = FigmaTextMuted,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = FigmaTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = FigmaTextMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Pill Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeTint
                )
            }
        }
    }
}
