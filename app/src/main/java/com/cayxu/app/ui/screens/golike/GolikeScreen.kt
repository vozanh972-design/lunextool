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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.navigation.goHome
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.launch

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
internal fun GolikeStatusCard(navController: NavController, showStats: Boolean = true) {
    val isLoggedIn by GolikeSession.isLoggedIn

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoggedIn) {
            LoggedInGolikeCard(showStats = showStats)
        } else {
            LoggedOutGolikeCard(navController)
        }
    }
}

@Composable
private fun LoggedOutGolikeCard(navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(Routes.GOLIKE_LOGIN) { launchSingleTop = true } }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(WarningOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Login, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Chưa đăng nhập GoLike", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
            Text(
                "Bấm để đăng nhập GoLike",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

/** Card khi đã đăng nhập - đúng layout: tên/handle + đăng xuất, số dư + làm mới, 3 ô thống kê.
 *  [showStats] = false ở màn Golike chính (chưa vào nền tảng nào) - chỉ hiện tên/số dư,
 *  KHÔNG hiện "NV hôm nay/Thưởng hôm nay/Đã liên kết" (3 ô đó chỉ có ý nghĩa khi đã vào
 *  đúng 1 nền tảng cụ thể, ví dụ TikTok). */
@Composable
private fun LoggedInGolikeCard(showStats: Boolean = true) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val name by GolikeSession.name
    val handle by GolikeSession.handle
    val coin by GolikeSession.coin
    val todayIncome by GolikeSession.todayIncome
    val platformStats by GolikeSession.platformStats
    var isRefreshing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "Tài khoản Golike" }, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 19.sp)
                if (handle.isNotBlank()) {
                    Text("@$handle", color = TextSecondary, fontSize = 14.sp)
                }
            }
            IconButton(onClick = { GolikeSession.logout(context) }) {
                Icon(Icons.Filled.Logout, contentDescription = "Đăng xuất", tint = DangerRed)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(30.dp).background(SuccessGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AttachMoney, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Số dư: $coin xu",
                color = SuccessGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !isRefreshing) {
                        isRefreshing = true
                        scope.launch {
                            val ok = GolikeSession.refresh(context)
                            isRefreshing = false
                            if (!ok) {
                                android.widget.Toast.makeText(context, "Không thể làm mới, thử lại sau", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Làm mới", tint = Primary, modifier = Modifier.size(20.dp))
                }
            }
        }

        if (showStats) {
            // "Đã liên kết" = tổng số tài khoản TikTok đã bật (loại duy nhất app đang quản lý
            // tài khoản thật cho tới nay) - khi có thêm Facebook/YouTube/Instagram thật, cộng
            // thêm ở đây. Chỉ tính khi thực sự cần hiện (showStats = true).
            val linkedAccountsCount = remember {
                TikTokAccountsStore.getAccounts(context).count { it.enabled }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = TextSecondary.copy(alpha = 0.12f))
            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                GolikeStatItem(
                    icon = Icons.Filled.AttachMoney,
                    iconTint = SuccessGreen,
                    value = "+${todayIncome}đ",
                    valueColor = SuccessGreen,
                    label = "Thu nhập hôm nay",
                    modifier = Modifier.weight(1f)
                )
                GolikeStatItem(
                    icon = Icons.Filled.StarBorder,
                    iconTint = DangerRed,
                    value = "${platformStats.count { it.pendingCoin > 0 }}",
                    valueColor = DangerRed,
                    label = "Nền tảng đang có",
                    modifier = Modifier.weight(1f)
                )
                GolikeStatItem(
                    icon = Icons.Filled.Person,
                    iconTint = Primary,
                    value = "$linkedAccountsCount",
                    valueColor = Primary,
                    label = "Tài khoản",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GolikeStatItem(
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextSecondary, fontSize = 11.sp)
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
            GolikeStatusCard(navController, showStats = false)

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
