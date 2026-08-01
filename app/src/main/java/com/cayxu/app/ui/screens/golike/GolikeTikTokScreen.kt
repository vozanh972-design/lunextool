package com.cayxu.app.ui.screens.golike

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountStatus
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.theme.*
import java.util.concurrent.TimeUnit

/**
 * Màn Golike riêng cho TikTok.
 *
 * Bố cục: 3 loại app TikTok (TikTok / TikTok Lite / TikTok Studio) nằm CÙNG 1 HÀNG dạng tab.
 * Chọn tab nào thì phần giữa hiện danh sách tài khoản ĐÃ BẬT (enabled = true) của đúng loại
 * đó - chỉ đọc lại từ TikTokAccountsStore, KHÔNG cho tick/chọn lại ở đây (tài khoản được bật
 * từ màn Quản lý tài khoản TikTok). Dưới cùng là 2 nút dùng chung "Cấu hình chạy" và "Chạy" -
 * CHƯA gắn logic chạy gì cả, chỉ là 2 nút hiển thị.
 *
 * Không đụng tới TikTokLinkAccountScreen/TikTokAccountsStore hay các màn khác.
 */
private enum class TikTokTab(val label: String, val variant: TikTokAppVariant) {
    STANDARD("TikTok", TikTokAppVariant.STANDARD),
    LITE("TikTok Lite", TikTokAppVariant.LITE),
    STUDIO("TikTok Studio", TikTokAppVariant.STUDIO)
}

@Composable
fun GolikeTikTokScreen(navController: NavController) {
    val context = LocalContext.current
    val allAccounts = remember { mutableStateOf(TikTokAccountsStore.getAccounts(context)) }
    val enabledAccounts = allAccounts.value.filter { it.enabled }

    var selectedTab by remember { mutableStateOf(TikTokTab.STANDARD) }
    val accountsForSelectedTab = enabledAccounts.filter { it.variant == selectedTab.variant }

    var searchQuery by remember { mutableStateOf("") }
    val filteredAccounts = accountsForSelectedTab.filter { account ->
        searchQuery.isBlank() ||
            account.displayName.contains(searchQuery, ignoreCase = true) ||
            account.handle.contains(searchQuery, ignoreCase = true)
    }

    var selectedAccountId by remember(selectedTab) { mutableStateOf<String?>(null) }
    val effectiveSelectedId = selectedAccountId ?: filteredAccounts.firstOrNull()?.uid

    val totalCount = accountsForSelectedTab.size
    val activeCount = accountsForSelectedTab.count { it.status == TikTokAccountStatus.ACTIVE }
    val checkingCount = accountsForSelectedTab.count { it.status == TikTokAccountStatus.CHECKING }
    val lockedCount = accountsForSelectedTab.count { it.status == TikTokAccountStatus.LOCKED }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("TikTok", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        // Phần nội dung cuộn riêng - chiều cao co giãn (weight), để 2 nút bên dưới
        // LUÔN cố định ở cuối màn hình, không phụ thuộc nội dung dài/ngắn hay có
        // tài khoản hay không (kể cả "Chưa có tài khoản nào được bật cho loại này").
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            GolikeStatusCard(navController)

            Spacer(Modifier.height(20.dp))
            Text("Loại TikTok", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tài khoản hiển thị bên dưới là tài khoản đã bật ở phần Quản lý tài khoản TikTok, không cần chọn lại ở đây.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            // 3 loại TikTok cùng 1 hàng - GIỮ NGUYÊN như cũ, không đổi.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardWhite, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TikTokTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val countForTab = enabledAccounts.count { it.variant == tab.variant }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Primary else CardWhite)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            tab.label,
                            color = if (isSelected) Color.White else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$countForTab tài khoản",
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (accountsForSelectedTab.isEmpty()) {
                // Không có tài khoản nào được bật cho loại này - giữ thông báo đơn giản.
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Tài khoản ${selectedTab.label}",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Chưa có tài khoản nào được bật cho loại này.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                // ---- Ô tìm kiếm ----
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm theo tên hoặc handle", color = TextSecondary) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite,
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // ---- 4 ô thống kê: Tổng / Hoạt động / Kiểm tra / Khoá ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatBox(label = "TỔNG", value = totalCount, color = TextPrimary, modifier = Modifier.weight(1f))
                    StatBox(label = "HOẠT ĐỘNG", value = activeCount, color = SuccessGreen, modifier = Modifier.weight(1f))
                    StatBox(label = "KIỂM TRA", value = checkingCount, color = WarningOrange, modifier = Modifier.weight(1f))
                    StatBox(label = "KHOÁ", value = lockedCount, color = DangerRed, modifier = Modifier.weight(1f))
                }

                Spacer(Modifier.height(12.dp))

                Text("Bấm vào 1 acc để bắt đầu chạy ngay.", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                // ---- Danh sách tài khoản ----
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredAccounts.forEach { account ->
                        TikTokAccountCard(
                            account = account,
                            isSelected = account.uid == effectiveSelectedId,
                            onClick = { selectedAccountId = account.uid }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 2 nút CỐ ĐỊNH ở cuối màn hình - luôn hiện dù không có tài khoản TikTok
        // nào được bật, không nằm trong phần cuộn ở trên nữa. CHƯA gắn logic.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            OutlinedButton(
                onClick = {
                    navController.navigate(
                        com.cayxu.app.ui.navigation.Routes.golikeTikTokConfig(selectedTab.name)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cấu hình chạy")
            }
            Button(
                onClick = { /* Chưa gắn logic - chỉ hiển thị nút */ },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Chạy")
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Text(label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("$value", color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Bảng màu avatar để tô theo từng tài khoản - chọn theo hash(uid), không dùng logic thật. */
private val AvatarColors = listOf(
    Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFF16A34A),
    Color(0xFFDB2777), Color(0xFFEA580C), Color(0xFF0891B2)
)

private fun avatarColorFor(uid: String): Color {
    val index = (uid.hashCode().let { if (it < 0) -it else it }) % AvatarColors.size
    return AvatarColors[index]
}

private fun monthsAgoText(createdAt: Long): String {
    val months = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createdAt).toInt() / 30
    return if (months <= 0) "mới tạo" else "tạo $months tháng"
}

private fun statusLabel(status: TikTokAccountStatus): String = when (status) {
    TikTokAccountStatus.ACTIVE -> "Hoạt động"
    TikTokAccountStatus.CHECKING -> "Đang kiểm tra"
    TikTokAccountStatus.LOCKED -> "Bị khoá"
}

@Composable
private fun statusColor(status: TikTokAccountStatus): Color = when (status) {
    TikTokAccountStatus.ACTIVE -> SuccessGreen
    TikTokAccountStatus.CHECKING -> WarningOrange
    TikTokAccountStatus.LOCKED -> DangerRed
}

@Composable
private fun TikTokAccountCard(account: TikTokAccount, isSelected: Boolean, onClick: () -> Unit) {
    val avatarColor = avatarColorFor(account.uid)
    val initials = (account.displayName.ifBlank { account.handle.ifBlank { "?" } })
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) SuccessGreen else TextSecondary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(avatarColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        account.displayName.ifBlank { account.handle },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "@${account.handle} (${monthsAgoText(account.createdAt)})",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Text("+0đ", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .border(1.dp, TextSecondary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                        .clickable { /* Chưa gắn logic - chỉ hiển thị nút */ }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Label, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        account.subName.ifBlank { "Thêm tên phụ" },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .background(statusColor(account.status).copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(6.dp).background(statusColor(account.status), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(statusLabel(account.status), color = statusColor(account.status), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${account.taskCount} N.vụ · Chưa chạy",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
