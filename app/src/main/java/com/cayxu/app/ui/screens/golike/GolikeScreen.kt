package com.cayxu.app.ui.screens.golike

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.navigation.goHome
import com.cayxu.app.ui.theme.*

internal data class GolikePlatform(
    val name: String,
    val icon: ImageVector,
    val accentColor: Color
)

internal val golikePlatforms = listOf(
    GolikePlatform("Facebook", Icons.Filled.Facebook, Color(0xFF1877F2)),
    GolikePlatform("YouTube", Icons.Filled.PlayCircleFilled, Color(0xFFFF0000)),
    GolikePlatform("TikTok", Icons.Filled.MusicNote, Color(0xFF010101)),
    GolikePlatform("Instagram", Icons.Filled.CameraAlt, Color(0xFFE1306C))
)

/**
 * Card trạng thái đăng nhập Golike - dùng CHUNG cho mọi màn thuộc Golike (không vẽ lại card
 * mới riêng ở từng màn). To hơn bản cũ (padding/icon/chữ đều lớn hơn). Đọc trực tiếp từ
 * GolikeSession nên trạng thái luôn nhất quán dù đứng ở màn nào.
 */
@Composable
internal fun GolikeStatusCard(navController: NavController) {
    val isLoggedIn by GolikeSession.isLoggedIn
    val name by GolikeSession.name
    val coin by GolikeSession.coin
    val context = androidx.compose.ui.platform.LocalContext.current

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isLoggedIn) name.ifBlank { "Tài khoản Golike" } else "Tài khoản Golike",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 17.sp
                )
                Text(
                    if (isLoggedIn) "$coin xu" else "Chưa đăng nhập",
                    color = if (isLoggedIn) SuccessGreen else TextSecondary,
                    fontWeight = if (isLoggedIn) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp
                )
            }
            if (isLoggedIn) {
                TextButton(onClick = { GolikeSession.logout(context) }) {
                    Text("Đăng xuất", color = DangerRed)
                }
            } else {
                Button(
                    onClick = { navController.navigate(Routes.GOLIKE_LOGIN) { launchSingleTop = true } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) { Text("Đăng nhập") }
            }
        }
    }
}

@Composable
fun GolikeScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.goHome() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Về trang chủ", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("Golike", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            GolikeStatusCard(navController)

            Spacer(Modifier.height(20.dp))
            Text("Nền tảng liên kết", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                golikePlatforms.forEach { platform ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardWhite, RoundedCornerShape(14.dp))
                            .clickable {
                                navController.navigate(Routes.golikePlatform(platform.name)) { launchSingleTop = true }
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(platform.accentColor.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(platform.icon, contentDescription = null, tint = platform.accentColor, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(platform.name, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
        }
    }
}
