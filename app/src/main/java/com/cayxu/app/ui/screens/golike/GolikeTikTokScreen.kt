package com.cayxu.app.ui.screens.golike

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cayxu.app.data.local.GolikeAccountStore
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountStatus
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.repository.GolikeTikTokAccountRepository
import com.cayxu.app.data.repository.GolikeTikTokAccountsResult
import com.cayxu.app.ui.theme.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

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

    var selectedAccountId by remember(selectedTab) { mutableStateOf<String?>(null) }
    val effectiveSelectedId = selectedAccountId ?: accountsForSelectedTab.firstOrNull()?.uid

    // Đọc THẬT danh sách acc TikTok đã có trong GoLike (GET /api/tiktok-account, dùng
    // token đã đăng nhập sẵn - không hỏi lại) - để biết acc nào CHƯA có trong GoLike thì
    // hiện nút "+ Thêm" cho acc đó. Chỉ gọi khi đã đăng nhập Golike.
    val isGolikeLoggedIn by GolikeSession.isLoggedIn
    var golikeLinkedHandles by remember { mutableStateOf<Set<String>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun refreshGolikeLinkedHandles() {
        if (!isGolikeLoggedIn) {
            golikeLinkedHandles = emptySet()
            return
        }
        val token = GolikeAccountStore.getToken(context)
        if (!token.isNullOrBlank()) {
            when (val result = GolikeTikTokAccountRepository.fetchLinkedHandles(token)) {
                is GolikeTikTokAccountsResult.Success -> golikeLinkedHandles = result.handles
                is GolikeTikTokAccountsResult.Error -> Unit // giữ danh sách cũ, coi như chưa xác định được
            }
        }
    }

    LaunchedEffect(isGolikeLoggedIn) {
        refreshGolikeLinkedHandles()
    }

    // Sau khi bấm "Thêm" -> mở lớp nổi -> TikTok -> tự follow -> tự quay lại app (màn này
    // resume lại) - lúc đó tự làm mới danh sách để acc vừa thêm xong biến mất nút "Thêm"
    // luôn, không cần người dùng tự kéo refresh hay thoát vào lại màn.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch { refreshGolikeLinkedHandles() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

            // 3 loại TikTok - đổi sang dạng pill nhỏ, cuộn ngang (theo mẫu), vẫn giữ đúng
            // 3 loại như cũ, chỉ đổi kiểu hiển thị nhỏ gọn hơn.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TikTokTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val countForTab = enabledAccounts.count { it.variant == tab.variant }
                    TikTokTabChip(
                        label = tab.label,
                        count = countForTab,
                        isSelected = isSelected,
                        onClick = { selectedTab = tab }
                    )
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
                Text("Bấm vào 1 acc để bắt đầu chạy ngay.", color = TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                // ---- Danh sách tài khoản ----
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    accountsForSelectedTab.forEach { account ->
                        TikTokAccountCard(
                            navController = navController,
                            account = account,
                            isSelected = account.uid == effectiveSelectedId,
                            isLinkedToGolike = account.handle.lowercase() in golikeLinkedHandles,
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
                onClick = {
                    val selectedAccount = accountsForSelectedTab.firstOrNull { it.uid == effectiveSelectedId }
                    if (selectedAccount == null) {
                        android.widget.Toast.makeText(
                            context,
                            "Chưa có tài khoản nào để chạy - hãy bật tài khoản ở phần Quản lý tài khoản TikTok trước",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        // CHỈ có phần tài khoản là dữ liệu THẬT (lấy từ acc đang chọn). Các
                        // trường job (jobId/jobType/jobPrice/success/fail/earned/link) để
                        // trống vì CHƯA có API lấy job thật - màn nổi sẽ tự ẩn các dòng đó.
                        com.cayxu.app.ui.overlay.golike.startJobRunnerOverlay(
                            context = context,
                            navController = navController,
                            data = com.cayxu.app.ui.overlay.golike.JobRunData(
                                modeLabel = selectedTab.label,
                                accountHandle = selectedAccount.handle,
                                accountTaskCount = selectedAccount.taskCount
                            )
                        )
                    }
                },
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
private fun TikTokTabChip(label: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(
                if (isSelected) Primary.copy(alpha = 0.10f) else CardWhite,
                RoundedCornerShape(50)
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) Primary else TextSecondary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (isSelected) Primary else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .background(
                    if (isSelected) Primary.copy(alpha = 0.18f) else TextSecondary.copy(alpha = 0.12f),
                    CircleShape
                )
                .padding(horizontal = 7.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("$count", color = if (isSelected) Primary else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
private fun TikTokAccountCard(
    navController: NavController,
    account: TikTokAccount,
    isSelected: Boolean,
    isLinkedToGolike: Boolean,
    onClick: () -> Unit
) {
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
                if (isLinkedToGolike) {
                    Text("+0đ", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    val context = LocalContext.current
                    Row(
                        modifier = Modifier
                            .background(WarningOrange.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .clickable {
                                startAddToGolikeOverlay(context, navController, account)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thêm", color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
