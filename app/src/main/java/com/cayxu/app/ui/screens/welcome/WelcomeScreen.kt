package com.cayxu.app.ui.screens.welcome

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * MÀN HÌNH CHÀO MỪNG (WELCOME SCREEN)
 * - Cá nhân hóa theo tên Username của người dùng.
 * - Animation co giãn nhịp thở các icon tương tác MXH Like 👍, Love ❤️, Follow 👤+, Comment 💬, Xu 🪙.
 * - Hiệu ứng trượt mờ từ từ lên trên (Smooth Slide-Up & Fade-Out) chuyển tiếp vào Trang chủ Home.
 */
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePrefs(context) }
    val buyerName = remember { securePrefs.getBuyerUsername() }
    val accountId = remember { securePrefs.getOrCreateAccountId() }
    val displayName = remember(buyerName) {
        if (!buyerName.isNullOrBlank()) buyerName else "LuneX Member #${accountId.takeLast(4)}"
    }

    var isExiting by remember { mutableStateOf(false) }
    val exitOffsetY by animateDpAsState(
        targetValue = if (isExiting) (-110).dp else 0.dp,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "exitOffsetY"
    )
    val exitAlpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(550, easing = FastOutSlowInEasing),
        label = "exitAlpha"
    )

    // ⏱️ CHÀO MỪNG THEO USERNAME -> TRƯỢT MỜ TỪ TỪ LÊN TRÊN VÀO MÀN 4 (HOME)
    LaunchedEffect(Unit) {
        delay(2100L)
        isExiting = true
        delay(600L)
        onLoginClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardWhite)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Hào quang tỏa sáng mềm mại (Ambient Aura Glow)
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFDBEAFE).copy(alpha = 0.5f),
                            Color(0xFFEEF2FF).copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = exitOffsetY)
                .alpha(exitAlpha)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 📌 1. CỤM ANIMATION 3D REACTION CO GIÃN ĐÀN HỒI (NHỎ RA TO RỒI THU NHỎ)
            Animated3DSocialReactionCluster()

            Spacer(modifier = Modifier.height(24.dp))

            // 📌 2. HUY HIỆU XÁC THỰC THÀNH CÔNG
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = SuccessGreen.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                        text = "Bản quyền Pro Đã Kích Hoạt",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 📌 3. TIÊU ĐỀ CHÀO MỪNG ĐÍCH DANH USERNAME
            Text(
                text = "Xin chào,",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                letterSpacing = (-0.2).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayName,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Primary,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Môi trường tự động hóa đã sẵn sàng.\nĐang trượt vào bảng điều khiển...",
                fontSize = 13.5.sp,
                color = TextSecondary,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 📌 4. THANH TIẾN ĐỘ TỰ ĐỘNG CHUYỂN TIẾP (ZERO-CLICK TRANSITION INDICATOR)
            AnimatedLoadingProgressIndicator()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isExiting) "Đang mở Trang chủ..." else "Tự động vào Trang chủ sau 2 giây...",
                fontSize = 11.sp,
                color = TextSecondary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Cụm Animation các biểu tượng tương tác mạng xã hội 3D:
 * Co giãn nhịp thở đàn hồi (nhỏ dần -> to ra -> thu nhỏ) theo phong cách @madrooom:
 * - 3D Like 👍 (Facebook Blue Glassmorphism)
 * - 3D Heart ❤️ (Candy Crimson Gloss)
 * - 3D Follow 👤+ (Sky Glass Pill)
 * - 3D Comment 💬 (Violet Bubble)
 * - 3D Xu 🪙 (Golden Coin)
 */
@Composable
private fun Animated3DSocialReactionCluster() {
    val infiniteTransition = rememberInfiniteTransition(label = "socialReactions")

    // Nhịp 1: Like & Lõi trung tâm
    val scaleCenter by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleCenter"
    )

    // Nhịp 2: Trái tim đỏ (nở to khi lõi thu nhỏ)
    val scaleHeart by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleHeart"
    )

    // Nhịp 3: Follow pill
    val scaleFollow by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleFollow"
    )

    // Nhịp 4: Comment bubble
    val scaleComment by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleComment"
    )

    // Nhịp 5: Đồng xu vàng
    val scaleCoin by infiniteTransition.animateFloat(
        initialValue = 1.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleCoin"
    )

    Box(
        modifier = Modifier
            .size(230.dp),
        contentAlignment = Alignment.Center
    ) {
        // Vòng sóng radar tỏa xung quanh
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .border(BorderStroke(1.dp, Color(0xFFBFDBFE).copy(alpha = 0.5f)), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF6FF).copy(alpha = 0.7f))
        )

        // 🌟 LÕI TRUNG TÂM: ICON 3D LIKE FACEBOOK CHÍNH
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Primary,
            border = BorderStroke(2.5.dp, Color.White),
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(86.dp)
                .scale(scaleCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF1E40AF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = "Like",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // ❤️ 1. ICON 3D TRÁI TIM / LOVE (Góc trên phải)
        Surface(
            shape = CircleShape,
            color = Color(0xFFF43F5E),
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 6.dp)
                .size(52.dp)
                .scale(scaleHeart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFB7185), Color(0xFFE11D48))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "❤️", fontSize = 24.sp)
            }
        }

        // 👤+ 2. ICON 3D FOLLOW (Góc trên trái)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardWhite,
            border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 14.dp)
                .scale(scaleFollow)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Follow",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Black,
                    color = Primary
                )
            }
        }

        // 💬 3. ICON 3D COMMENT (Góc dưới trái)
        Surface(
            shape = CircleShape,
            color = CardWhite,
            border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 12.dp, y = (-12).dp)
                .size(48.dp)
                .scale(scaleComment)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "Comment",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 🪙 4. ICON 3D ĐỒNG XU / REWARD (Góc dưới phải)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardWhite,
            border = BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-8).dp)
                .scale(scaleCoin)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = "Xu",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "+Xu",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SuccessGreen
                )
            }
        }
    }
}

/**
 * Thanh tiến độ chuyển trang tự động chạy trong 2.4 giây
 */
@Composable
private fun AnimatedLoadingProgressIndicator() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2300, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .width(180.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF1F5F9))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.value)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(Primary, Color(0xFF6366F1))
                    )
                )
        )
    }
}
