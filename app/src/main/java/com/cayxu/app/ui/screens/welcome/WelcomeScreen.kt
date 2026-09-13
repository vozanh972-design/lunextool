package com.cayxu.app.ui.screens.welcome

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingPageData(
    val imageRes: Int,
    val titleLine1: String,
    val titleLine2: String,
    val description: String
)

/**
 * Luồng khởi động AutoLunex:
 * 1. Màn Splash (Chào mừng): Logo chữ A sắc nét, slogan, thanh loading đáy màn hình.
 * 2. Màn Giới thiệu (Onboarding): Slider tự động trượt trang 100% không bị dừng lửng, nút Bỏ qua kéo sát góc trên cùng.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    var isSplash by remember { mutableStateOf(true) }

    AnimatedContent(
        targetState = isSplash,
        transitionSpec = {
            fadeIn(animationSpec = tween(450)) togetherWith fadeOut(animationSpec = tween(450))
        },
        label = "WelcomeTransition"
    ) { splashState ->
        if (splashState) {
            SplashScreenView(onFinishSplash = { isSplash = false })
        } else {
            OnboardingPagerScreenView(onFinish = onGetStarted)
        }
    }
}

/**
 * GIAO DIỆN 1: MÀN CHÀO MỪNG (SPLASH SCREEN)
 */
@Composable
private fun SplashScreenView(onFinishSplash: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2300, easing = LinearEasing)
        )
        delay(100)
        onFinishSplash()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFinishSplash() }
    ) {
        Image(
            painter = painterResource(R.drawable.bg_splash_warm_waves),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.9f))

            Image(
                painter = painterResource(R.drawable.ic_autolunex_warm_logo),
                contentDescription = "AutoLunex Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(90.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Kiếm xu mỗi ngày",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tự do tài chính trong tầm tay",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1.3f))

            Box(
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .width(160.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

/**
 * GIAO DIỆN 2: MÀN GIỚI THIỆU (ONBOARDING)
 * - Nút "Bỏ qua" đặt sát góc trên cùng bên phải
 * - Tự động trượt qua hết 100% trang, khóa snap chuẩn không bao giờ bị dừng lưng chừng
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingPagerScreenView(onFinish: () -> Unit) {
    val brandOrange = Color(0xFFFF5E1E)
    val coroutineScope = rememberCoroutineScope()

    val pages = remember {
        listOf(
            OnboardingPageData(
                imageRes = R.drawable.ill_onboarding_warm,
                titleLine1 = "Làm nhiệm vụ",
                titleLine2 = "Kiếm thu nhập thật",
                description = "Thực hiện các nhiệm vụ, đơn giản và nhận ngay xu thưởng."
            ),
            OnboardingPageData(
                imageRes = R.drawable.ill_wallet_growth,
                titleLine1 = "Nuôi tài khoản",
                titleLine2 = "Tự động tương tác",
                description = "Tự động nuôi và tương tác Facebook, TikTok an toàn, bền bỉ."
            ),
            OnboardingPageData(
                imageRes = R.drawable.ic_mascot_coin,
                titleLine1 = "Đổi thưởng nhanh",
                titleLine2 = "Uy tín tuyệt đối",
                description = "Quy đổi xu thưởng thành tiền mặt hoặc thẻ cào trong tích tắc."
            ),
            OnboardingPageData(
                imageRes = R.drawable.ill_key_3d,
                titleLine1 = "Bảo mật an toàn",
                titleLine2 = "Hỗ trợ 24/7",
                description = "Hệ thống mã hóa dữ liệu độc quyền và hỗ trợ tận tâm mọi lúc."
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })

    // Tự động lướt trọn vẹn từng trang sau mỗi 3 giây
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            if (!pagerState.isScrollInProgress) {
                val next = pagerState.currentPage + 1
                if (next < pages.size) {
                    pagerState.animateScrollToPage(
                        page = next,
                        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                    )
                } else {
                    delay(500)
                    onFinish()
                    break
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFBF9))
    ) {
        // Nền sóng cam chân trang
        Image(
            painter = painterResource(R.drawable.bg_bottom_warm_waves),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )

        // Nút "Bỏ qua" đặt sát đỉnh góc trên bên phải
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp, end = 12.dp)
                .align(Alignment.TopEnd),
            contentAlignment = Alignment.CenterEnd
        ) {
            TextButton(
                onClick = onFinish,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Bỏ qua",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF475569)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Slider 4 trang Onboarding với Snap Fling hoàn hảo
            HorizontalPager(
                state = pagerState,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    snapAnimationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(page.imageRes),
                        contentDescription = page.titleLine1,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .height(260.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = page.titleLine1,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = page.titleLine2,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = page.description,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF475569),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Dải 4 chấm chỉ báo trang chạy động
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 26.dp)
            ) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(
                                width = if (isSelected) 20.dp else 7.dp,
                                height = 7.dp
                            )
                            .clip(if (isSelected) RoundedCornerShape(3.5.dp) else CircleShape)
                            .background(if (isSelected) brandOrange else Color(0xFFCBD5E1))
                    )
                }
            }

            // Nút "Tiếp tục →" / "Bắt đầu ngay"
            val isLastPage = pagerState.currentPage == pages.size - 1
            Button(
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                page = pagerState.currentPage + 1,
                                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp), spotColor = brandOrange)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isLastPage) "Bắt đầu ngay" else "Tiếp tục",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(34.dp))
        }
    }
}
