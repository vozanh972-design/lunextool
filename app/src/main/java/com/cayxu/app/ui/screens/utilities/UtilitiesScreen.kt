package com.cayxu.app.ui.screens.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.navigation.goHome

private val ScreenBg = Color(0xFFF3F5F8)
private val CardBorder = Color(0x140F1E37) // rgba(15, 30, 55, 0.08)
private val TextPrimaryColor = Color(0xFF0B1730)
private val TextSecondaryColor = Color(0xFF5B6B85)
private val TextTertiaryColor = Color(0xFF8E9BB0)

private val Cobalt500 = Color(0xFF2E6BF2)
private val Cobalt600 = Color(0xFF1D4ED8)

@Composable
fun UtilitiesScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Sticky Header chuẩn HTML
        Surface(
            color = ScreenBg.copy(alpha = 0.95f),
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Tiện ích",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Mọi công cụ hữu ích, gói gọn trong một nơi",
                    fontSize = 12.sp,
                    color = TextSecondaryColor
                )
            }
        }

        // Body list chuẩn HTML
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Tool Card 1: Nuôi tài khoản (Featured)
            HtmlToolCard(
                title = "Nuôi tài khoản",
                subtitle = "Tự động chăm sóc tài khoản mỗi ngày",
                icon = Icons.Outlined.Person,
                iconGradient = listOf(Color(0xFFE3FBFD), Color(0xFFC7F5FC)),
                iconTint = Cobalt500,
                isFeatured = true,
                onClick = {
                    navController.navigate(Routes.NURTURE_SETUP) { launchSingleTop = true }
                }
            )

            // Tool Card 2: Reg page & Chuyển page (Blue theme)
            HtmlToolCard(
                title = "Reg page & Chuyển page",
                subtitle = "Tự động đăng ký và chuyển trang",
                icon = Icons.Outlined.SwapHoriz,
                iconGradient = listOf(Color(0xFFDBEAFE), Color(0xFFBFDBFE)),
                iconTint = Cobalt600,
                isFeatured = false,
                onClick = {
                    navController.navigate(Routes.REG_AND_TRANSFER_PAGE) { launchSingleTop = true }
                }
            )

            // Tool Card 3: Đăng nhập Facebook
            HtmlToolCard(
                title = "Đăng nhập Facebook",
                subtitle = "Đăng nhập tài khoản Facebook trực tiếp",
                icon = Icons.Outlined.Person,
                iconGradient = listOf(Color(0xFFE0E7FF), Color(0xFFC7D2FE)),
                iconTint = Cobalt600,
                isFeatured = false,
                onClick = {
                    navController.navigate(Routes.FB_WEB_LOGIN) { launchSingleTop = true }
                }
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HtmlToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconGradient: List<Color>,
    iconTint: Color,
    isFeatured: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFeatured) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool Icon Wrap (48x48 dp with radius 14dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(iconGradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Tool Text (Title + Subtitle)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor,
                    letterSpacing = (-0.1).sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryColor,
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Chevron right icon
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextTertiaryColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
