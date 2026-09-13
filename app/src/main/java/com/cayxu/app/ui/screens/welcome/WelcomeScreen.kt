package com.cayxu.app.ui.screens.welcome

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import kotlinx.coroutines.delay

/**
 * Luồng 2 màn hình ban đầu chuẩn phong cách AutoLunex:
 * 1. Màn Splash (Chào mừng) với nền chuyển sắc ấm và logo AutoLunex + Tagline
 * 2. Màn Onboarding (Giới thiệu) với hình minh họa làm nhiệm vụ, tiêu đề, dot indicator và nút Tiếp tục
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    var currentStep by remember { mutableStateOf(1) }

    // Tự động chuyển từ Màn 1 (Splash) sang Màn 2 (Onboarding) sau 2 giây
    LaunchedEffect(Unit) {
        delay(2000)
        if (currentStep == 1) {
            currentStep = 2
        }
    }

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
        },
        label = "WelcomeScreenPager"
    ) { step ->
        if (step == 1) {
            SplashScreenView(onSkip = { currentStep = 2 })
        } else {
            OnboardingScreenView(onContinue = onGetStarted)
        }
    }
}

/**
 * GIAO DIỆN 1: MÀN CHÀO MỪNG (SPLASH)
 * Nền ấm, Logo AutoLunex ở giữa và Slogan truyền cảm hứng.
 */
@Composable
private fun SplashScreenView(onSkip: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF9F5),
                        Color(0xFFFFEDE4),
                        Color(0xFFFF7A3D),
                        Color(0xFFFF5E1E)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onSkip() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo AutoLunex tông cam ấm
            Image(
                painter = painterResource(R.drawable.ic_autolunex_warm_logo),
                contentDescription = "AutoLunex",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(68.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Tagline / Slogan
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
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF7C2D12),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1.2f))
        }
    }
}

/**
 * GIAO DIỆN 2: MÀN GIỚI THIỆU (ONBOARDING)
 * Nút Bỏ qua, Hình minh họa nhiệm vụ, Tiêu đề, Mô tả, Indicator và Nút Tiếp tục
 */
@Composable
private fun OnboardingScreenView(onContinue: () -> Unit) {
    val brandOrange = Color(0xFFFF5E1E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFBF9))
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thanh trên cùng: Nút "Bỏ qua"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onContinue) {
                    Text(
                        text = "Bỏ qua",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hình minh họa Onboarding (Laptop, quà tặng, đồng xu)
            Image(
                painter = painterResource(R.drawable.ill_onboarding_warm),
                contentDescription = "Làm nhiệm vụ kiếm thu nhập",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(270.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Tiêu đề chính 2 dòng
            Text(
                text = "Làm nhiệm vụ",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E293B),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Kiếm thu nhập thật",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = brandOrange,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mô tả
            Text(
                text = "Thực hiện các nhiệm vụ, đơn giản và nhận ngay xu thưởng.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Dải chấm tròn phân trang (Page indicator: 4 chấm, chấm đầu tiên là pill cam)
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 28.dp)
            ) {
                // Active pill dot
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(brandOrange)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Inactive dots
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            // Nút "Tiếp tục →"
            Button(
                onClick = onContinue,
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
                        text = "Tiếp tục",
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

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
