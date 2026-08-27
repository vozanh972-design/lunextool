package com.cayxu.app.ui.screens.xsmm

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.repository.XsmmAccountsRepository
import com.cayxu.app.data.repository.XsmmAccountsResult
import com.cayxu.app.data.repository.XsmmAddAccountResult
import com.cayxu.app.data.repository.XsmmAuthRepository
import com.cayxu.app.data.repository.XsmmLoginResult
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.launch

private val XsmmAccentStart = Color(0xFF34D399)
private val XsmmAccentEnd = Color(0xFF16A34A)

/**
 * Màn tài khoản XSMM - hiện username + số dư (points) dạng thẻ gradient ở trên, và NGAY BÊN
 * DƯỚI là danh sách acc TikTok (3 tab: TikTok / TikTok Lite / TikTok Studio, giống bố cục màn
 * TikTok của GoLike trước đây) - acc chưa "Thêm" hiện nút Thêm, acc đã thêm rồi thì ẩn nút đó.
 * Cuối màn có 2 nút cố định: "Cấu hình chạy" và "Chạy".
 *
 * Đã nối THẬT với API XSMM (/api/taskapi/accounts):
 *   - Vào màn/đổi tab -> gọi GET accounts?account_type=tiktok để biết @handle nào ĐÃ có trên
 *     XSMM (so khớp theo link_account) -> tự ẩn nút "Thêm" cho acc đó.
 *   - Bấm "Thêm" -> gọi THẬT POST accounts (type=tiktok, link_account, active=true) để thêm
 *     acc đó vào XSMM (đặt luôn làm "nick chạy").
 *
 * "Cấu hình chạy" và "Chạy" HIỆN VẪN LÀ PLACEHOLDER - XSMM có API GET tasks + POST
 * tasks/complete (xem XsmmTasksRepository, đã viết sẵn sàng nối) nhưng CHƯA gắn vào đây vì
 * "type" nhiệm vụ có nhiều loại (tiktok_follow/tiktok_like/tiktok_comment...) và chưa rõ màn
 * này nên để người dùng tự chọn loại nào hay mặc định loại nào.
 */
@Composable
fun XsmmAccountScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val username by XsmmSession.username
    val points by XsmmSession.points
    var isRefreshing by remember { mutableStateOf(false) }

    var selectedVariant by remember { mutableStateOf(TikTokAppVariant.STANDARD) }
    var linkedHandles by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isCheckingLinked by remember { mutableStateOf(false) }
    var addingUid by remember { mutableStateOf<String?>(null) }
    var selectedAccountUid by remember(selectedVariant) { mutableStateOf<String?>(null) }

    val allTikTokAccounts = remember { TikTokAccountsStore.getAccounts(context).filter { it.enabled } }
    val accountsForVariant = allTikTokAccounts.filter { it.variant == selectedVariant }

    // Gọi THẬT GET /api/taskapi/accounts?account_type=tiktok mỗi khi vào màn/đổi tab, để biết
    // acc nào ĐÃ có trên XSMM rồi (tự ẩn nút "Thêm"), acc nào chưa (hiện nút "Thêm").
    LaunchedEffect(selectedVariant) {
        val token = XsmmAccountStore.getToken(context) ?: return@LaunchedEffect
        isCheckingLinked = true
        when (val result = XsmmAccountsRepository.getAccounts(token, accountType = "tiktok")) {
            is XsmmAccountsResult.Success -> {
                linkedHandles = result.accounts.mapNotNull { acc ->
                    acc.linkAccount.substringAfterLast("@").trim('/').lowercase().takeIf { it.isNotBlank() }
                }.toSet()
            }
            is XsmmAccountsResult.Error -> Unit // giữ danh sách cũ, coi như chưa xác định được
        }
        isCheckingLinked = false
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
            Text("XSMM", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    isRefreshing = true
                    val token = XsmmAccountStore.getToken(context)
                    if (token.isNullOrBlank()) {
                        isRefreshing = false
                    } else {
                        scope.launch {
                            when (val result = XsmmAuthRepository.fetchUser(token)) {
                                is XsmmLoginResult.Success -> {
                                    XsmmSession.login(context, token, result.info.username, result.info.points)
                                }
                                is XsmmLoginResult.Error -> Unit
                            }
                            isRefreshing = false
                        }
                    }
                },
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Làm mới", tint = XsmmAccentEnd)
                }
            }
            IconButton(
                onClick = {
                    XsmmSession.logout(context)
                    navController.navigate(Routes.XSMM_LOGIN) {
                        popUpTo(Routes.XSMM_ACCOUNT) { inclusive = true }
                    }
                }
            ) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "Đăng xuất", tint = DangerRed)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---- Thẻ số dư ----
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(XsmmAccentStart, XsmmAccentEnd)))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                username.ifBlank { "Đang tải..." },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Số dư", color = Color(0xFFDCFCE7), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$points", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text("điểm", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Tài khoản TikTok", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Spacer(Modifier.height(10.dp))

            // ---- 3 tab: TikTok / TikTok Lite / TikTok Studio ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VariantTabChip("TikTok", TikTokAppVariant.STANDARD, selectedVariant, allTikTokAccounts) { selectedVariant = it }
                VariantTabChip("TikTok Lite", TikTokAppVariant.LITE, selectedVariant, allTikTokAccounts) { selectedVariant = it }
                VariantTabChip("TikTok Studio", TikTokAppVariant.STUDIO, selectedVariant, allTikTokAccounts) { selectedVariant = it }
            }

            Spacer(Modifier.height(12.dp))

            if (isCheckingLinked) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đang kiểm tra tài khoản trên XSMM...", color = TextSecondary, fontSize = 12.sp)
                }
            }

            if (accountsForVariant.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Chưa có tài khoản nào ở loại này - thêm ở phần Quản lý tài khoản TikTok trước.", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accountsForVariant.forEach { account ->
                        val handleLower = account.handle.trim().removePrefix("@").lowercase()
                        XsmmTikTokAccountCard(
                            account = account,
                            isSelected = account.uid == selectedAccountUid,
                            isAdded = handleLower in linkedHandles,
                            isAdding = addingUid == account.uid,
                            onClick = { selectedAccountUid = account.uid },
                            onAddClick = {
                                val token = XsmmAccountStore.getToken(context)
                                if (token.isNullOrBlank()) {
                                    android.widget.Toast.makeText(context, "Chưa đăng nhập XSMM", android.widget.Toast.LENGTH_SHORT).show()
                                    return@XsmmTikTokAccountCard
                                }
                                addingUid = account.uid
                                scope.launch {
                                    when (val result = XsmmAccountsRepository.addTikTokAccount(token, account.handle)) {
                                        is XsmmAddAccountResult.Success -> {
                                            linkedHandles = linkedHandles + handleLower
                                            android.widget.Toast.makeText(context, "Đã thêm @${account.handle} vào XSMM", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        is XsmmAddAccountResult.Error -> {
                                            android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    addingUid = null
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(90.dp))
        }

        // ---- 2 nút cố định dưới cùng: Cấu hình chạy + Chạy ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    android.widget.Toast.makeText(context, "Cấu hình chạy XSMM chưa có API - đang chờ backend", android.widget.Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = XsmmAccentEnd),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cấu hình chạy")
            }
            Button(
                onClick = {
                    if (selectedAccountUid == null) {
                        android.widget.Toast.makeText(context, "Hãy chọn 1 tài khoản để chạy", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "XSMM chưa có API lấy nhiệm vụ - đang chờ backend", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = XsmmAccentEnd),
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
private fun VariantTabChip(
    label: String,
    variant: TikTokAppVariant,
    selectedVariant: TikTokAppVariant,
    allAccounts: List<TikTokAccount>,
    onClick: (TikTokAppVariant) -> Unit
) {
    val isSelected = variant == selectedVariant
    val count = allAccounts.count { it.variant == variant }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (isSelected) XsmmAccentEnd.copy(alpha = 0.12f) else CardWhite, RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) XsmmAccentEnd else Color(0xFFEEF1F5),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick(variant) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "$label ($count)",
            color = if (isSelected) XsmmAccentEnd else TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun XsmmTikTokAccountCard(
    account: TikTokAccount,
    isSelected: Boolean,
    isAdded: Boolean,
    isAdding: Boolean,
    onClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) XsmmAccentEnd.copy(alpha = 0.06f) else CardWhite),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) XsmmAccentEnd else Color(0xFFEEF1F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("@${account.handle.ifBlank { "chưa_rõ" }}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(account.displayName.ifBlank { "Chưa có tên hiển thị" }, color = TextSecondary, fontSize = 12.sp)
            }
            when {
                isAdding -> {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
                isAdded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = XsmmAccentEnd, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đã thêm", color = XsmmAccentEnd, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                else -> {
                    Button(
                        onClick = onAddClick,
                        colors = ButtonDefaults.buttonColors(containerColor = XsmmAccentEnd),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Thêm", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
