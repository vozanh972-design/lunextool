package com.cayxu.app.ui.screens.linkaccount.tiktok

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cayxu.app.automation.tiktok.TikTokCaptureBridge
import com.cayxu.app.automation.tiktok.TikTokCaptureState
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountStatus
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.model.VerifyKeyResponse
import com.cayxu.app.data.repository.AuthRepository
import com.cayxu.app.data.repository.AuthResult
import com.cayxu.app.util.DeviceUtils
import com.cayxu.app.ui.theme.*
import java.util.concurrent.TimeUnit

private enum class TikTokFilter(val label: String) {
    ALL("Tổng"),
    ACTIVE("Hoạt động"),
    CHECKING("Kiểm tra"),
    LOCKED("Khóa")
}

/**
 * Màn hình quản lý tài khoản TikTok - RIÊNG cho TikTok, dùng TikTokAccountsStore/route
 * độc lập, không đụng tới LinkAccountScreen/AddAccountScreen dùng chung cho các nền
 * tảng khác (Instagram/LinkedIn/...).
 */
@Composable
fun TikTokLinkAccountScreen(navController: NavController) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(TikTokAccountsStore.getAccounts(context)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(TikTokFilter.ALL) }
    var showAddSheet by remember { mutableStateOf(false) }
    var subNameDialogFor by remember { mutableStateOf<TikTokAccount?>(null) }

    fun refresh() { accounts = TikTokAccountsStore.getAccounts(context) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Khi lớp nổi báo đã quét được @handle, tự lưu vào danh sách và trả bridge về Idle.
    LaunchedEffect(Unit) {
        TikTokCaptureBridge.state.collect { state ->
            if (state is TikTokCaptureState.Captured) {
                TikTokAccountsStore.addFromCapture(
                    context = context,
                    handle = state.handle,
                    displayName = state.displayName,
                    avatarUrl = state.avatarUrl,
                    variant = state.variant
                )
                refresh()
                Toast.makeText(context, "Đã thêm tài khoản ${state.handle}", Toast.LENGTH_SHORT).show()
                TikTokCaptureBridge.reset()
            }
        }
    }

    val filteredByStatus = when (filter) {
        TikTokFilter.ALL -> accounts
        TikTokFilter.ACTIVE -> accounts.filter { it.status == TikTokAccountStatus.ACTIVE }
        TikTokFilter.CHECKING -> accounts.filter { it.status == TikTokAccountStatus.CHECKING }
        TikTokFilter.LOCKED -> accounts.filter { it.status == TikTokAccountStatus.LOCKED }
    }
    val filtered = if (query.isBlank()) filteredByStatus else filteredByStatus.filter {
        it.handle.contains(query, ignoreCase = true) ||
            it.displayName.contains(query, ignoreCase = true) ||
            it.subName.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TikTok", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Quản lý tài khoản · ${accounts.size} tổng", fontSize = 11.sp, color = TextSecondary)
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            KeyExpiryBanner()

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Tìm theo tên hoặc handle", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CardWhite,
                    unfocusedContainerColor = CardWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterStatCard(
                    label = TikTokFilter.ALL.label,
                    count = accounts.size,
                    color = TextPrimary,
                    selected = filter == TikTokFilter.ALL,
                    modifier = Modifier.weight(1f)
                ) { filter = TikTokFilter.ALL }
                FilterStatCard(
                    label = TikTokFilter.ACTIVE.label,
                    count = accounts.count { it.status == TikTokAccountStatus.ACTIVE },
                    color = SuccessGreen,
                    selected = filter == TikTokFilter.ACTIVE,
                    modifier = Modifier.weight(1f)
                ) { filter = TikTokFilter.ACTIVE }
                FilterStatCard(
                    label = TikTokFilter.CHECKING.label,
                    count = accounts.count { it.status == TikTokAccountStatus.CHECKING },
                    color = Color(0xFFE08A2E),
                    selected = filter == TikTokFilter.CHECKING,
                    modifier = Modifier.weight(1f)
                ) { filter = TikTokFilter.CHECKING }
                FilterStatCard(
                    label = TikTokFilter.LOCKED.label,
                    count = accounts.count { it.status == TikTokAccountStatus.LOCKED },
                    color = DangerRed,
                    selected = filter == TikTokFilter.LOCKED,
                    modifier = Modifier.weight(1f)
                ) { filter = TikTokFilter.LOCKED }
            }

            Spacer(Modifier.height(12.dp))

            TikTokTipsBanner()

            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (accounts.isEmpty()) "Chưa có tài khoản TikTok nào, bấm \"Thêm\" để lấy tài khoản từ app TikTok" else "Không tìm thấy tài khoản phù hợp",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filtered, key = { it.uid }) { account ->
                        TikTokAccountCard(
                            account = account,
                            onAddSubName = { subNameDialogFor = account },
                            onRemove = {
                                TikTokAccountsStore.removeAccount(context, account.uid)
                                refresh()
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = { showAddSheet = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = CardWhite, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Thêm", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    if (showAddSheet) {
        TikTokAddAccountSheet(onDismiss = { showAddSheet = false })
    }

    subNameDialogFor?.let { account ->
        SubNameDialog(
            initial = account.subName,
            onDismiss = { subNameDialogFor = null },
            onConfirm = { newName ->
                TikTokAccountsStore.setSubName(context, account.uid, newName)
                refresh()
                subNameDialogFor = null
            }
        )
    }
}

/**
 * Hiển thị ngày hết hạn / số ngày còn lại của KEY đang dùng - liên kết thẳng với hệ thống
 * key ở màn Trang chủ (AuthRepository.verifyKey), không tạo hệ key riêng cho TikTok.
 */
@Composable
private fun KeyExpiryBanner() {
    val context = LocalContext.current
    var info by remember { mutableStateOf<VerifyKeyResponse?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val key = SecurePrefs(context).getKey()
        if (key.isNullOrBlank()) {
            errorMsg = "Chưa đăng nhập key"
            return@LaunchedEffect
        }
        when (val result = AuthRepository().verifyKey(key, DeviceUtils.getAndroidId(context))) {
            is AuthResult.Success -> info = result.data
            is AuthResult.ApiError -> errorMsg = result.message
            is AuthResult.NetworkError -> errorMsg = "Không kiểm tra được hạn key (mất mạng)"
        }
    }

    val (label, color) = when {
        info?.expiresAt != null -> "Key hết hạn: ${info?.expiresAt}" +
            (info?.daysLeft?.let { " · còn $it ngày" } ?: "") to
            (if ((info?.daysLeft ?: 99) <= 3) DangerRed else Primary)
        errorMsg != null -> errorMsg!! to TextSecondary
        else -> "Đang kiểm tra hạn key..." to TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.VpnKey, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private val tikTokTips = listOf(
    "Giữ một tài khoản để bật chế độ chọn nhiều, rồi chạm để chọn thêm. Chọn xong có thể bật/tắt hoặc xoá hàng loạt.",
    "Tài khoản mới (chưa đủ ngày tuổi) thường chỉ nhận job giá thấp. Nuôi nick vài ngày để mở job giá cao hơn.",
    "Badge ⭐ đánh dấu nick vừa thêm gần đây.",
    "Bấm \"Chạy\" để buộc dừng và mở lại TikTok cho tài khoản đó trước khi chạy nhiệm vụ."
)

@Composable
private fun TikTokTipsBanner() {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InfoBlueBg)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mẹo dùng tài khoản TikTok", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            tikTokTips.forEach { tip ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("•  ", color = Primary, fontSize = 11.sp)
                    Text(tip, color = TextPrimary, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun FilterStatCard(label: String, count: Int, color: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .then(
                if (selected) Modifier.background(color.copy(alpha = 0.06f))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(count.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TikTokAccountCard(
    account: TikTokAccount,
    onAddSubName: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = account.displayName.ifBlank { account.handle.ifBlank { "Chưa xác định" } }
    val avatarColor = colorForKey(account.uid)
    val initials = title.trim().take(2).uppercase()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (account.handle.isNotBlank()) "@${account.handle.removePrefix("@")}" else "Chưa có handle") +
                            "  ·  ${variantLabel(account.variant)}  ·  tạo ${relativeAgo(account.createdAt)}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onAddSubName,
                    label = {
                        Text(
                            if (account.subName.isBlank()) "Thêm tên phụ" else account.subName,
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(account.status)
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Tuỳ chọn", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Xoá") }, onClick = { menuOpen = false; onRemove() })
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "${account.taskCount} N.vụ · ${if (account.taskCount == 0) "Chưa chạy" else "Đang chạy"}",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun StatusBadge(status: TikTokAccountStatus) {
    val (label, color) = when (status) {
        TikTokAccountStatus.ACTIVE -> "Hoạt động" to SuccessGreen
        TikTokAccountStatus.CHECKING -> "Kiểm tra" to Color(0xFFE08A2E)
        TikTokAccountStatus.LOCKED -> "Khóa" to DangerRed
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SubNameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm tên phụ") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ví dụ: Acc seeding, Acc chính...") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Huỷ") }
        }
    )
}

private val avatarPalette = listOf(
    Color(0xFF2E86DE), Color(0xFF8E44AD), Color(0xFF16A085),
    Color(0xFFD35400), Color(0xFFE74C3C), Color(0xFF2ECC71)
)

private fun colorForKey(key: String): Color {
    val idx = (key.hashCode().let { if (it < 0) -it else it }) % avatarPalette.size
    return avatarPalette[idx]
}

private fun variantLabel(variant: com.cayxu.app.data.local.TikTokAppVariant): String = when (variant) {
    com.cayxu.app.data.local.TikTokAppVariant.STANDARD -> "TikTok"
    com.cayxu.app.data.local.TikTokAppVariant.LITE -> "TikTok Lite"
    com.cayxu.app.data.local.TikTokAppVariant.STUDIO -> "TikTok Studio"
}

private fun relativeAgo(timestampMs: Long): String {
    if (timestampMs <= 0L) return "vừa xong"
    val diff = System.currentTimeMillis() - timestampMs
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        days <= 0 -> "hôm nay"
        days < 30 -> "$days ngày"
        days < 365 -> "${days / 30} tháng"
        else -> "${days / 365} năm"
    }
}
