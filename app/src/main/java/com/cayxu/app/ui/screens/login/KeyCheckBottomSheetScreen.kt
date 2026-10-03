package com.cayxu.app.ui.screens.login

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Màn hình kiểm tra key kích hoạt — Bottom Sheet Modal chuẩn thiết kế Figma Nexa.
 * Hiển thị phía trước màn hình Login (nền mờ phía sau).
 * KHÔNG thay đổi logic verify key — chỉ thay đổi phần UI hiển thị.
 */
@Composable
fun KeyCheckBottomSheetScreen(
    status: AutoVerifyStatus,
    errorMessage: String?,
    isEnglish: Boolean,
    brandBlue: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit = {}
) {
    val blueLightBg = brandBlue.copy(alpha = 0.12f)
    val successGreen = Color(0xFF34C759)
    val errorRed = Color(0xFFFF3B30)
    val sheetBg = Color(0xFFFFFFFF)
    val progressTrack = Color(0xFFE5E5EA)

    // Animated progress for indeterminate-style bar while CHECKING
    val infiniteTransition = rememberInfiniteTransition(label = "progressAnim")
    val animatedProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progressValue"
    )

    val progressFraction = when (status) {
        AutoVerifyStatus.CHECKING -> animatedProgress
        AutoVerifyStatus.SUCCESS  -> 1f
        AutoVerifyStatus.ERROR    -> 0f
        AutoVerifyStatus.IDLE     -> 0f
    }

    val progressColor = when (status) {
        AutoVerifyStatus.SUCCESS -> successGreen
        AutoVerifyStatus.ERROR   -> errorRed
        else                     -> brandBlue
    }

    // ── Full-screen scrim + bottom sheet ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000).copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                // Cho phép chạm vào nền mờ ngoài bottom sheet để đóng nếu gặp lỗi hoặc muốn nhập key
                if (status == AutoVerifyStatus.ERROR) {
                    onDismiss()
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // ── Bottom Sheet ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(sheetBg)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { /* Chặn click xuyên qua vùng scrim */ }
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 1. Pull handle
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD1D1D6))
            )

            Spacer(Modifier.height(28.dp))

            // 2. Icon tròn lớn — phone + shield/lock
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(blueLightBg),
                contentAlignment = Alignment.Center
            ) {
                when (status) {
                    AutoVerifyStatus.SUCCESS -> {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(successGreen.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Success",
                                tint = successGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    AutoVerifyStatus.ERROR -> {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(errorRed.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Error",
                                tint = errorRed,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    else -> {
                        // CHECKING state — phone + shield icon (PhonelinkLock)
                        Icon(
                            imageVector = Icons.Outlined.PhonelinkLock,
                            contentDescription = "Key Check",
                            tint = brandBlue,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 3. Title
            val titleText = when (status) {
                AutoVerifyStatus.CHECKING -> if (isEnglish) "Checking activation key" else "Kiểm tra khóa kích hoạt"
                AutoVerifyStatus.SUCCESS  -> if (isEnglish) "Key verified!" else "Xác minh thành công!"
                AutoVerifyStatus.ERROR    -> if (isEnglish) "Verification failed" else "Xác minh thất bại"
                AutoVerifyStatus.IDLE     -> ""
            }
            Text(
                text = titleText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = when (status) {
                    AutoVerifyStatus.SUCCESS -> successGreen
                    AutoVerifyStatus.ERROR   -> errorRed
                    else                     -> textPrimary
                },
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // 4. Subtitle
            val subtitleText = when (status) {
                AutoVerifyStatus.CHECKING -> if (isEnglish) "Verifying security key on this device" else "Đang xác minh khóa bảo mật trên thiết bị này"
                AutoVerifyStatus.SUCCESS  -> if (isEnglish) "Entering application..." else "Đang vào ứng dụng..."
                AutoVerifyStatus.ERROR    -> errorMessage ?: (if (isEnglish) "Invalid or expired key" else "Key không hợp lệ hoặc đã hết hạn")
                AutoVerifyStatus.IDLE     -> ""
            }
            Text(
                text = subtitleText,
                fontSize = 13.sp,
                color = textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            Spacer(Modifier.height(24.dp))

            // 5. Progress bar ngang bo tròn
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(progressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(progressColor)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 6. Text trạng thái nhỏ bên dưới progress bar
            val statusLabel = when (status) {
                AutoVerifyStatus.CHECKING -> if (isEnglish) "Checking..." else "Đang kiểm tra..."
                AutoVerifyStatus.SUCCESS  -> if (isEnglish) "Done" else "Hoàn tất"
                AutoVerifyStatus.ERROR    -> if (isEnglish) "Failed" else "Thất bại"
                AutoVerifyStatus.IDLE     -> ""
            }
            Text(
                text = statusLabel,
                fontSize = 12.sp,
                color = when (status) {
                    AutoVerifyStatus.SUCCESS -> successGreen
                    AutoVerifyStatus.ERROR   -> errorRed
                    else                     -> textSecondary
                },
                textAlign = TextAlign.Center
            )

        }
    }
}
