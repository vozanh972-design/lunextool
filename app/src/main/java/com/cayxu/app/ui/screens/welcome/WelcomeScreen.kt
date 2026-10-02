package com.cayxu.app.ui.screens.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * GIAO DIỆN CHÀO MỪNG (WELCOME SCREEN)
 * Chuẩn phong cách thiết kế Figma Nexa Minimalist:
 * - Nền trắng tinh tế #FFFFFF
 * - Màu chủ đạo Apple Blue #0A84FF & Chữ than đậm #1C1C1E
 * - Cụm Preview Card nhiệm vụ tương tác kèm 3 tính năng nổi bật
 * - Nút chính "Bắt đầu ngay" bo góc hiện đại & Nút phụ "Tôi đã có key kích hoạt"
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val bgWhite = Color(0xFFFFFFFF)
    val brandBlue = Color(0xFF0A84FF)
    val textPrimary = Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF8E8E93)
    val cardSurface = Color(0xFFF2F2F7)
    val dividerColor = Color(0xFFE5E5EA)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // TOP BAR: Logo ứng dụng & Tên thương hiệu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "LunexTool Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "LUNEXTOOL",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = textPrimary
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // HERO SECTION: Tiêu đề lớn & mô tả phụ
            Text(
                text = "Tương tác đơn giản,\nkiếm tiền mỗi ngày",
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Hoàn thành nhiệm vụ mạng xã hội, tích lũy thu nhập và đổi thưởng tiện lợi cùng LunexTool.",
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(26.dp))

            // PREVIEW BOX TÍNH NĂNG (CHUẨN FIGMA CARD)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardSurface)
                    .padding(14.dp)
            ) {
                // Header của thẻ
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nhiệm vụ tương tác",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEAF4FF))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Nhận điểm",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card trắng bên trong chứa 3 hàng tính năng
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        BenefitRow(
                            icon = Icons.Filled.Favorite,
                            iconBg = Color(0xFFEAF4FF),
                            iconTint = brandBlue,
                            title = "Thích nội dung phù hợp",
                            desc = "Chỉ mất khoảng 1 phút hoàn thành",
                            textColor = textPrimary,
                            descColor = textSecondary
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = dividerColor.copy(alpha = 0.6f),
                            thickness = 0.8.dp
                        )

                        BenefitRow(
                            icon = Icons.Filled.PersonAdd,
                            iconBg = Color(0xFFEFEEFF),
                            iconTint = Color(0xFF5856D6),
                            title = "Theo dõi tài khoản uy tín",
                            desc = "Nhiệm vụ được hệ thống kiểm duyệt",
                            textColor = textPrimary,
                            descColor = textSecondary
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = dividerColor.copy(alpha = 0.6f),
                            thickness = 0.8.dp
                        )

                        BenefitRow(
                            icon = Icons.Filled.CheckCircle,
                            iconBg = Color(0xFFEAF4FF),
                            iconTint = brandBlue,
                            title = "Xác nhận và nhận điểm",
                            desc = "Theo dõi tiến độ ngay trong ứng dụng",
                            textColor = textPrimary,
                            descColor = textSecondary
                        )
                    }
                }
            }

            // Khoảng trống đệm cuối danh sách để nội dung cuộn không bị nút che
            Spacer(modifier = Modifier.height(84.dp))
        }

        // NÚT "BẮT ĐẦU NGAY" ĐẶT SÁT ĐÁY MÀN HÌNH (CHUẨN FIGMA)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            bgWhite.copy(alpha = 0f),
                            bgWhite.copy(alpha = 0.92f),
                            bgWhite
                        )
                    )
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandBlue,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Bắt đầu ngay",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    desc: String,
    textColor: Color,
    descColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.5.sp,
                color = descColor
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFC7C7CC),
            modifier = Modifier.size(18.dp)
        )
    }
}
