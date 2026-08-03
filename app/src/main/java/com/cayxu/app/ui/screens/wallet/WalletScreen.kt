package com.cayxu.app.ui.screens.wallet

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.R
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.navigation.goHome
import com.cayxu.app.ui.screens.golike.GolikeSession
import com.cayxu.app.ui.screens.golike.golikePlatformColor
import com.cayxu.app.ui.screens.golike.golikePlatformDisplayName
import com.cayxu.app.ui.theme.*

private data class WalletHistoryItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val title: String,
    val time: String,
    val amount: String,
    val isPositive: Boolean
)

private data class WalletTab(val title: String, val gradientColors: List<Color>, val isGolike: Boolean = false)

private val walletTabs = listOf(
    WalletTab("Ví Golike", listOf(Color(0xFF9D5CE8), Color(0xFF7C3AED)), isGolike = true),
    WalletTab("Ví Traodoisub", listOf(Color(0xFF418DFC), Color(0xFF7950F6))),
    WalletTab("Ví Tuongtaccheo", listOf(Color(0xFFF472B6), Color(0xFFEC4899)))
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WalletScreen(navController: NavController) {
    var balanceVisible by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.goHome() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                }
                Spacer(Modifier.width(6.dp))
                Text("Ví", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Spacer(Modifier.height(14.dp))

            // 3 ví riêng theo từng nền tảng, dạng vuốt ngang (carousel) như thẻ ngân hàng.
            // Ví Golike đã lấy SỐ DƯ THẬT từ GolikeSession (đăng nhập bằng token Bearer) -
            // 2 ví còn lại (Traodoisub/Tuongtaccheo) chưa có backend nên vẫn chỉ hiện "--".
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { walletTabs.size })
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val tab = walletTabs[page]
                WalletBalanceCard(
                    title = tab.title,
                    gradientColors = tab.gradientColors,
                    isGolike = tab.isGolike,
                    balanceVisible = balanceVisible,
                    onToggleVisibility = { balanceVisible = !balanceVisible },
                    navController = navController
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(walletTabs.size) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isActive) 20.dp else 6.dp, 6.dp)
                            .background(
                                if (isActive) walletTabs[index].gradientColors.last() else Color(0xFFD9E7FF),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lịch sử cày Xu", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WalletFilterChip(text = "Tất cả", leadingIcon = null, showDropdownArrow = true)
                WalletFilterChip(text = "7 ngày qua", leadingIcon = Icons.Filled.CalendarToday, showDropdownArrow = false)
            }

            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val isGolikeLoggedIn by GolikeSession.isLoggedIn
                val platformStats by GolikeSession.platformStats

                if (!isGolikeLoggedIn) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Cần đăng nhập GoLike để xem lịch sử cày Xu",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { navController.navigate(Routes.GOLIKE_LOGIN) { launchSingleTop = true } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                        ) {
                            Text("Đăng nhập GoLike")
                        }
                    }
                } else {
                    // GoLike hiện chưa có API trả về danh sách lịch sử giao dịch dạng nhiều
                    // dòng theo thời gian (data: [] trống trong response thật) - CHỈ có
                    // breakdown thu nhập hôm nay theo TỪNG nền tảng (GET /api/statistics/
                    // report) - nên hiện đúng breakdown thật đó, mỗi nền tảng đang có thu
                    // nhập hôm nay là 1 dòng, KHÔNG bịa thêm các dòng lịch sử cũ hơn.
                    val realHistory = platformStats
                        .filter { it.pendingCoin > 0 }
                        .sortedByDescending { it.pendingCoin }

                    if (realHistory.isEmpty()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Chưa có thu nhập hôm nay trên nền tảng nào", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        Column {
                            realHistory.forEachIndexed { index, stat ->
                                WalletHistoryRow(
                                    WalletHistoryItem(
                                        icon = Icons.Filled.Bolt,
                                        iconColor = golikePlatformColor(stat.platform),
                                        title = golikePlatformDisplayName(stat.platform),
                                        time = "Hôm nay",
                                        amount = "+${stat.pendingCoin}đ",
                                        isPositive = true
                                    )
                                )
                                if (index != realHistory.lastIndex) {
                                    HorizontalDivider(color = AppBackground, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            SecurityInfoCard { }

            Spacer(Modifier.height(90.dp))
        }
}

@Composable
private fun WalletBalanceCard(
    title: String,
    gradientColors: List<Color>,
    isGolike: Boolean,
    balanceVisible: Boolean,
    onToggleVisibility: () -> Unit,
    navController: NavController
) {
    val isGolikeLoggedIn by GolikeSession.isLoggedIn
    val golikeCoin by GolikeSession.coin

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
        ) {
            Image(
                painter = painterResource(R.drawable.ic_wallet_coin),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(width = 130.dp, height = 126.dp)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color(0xFFE5DCFF), fontSize = 13.sp)
                    if (isGolike && isGolikeLoggedIn) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            if (balanceVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "Ẩn/hiện số dư",
                            tint = Color(0xFFE5DCFF),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onToggleVisibility)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (isGolike && !isGolikeLoggedIn) {
                    // Không còn hardcode số dư nữa - chưa đăng nhập GoLike thì báo cần đăng
                    // nhập thay vì hiện số giả.
                    Text(
                        "Cần đăng nhập để xem số dư",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            navController.navigate(Routes.GOLIKE_LOGIN) { launchSingleTop = true }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Đăng nhập GoLike", color = gradientColors.last())
                    }
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        val displayBalance = if (isGolike) golikeCoin else "--"
                        Text(
                            if (balanceVisible) displayBalance else "••••••",
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (balanceVisible) {
                            Spacer(Modifier.width(6.dp))
                            Text("Xu", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tài khoản an toàn", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun WalletFilterChip(
    text: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    showDropdownArrow: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(CardWhite, RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (showDropdownArrow) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WalletHistoryRow(item: WalletHistoryItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(item.iconColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = null, tint = item.iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
            Text(item.time, color = TextSecondary, fontSize = 12.sp)
        }
        Text(
            item.amount,
            color = if (item.isPositive) SuccessGreen else DangerRed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun SecurityInfoCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(Primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Bảo mật & An toàn", fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 13.sp)
                Text("Mọi giao dịch đều được mã hóa và bảo vệ tuyệt đối.", color = TextSecondary, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
    }
}
