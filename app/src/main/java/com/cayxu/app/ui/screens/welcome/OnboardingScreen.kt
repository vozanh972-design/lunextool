@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cayxu.app.ui.screens.welcome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
// 📌 TRANG 1: "Biến tương tác thành cơ hội" (Seamless Orbital Glow Design)
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
        // Minh họa hòa mình vào nền: Lõi trung tâm + Orbital chips nổi
        Box(
            modifier = Modifier
                .size(310.dp),
            contentAlignment = Alignment.Center
        ) {
            // Hào quang tỏa sáng mềm mại (Aura Glow)
            Box(
                modifier = Modifier
                    .size(270.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EFFF).copy(alpha = 0.7f))
            )

            // Vòng quỹ đạo hairline mờ
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(1.dp, Color(0xFFC7D7FE).copy(alpha = 0.6f)), CircleShape)
            )

            // Lõi trung tâm Glow
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Primary,
                shadowElevation = 10.dp,
                modifier = Modifier.size(92.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FlashOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            // Chip 1: Trạng thái tự động (Top-Left)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardWhite,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 16.dp, y = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Text(
                        text = "100% Tự động",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Chip 2: Nền tảng MXH (Top-Right)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardWhite,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-16).dp, y = 36.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "⚡", fontSize = 11.sp)
                    Text(
                        text = "TikTok • FB",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // Chip 3: Điểm thưởng tích lũy (Bottom-Left)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CardWhite,
                shadowElevation = 5.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 18.dp, y = (-28).dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFFFEF3C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "★", fontSize = 12.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(
                            text = "Tích lũy",
                            fontSize = 9.5.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "+166k Điểm",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Chip 4: Bảo mật an toàn (Bottom-Right)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardWhite,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-20).dp, y = (-32).dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "An toàn & Bảo mật",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

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
// 📌 TRANG 2: "Một nơi. Mọi nền tảng." (Seamless Floating Grid)
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
        // Minh họa: Khung hài hòa với aura nền thay vì hộp cứng
        Box(
            modifier = Modifier.size(width = 310.dp, height = 280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Nền hào quang aura mềm mại phía sau
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EFFF).copy(alpha = 0.5f))
            )

            // Lưới 4 thẻ nền tảng với đổ bóng nhẹ nổi bồng bềnh
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OnboardPlatformPillCard(
                        modifier = Modifier.weight(1f),
                        name = "Instagram",
                        icon = Icons.Outlined.PhotoCamera,
                        iconBg = Color(0xFFFDF2F8),
                        iconTint = Color(0xFFE1306C)
                    )
                    OnboardPlatformPillCard(
                        modifier = Modifier.weight(1f),
                        name = "TikTok",
                        icon = Icons.Outlined.MusicNote,
                        iconBg = Color(0xFF0F172A),
                        iconTint = Color.White
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OnboardPlatformPillCard(
                        modifier = Modifier.weight(1f),
                        name = "Facebook",
                        icon = Icons.Outlined.ThumbUp,
                        iconBg = Color(0xFFEFF6FF),
                        iconTint = Color(0xFF1877F2)
                    )
                    OnboardPlatformPillCard(
                        modifier = Modifier.weight(1f),
                        name = "YouTube",
                        icon = Icons.Outlined.PlayArrow,
                        iconBg = Color(0xFFFEF2F2),
                        iconTint = Color(0xFFEF4444)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

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
private fun OnboardPlatformPillCard(
    modifier: Modifier = Modifier,
    name: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        modifier = modifier.height(96.dp)
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
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = name,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 📌 TRANG 3: "Tích điểm mỗi ngày. Nhận quà thật." (Seamless Minimalist Chart)
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
            modifier = Modifier.size(width = 310.dp, height = 280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Nền tròn aura mờ
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8EFFF).copy(alpha = 0.5f))
            )

            // Thẻ tăng trưởng bồng bềnh
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = CardWhite,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                modifier = Modifier
                    .size(width = 280.dp, height = 200.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tăng trưởng tương tác",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )

                    // 5 cột biểu đồ tăng dần
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        ChartBar(height = 36.dp, color = Color(0xFFBFDBFE))
                        ChartBar(height = 54.dp, color = Color(0xFF93C5FD))
                        ChartBar(height = 74.dp, color = Color(0xFF60A5FA))
                        ChartBar(height = 98.dp, color = Primary)
                        ChartBar(height = 115.dp, color = Color(0xFF0F172A))
                    }
                }
            }

            // Huy hiệu "+120 điểm" nổi ở góc dưới phải
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10).dp, y = (-10).dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
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
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

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
