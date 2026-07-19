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
import com.cayxu.app.ui.theme.*

private data class WalletHistoryItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconColor: Color,
    val title: String,
    val time: String,
    val amount: String,
    val isPositive: Boolean
)

// TODO: backend hiện chỉ có endpoint verify_key.php, chưa có API lịch sử giao dịch/số dư Xu thật.
// Khi có API, thay `balanceXu` và `historyItems` bằng dữ liệu lấy từ ViewModel/API tương ứng.
private const val balanceXu = "128.500"

@Composable
private fun historyItems(): List<WalletHistoryItem> = listOf(
    WalletHistoryItem(Icons.Filled.CheckCircle, SuccessGreen, "Check-in hàng ngày", "Hôm nay, 09:30", "+500 Xu", true),
    WalletHistoryItem(Icons.Filled.PlayCircle, Primary, "Xem quảng cáo", "Hôm nay, 08:15", "+500 Xu", true),
    WalletHistoryItem(Icons.Filled.SportsEsports, Color(0xFFF97316), "Chơi game: Lucky Spin", "Hôm qua, 21:10", "+2.000 Xu", true),
    WalletHistoryItem(Icons.Filled.Assignment, Color(0xFF7C3AED), "Hoàn thành nhiệm vụ", "Hôm qua, 20:05", "+1.500 Xu", true),
    WalletHistoryItem(Icons.Filled.Group, Color(0xFFDB2777), "Giới thiệu bạn bè", "08/05/2026, 15:45", "+5.000 Xu", true),
    WalletHistoryItem(Icons.Filled.CardGiftcard, Color(0xFFF97316), "Đổi quà: Thẻ cào 10.000đ", "07/05/2026, 14:20", "-10.000 Xu", false),
    WalletHistoryItem(Icons.Filled.ArrowUpward, Primary, "Chuyển Xu cho bạn bè", "07/05/2026, 11:30", "-2.000 Xu", false)
)

private data class WalletTab(val title: String, val gradientColors: List<Color>)

private val walletTabs = listOf(
    WalletTab("Ví Golike", listOf(Color(0xFF9D5CE8), Color(0xFF7C3AED))),
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
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                }
                Spacer(Modifier.width(6.dp))
                Text("Ví", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Spacer(Modifier.height(14.dp))

            // 3 ví riêng theo từng nền tảng, dạng vuốt ngang (carousel) như thẻ
            // ngân hàng, thay vì xếp chồng dọc. Hiện dùng chung 1 số dư demo vì
            // backend chỉ có endpoint verify_key.php, chưa có API số dư riêng
            // theo từng nền tảng.
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { walletTabs.size })
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val tab = walletTabs[page]
                WalletBalanceCard(
                    title = tab.title,
                    gradientColors = tab.gradientColors,
                    balanceVisible = balanceVisible,
                    onToggleVisibility = { balanceVisible = !balanceVisible }
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
                Column {
                    val items = historyItems()
                    items.forEachIndexed { index, item ->
                        WalletHistoryRow(item)
                        if (index != items.lastIndex) {
                            HorizontalDivider(color = AppBackground, thickness = 1.dp)
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
    balanceVisible: Boolean,
    onToggleVisibility: () -> Unit
) {
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
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (balanceVisible) balanceXu else "••••••",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (balanceVisible) {
                        Spacer(Modifier.width(6.dp))
                        Text("Xu", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
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
