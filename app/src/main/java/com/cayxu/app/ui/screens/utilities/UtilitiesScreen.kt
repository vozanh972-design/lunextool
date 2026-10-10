package com.cayxu.app.ui.screens.utilities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*

/**
 * MÀN HÌNH TIỆN ÍCH (UTILITIES SCREEN) - BENTO PASTEL
 * - Nối đầy đủ các chức năng Nuôi tài khoản, Reg & Chuyển Page, Cấu hình nuôi FB
 * - Thiết kế chuẩn Bento đồng bộ với màn Nhiệm vụ
 */
@Composable
fun UtilitiesScreen(navController: NavController) {
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
            Spacer(modifier = Modifier.height(14.dp))

            // 1. TIÊU ĐỀ TRANG
            Text(
                text = "Tiện ích mở rộng",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Tối ưu hóa và tự động hóa quy trình nuôi tài khoản mạng xã hội",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. HERO FEATURED CARD: NUÔI TÀI KHOẢN (BENTO VỚI ẢNH THẬT)
            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Routes.NURTURE_SETUP) { launchSingleTop = true } }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEFF6FF))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "NỔI BẬT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }

                        Text(
                            text = "Đa luồng →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Nuôi tài khoản tổng hợp",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Tự động lướt feed, thả cảm xúc, xem story chăm sóc nick FB & TikTok",
                        fontSize = 12.5.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ảnh minh họa lớn
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(18.dp))
                    ) {
                        SubcomposeAsyncImage(
                            model = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=500&auto=format&fit=crop&q=80",
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
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Cấu hình ngay",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. BENTO HÀNG 2: REG PAGE & NUÔI FACEBOOK
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Thẻ Reg Page & Chuyển Page (Soft Sky Pastel)
                UtilityBentoCard(
                    badge = "FANPAGE",
                    title = "Reg & Chuyển Page",
                    subtitle = "Tạo trang fanpage và chuyển quyền quản trị",
                    bgColor = Color(0xFFEBF5FF),
                    accentColor = Color(0xFF0284C7),
                    borderColor = Color(0xFFCCE4FF),
                    modifier = Modifier
                        .weight(1f)
                        .height(175.dp),
                    onClick = {
                        navController.navigate(Routes.REG_AND_TRANSFER_PAGE) { launchSingleTop = true }
                    }
                )

                // Thẻ Nuôi FB chuyên sâu (Soft Rose Pastel)
                UtilityBentoCard(
                    badge = "FACEBOOK",
                    title = "Nuôi nick FB",
                    subtitle = "Lướt feed, kết bạn và tương tác nick chính",
                    bgColor = Color(0xFFFFEDF2),
                    accentColor = Color(0xFFE11D48),
                    borderColor = Color(0xFFFFD1DC),
                    modifier = Modifier
                        .weight(1f)
                        .height(175.dp),
                    onClick = {
                        navController.navigate(Routes.FB_NURTURE) { launchSingleTop = true }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. BENTO HÀNG 3: CẤU HÌNH NUÔI CHUYÊN SÂU
            UtilityBentoCard(
                badge = "CẤU HÌNH",
                title = "Thiết lập kịch bản nuôi",
                subtitle = "Tùy chỉnh thời gian delay, số lượng hành động và kịch bản tương tác",
                bgColor = Color(0xFFF3F0FA),
                accentColor = Color(0xFF7C3AED),
                borderColor = Color(0xFFE4DBF5),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                onClick = {
                    navController.navigate(Routes.FB_NUOI_CONFIG) { launchSingleTop = true }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UtilityBentoCard(
    badge: String,
    title: String,
    subtitle: String,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Text(
                    text = "→",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            Text(
                text = "Mở tiện ích",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF334155)
            )
        }
    }
}
