package com.cayxu.app.ui.screens.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.R
import com.cayxu.app.ui.navigation.goHome
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

private data class PlatformOption(
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color
)

private val platformOptions = listOf(
    PlatformOption(
        name = "Nhiemvucheo",
        subtitle = "Nhiệm vụ tăng sub, view, tương tác đa kênh",
        icon = Icons.Filled.SwapHoriz,
        accentColor = Color(0xFF2563EB)
    ),
    PlatformOption(
        name = "Tuongtaccheo",
        subtitle = "Nhiệm vụ tương tác chéo giữa các nền tảng",
        icon = Icons.Filled.FavoriteBorder,
        accentColor = Color(0xFFEC4899)
    ),
    PlatformOption(
        name = "XSMM",
        subtitle = "Nhiệm vụ tăng tương tác Facebook, TikTok",
        icon = Icons.Filled.Check,
        accentColor = Color(0xFF16A34A)
    ),
    PlatformOption(
        name = "Golike",
        subtitle = "Nhiệm vụ tương tác kiếm tiền mạng xã hội đa kênh",
        icon = Icons.Filled.Star,
        accentColor = Color(0xFFF59E0B)
    )
)

@Composable
fun TasksScreen(navController: NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.goHome() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Về trang chủ", tint = TextPrimary)
                }
                Spacer(Modifier.width(6.dp))
                Text("Nhiệm vụ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            IconButton(onClick = { /* TODO: hướng dẫn làm nhiệm vụ */ }) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Trợ giúp", tint = TextPrimary)
            }
        }

        Spacer(Modifier.height(8.dp))

        TaskBannerCard()

        Spacer(Modifier.height(24.dp))

        Text("Chọn nền tảng", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            platformOptions.forEach { option ->
                PlatformRow(
                    option = option,
                    onClick = {
                        if (option.name == "XSMM") {
                            val isXsmmLoggedIn = com.cayxu.app.data.local.XsmmAccountStore.isLoggedIn(context)
                            if (isXsmmLoggedIn) {
                                com.cayxu.app.ui.screens.xsmm.XsmmSession.restore(context)
                            }
                            val route = if (isXsmmLoggedIn || com.cayxu.app.ui.screens.xsmm.XsmmSession.isLoggedIn.value) {
                                com.cayxu.app.ui.navigation.Routes.XSMM_ACCOUNT
                            } else {
                                com.cayxu.app.ui.navigation.Routes.XSMM_LOGIN
                            }
                            navController.navigate(route) { launchSingleTop = true }
                        } else if (option.name == "Golike") {
                            com.cayxu.app.ui.screens.golike.GolikeSession.restore(context)
                            navController.navigate(com.cayxu.app.ui.navigation.Routes.GOLIKE_ACCOUNT) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(
                                com.cayxu.app.ui.navigation.Routes.simpleTaskPlatform(option.name)
                            ) { launchSingleTop = true }
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun TaskBannerCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF0F6FF), Color(0xFFE8F0FE))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .align(Alignment.CenterStart)
            ) {
                Text(
                    text = "Hoàn thành nhiệm vụ",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nhận ", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                    Text("xu", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    Text(" mỗi ngày", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Làm nhiệm vụ đơn giản để nhận xu và tăng cấp nhanh hơn!",
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFF6B7280)
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_mascot_coin),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 105.dp, height = 95.dp)
            )
        }
    }
}

@Composable
private fun PlatformRow(option: PlatformOption, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(option.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF111827)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = option.subtitle,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
