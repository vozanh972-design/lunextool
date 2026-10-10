@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cayxu.app.ui.screens.welcome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * BỘ 3 MÀN HÌNH ONBOARDING CHUẨN FIGMA (SCREEN 02, 03, 04)
 * - Trang 1: "Biến tương tác thành cơ hội" (Figma Screen 02)
 * - Trang 2: "Một nơi. Mọi nền tảng." (Figma Screen 03)
 * - Trang 3: "Tích điểm mỗi ngày. Nhận quà thật." (Figma Screen 04)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Nút Bỏ qua ở góc phải
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < 2) {
                    Text(
                        text = "Bỏ qua",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        modifier = Modifier.clickable { onFinished() }
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Pager vuốt 3 màn hình Onboard
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPage1()
                    1 -> OnboardingPage2()
                    2 -> OnboardingPage3()
                }
            }

            // Bottom Section: Dấu chấm trang (Page Indicator) + Nút Bấm Tiếp tục / Bắt đầu
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hàng 3 chấm indicator (viên thuốc xanh cho trang active)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val isActive = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isActive) 24.dp else 8.dp,
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isActive) Primary else Color(0xFFE7EAF2))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Nút Tiếp tục (Trang 1, 2) hoặc Bắt đầu (Trang 3)
                Button(
                    onClick = {
                        if (pagerState.currentPage < 2) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinished()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage == 2) "Bắt đầu" else "Tiếp tục",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 📌 TRANG 1: "Biến tương tác thành cơ hội" (Figma Screen 02)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OnboardingPage1() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Minh họa: Khung điện thoại + avatar MA + thẻ tim + thẻ nổi
        Box(
            modifier = Modifier
                .size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Nền tròn xanh nhạt mờ
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F5FF))
            )

            // Khung điện thoại trắng trung tâm
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = CardWhite,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(width = 140.dp, height = 210.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Avatar MA
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEDE9FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MA",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED)
                        )
                    }

                    // Thanh gạch ngang
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFE2E8F0))
                    )

                    // Thẻ tim tím pastel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF5F3FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Thẻ nổi bên trái (User)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 10.dp, y = (-20).dp)
                    .size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Huy hiệu xanh bên dưới trái (PersonAdd)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 36.dp, y = (-30).dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Thẻ nổi bên phải (Heart hồng)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-10).dp, y = 10.dp)
                    .size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Biến tương tác thành\ncơ hội",
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Khám phá nhiệm vụ, kết nối cộng đồng và\nnhận điểm từ những tương tác mỗi ngày.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 📌 TRANG 2: "Một nơi. Mọi nền tảng." (Figma Screen 03)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OnboardingPage2() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Minh họa: Khung xanh bo tròn chứa lưới 4 thẻ nền tảng (Instagram, TikTok, Facebook, YouTube)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFF0F5FF),
            modifier = Modifier
                .size(width = 300.dp, height = 270.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OnboardPlatformMiniCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        name = "Instagram",
                        icon = Icons.Outlined.PhotoCamera,
                        iconBg = Color(0xFFFDF2F8),
                        iconTint = Color(0xFFE1306C)
                    )
                    OnboardPlatformMiniCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        name = "TikTok",
                        icon = Icons.Outlined.MusicNote,
                        iconBg = Color(0xFF0F172A),
                        iconTint = Color.White
                    )
                }

                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OnboardPlatformMiniCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        name = "Facebook",
                        icon = Icons.Outlined.ThumbUp,
                        iconBg = Color(0xFFEFF6FF),
                        iconTint = Color(0xFF1877F2)
                    )
                    OnboardPlatformMiniCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        name = "YouTube",
                        icon = Icons.Outlined.PlayArrow,
                        iconBg = Color(0xFFFEF2F2),
                        iconTint = Color(0xFFEF4444)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Một nơi. Mọi nền tảng.",
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Kết nối Instagram, TikTok, Facebook và\nYouTube để bắt đầu hành trình của bạn.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OnboardPlatformMiniCard(
    modifier: Modifier = Modifier,
    name: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CardWhite,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = name,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 📌 TRANG 3: "Tích điểm mỗi ngày. Nhận quà thật." (Figma Screen 04)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OnboardingPage3() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Minh họa: Thẻ biểu đồ tăng trưởng 5 cột + huy hiệu "+120 điểm"
        Box(
            modifier = Modifier.size(width = 300.dp, height = 260.dp),
            contentAlignment = Alignment.Center
        ) {
            // Nền tròn mờ
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F5FF))
            )

            // Thẻ tăng trưởng
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = CardWhite,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(width = 270.dp, height = 190.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tăng trưởng của bạn",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    // 5 cột biểu đồ tăng dần
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        ChartBar(height = 32.dp, color = Primary)
                        ChartBar(height = 48.dp, color = Primary)
                        ChartBar(height = 68.dp, color = Primary)
                        ChartBar(height = 92.dp, color = Primary)
                        ChartBar(height = 110.dp, color = Color(0xFF7C3AED))
                    }
                }
            }

            // Huy hiệu "+120 điểm" nổi ở góc dưới phải
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-8).dp, y = (-8).dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF7C3AED))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "+120 điểm",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Tích điểm mỗi ngày.\nNhận quà thật.",
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Mỗi nhiệm vụ là một bước tiến. Theo dõi tăng\ntrưởng và đổi điểm lấy phần thưởng bạn yêu\nthích.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChartBar(
    height: androidx.compose.ui.unit.Dp,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(color)
    )
}
