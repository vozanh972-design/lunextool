package com.cayxu.app.ui.screens.welcome

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.R

/**
 * GIAO DIỆN 1: MÀN GIỚI THIỆU (INTRO SCREEN)
 * Thiết kế chuẩn phong cách Digital Banking Dark Navy theo mẫu:
 * - Nền Navy Gradient sâu thẳm
 * - Top Bar: Logo AutoLunex + Tên thương hiệu
 * - Hero: Tiêu đề "Kiếm tiền online mỗi ngày chỉ với vài thao tác" & mô tả
 * - Danh sách 3 tính năng nổi bật: Cày xu mỗi ngày, Đổi xu lấy tiền thật, Mời bạn cùng cày
 * - Nút "Bắt đầu kiếm tiền" dạng pill trắng nổi bật ở đáy màn hình
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val navy900 = Color(0xFF0A1730)
    val navy800 = Color(0xFF0F2148)
    val navy700 = Color(0xFF16305F)
    val cobalt600 = Color(0xFF1D4ED8)
    val cyan400 = Color(0xFF4FD1E8)
    val textSoft = Color(0xFFA9BEE0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        navy900,
                        navy800,
                        navy700,
                        cobalt600
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // TOP BAR: Logo & Tên thương hiệu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "AutoLunex Logo",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AUTOLUNEX",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // HERO SECTION: Tiêu đề lớn & mô tả
            Text(
                text = "Kiếm tiền online mỗi ngày\nchỉ với vài thao tác",
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Cày xu, hoàn thành nhiệm vụ và đổi thưởng thành tiền mặt — mọi lúc, mọi nơi.",
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                color = textSoft
            )

            Spacer(modifier = Modifier.height(32.dp))

            // DANH SÁCH 3 TÍNH NĂNG CHÍNH
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                FeatureItem(
                    icon = Icons.Filled.MonetizationOn,
                    title = "Cày xu mỗi ngày",
                    subtitle = "Hoàn thành nhiệm vụ đơn giản nhận xu miễn phí",
                    iconColor = cyan400,
                    textSoft = textSoft
                )

                FeatureItem(
                    icon = Icons.Filled.AccountBalanceWallet,
                    title = "Đổi xu lấy tiền thật",
                    subtitle = "Rút tiền nhanh chóng, an toàn về ví hoặc ngân hàng",
                    iconColor = cyan400,
                    textSoft = textSoft
                )

                FeatureItem(
                    icon = Icons.Filled.GroupAdd,
                    title = "Mời bạn cùng cày",
                    subtitle = "Nhận thêm hoa hồng khi giới thiệu bạn mới tham gia",
                    iconColor = cyan400,
                    textSoft = textSoft
                )
            }

            // NÚT BẮT ĐẦU KIẾM TIỀN Ở ĐÁY MÀN HÌNH
            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = navy900
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp), spotColor = Color.Black)
            ) {
                Text(
                    text = "Bắt đầu kiếm tiền",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = navy900
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    textSoft: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icon container bo góc nền mờ trong suốt
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = textSoft
            )
        }
    }
}
