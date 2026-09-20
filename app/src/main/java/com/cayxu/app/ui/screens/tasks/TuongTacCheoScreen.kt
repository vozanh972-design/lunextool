package com.cayxu.app.ui.screens.tasks

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.data.local.TtcAccount
import com.cayxu.app.data.local.TtcAccountsStore
import com.cayxu.app.tuongtaccheo.TuongTacCheoApiClient
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TtcPink = Color(0xFFEC4899)
private val FbBlue = Color(0xFF1877F2)

/**
 * Màn hình Tương Tác Chéo:
 *   - 2 Thẻ (Tabs) ở trên: Acc TTC / Facebook
 *   - Checkbox rút gọn thành "Tất cả"
 *   - Nút thêm acc TTC là dấu "+"
 *   - Cơ chế trượt từ dưới lên (ModalBottomSheet) để dán token mỗi dòng 1 acc
 *   - Có 2 ô chọn: Token và Proxy
 *   - 2 nút: "Hủy" và "Đăng nhập"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuongTacCheoScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tab đang chọn: 0 = Acc TTC, 1 = Facebook
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dữ liệu tài khoản
    var ttcAccounts by remember { mutableStateOf(TtcAccountsStore.getAccounts(context)) }
    var fbAccounts by remember { mutableStateOf(FacebookAccountsStore.getAccounts(context)) }

    // Quản lý selection
    var selectedTtcUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFbUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // BottomSheet thêm acc TTC (trượt từ dưới lên)
    var showAddTtcSheet by remember { mutableStateOf(false) }

    fun reloadData() {
        ttcAccounts = TtcAccountsStore.getAccounts(context)
        fbAccounts = FacebookAccountsStore.getAccounts(context)
    }

    if (showAddTtcSheet) {
        AddTtcBottomSheet(
            onDismiss = { showAddTtcSheet = false },
            onLogin = { lines, isToken, isProxy ->
                var successCount = 0
                var failCount = 0
                withContext(Dispatchers.IO) {
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank()) {
                            val token: String
                            val proxy: String
                            if (isToken && isProxy) {
                                val parts = trimmed.split("|")
                                token = parts.getOrNull(0)?.trim().orEmpty()
                                proxy = parts.getOrNull(1)?.trim().orEmpty()
                            } else if (isToken) {
                                val parts = trimmed.split("|")
                                token = parts.getOrNull(0)?.trim().orEmpty()
                                proxy = if (parts.size > 1) parts[1].trim() else ""
                            } else {
                                token = ""
                                proxy = trimmed
                            }

                            if (token.isNotBlank()) {
                                try {
                                    val client = TuongTacCheoApiClient(
                                        proxyStr = proxy.ifBlank { null }
                                    )
                                    val loggedAcc = client.loginWithToken(token)
                                    TtcAccountsStore.addAccount(
                                        context,
                                        TtcAccount(
                                            username = loggedAcc.username,
                                            token = token,
                                            cookie = loggedAcc.cookie.orEmpty(),
                                            proxy = proxy,
                                            coins = loggedAcc.sodu,
                                            isLive = true
                                        )
                                    )
                                    successCount++
                                } catch (e: Exception) {
                                    failCount++
                                    val fallbackUser = if (token.length > 12) "TTC_${token.take(8)}" else token
                                    TtcAccountsStore.addAccount(
                                        context,
                                        TtcAccount(
                                            username = fallbackUser,
                                            token = token,
                                            cookie = "",
                                            proxy = proxy,
                                            coins = 0L,
                                            isLive = false
                                        )
                                    )
                                }
                            } else if (proxy.isNotBlank()) {
                                val fallbackUser = "Proxy_${proxy.substringBefore(":").takeLast(6)}"
                                TtcAccountsStore.addAccount(
                                    context,
                                    TtcAccount(
                                        username = fallbackUser,
                                        token = "",
                                        cookie = "",
                                        proxy = proxy,
                                        coins = 0L,
                                        isLive = true
                                    )
                                )
                                successCount++
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    reloadData()
                    showAddTtcSheet = false
                    if (failCount > 0) {
                        Toast.makeText(context, "TTC: $successCount thành công, $failCount thất bại", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Đã đăng nhập thành công $successCount tài khoản TTC", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // ==================== TOP BAR ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Tương tác chéo",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                reloadData()
                scope.launch(Dispatchers.IO) {
                    val accs = TtcAccountsStore.getAccounts(context)
                    var updated = false
                    accs.forEach { acc ->
                        if (acc.token.isNotBlank()) {
                            try {
                                val client = TuongTacCheoApiClient(proxyStr = acc.proxy.ifBlank { null })
                                val res = client.loginWithToken(acc.token)
                                if (res.sodu != acc.coins || res.username != acc.username) {
                                    TtcAccountsStore.addAccount(
                                        context,
                                        acc.copy(username = res.username, coins = res.sodu, isLive = true)
                                    )
                                    updated = true
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    if (updated) {
                        withContext(Dispatchers.Main) { reloadData() }
                    }
                }
            }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Tải lại", tint = TextSecondary)
            }
        }

        // ==================== 2 THẺ (TABS) Ở TRÊN ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TtcTabButton(
                label = "Acc TTC",
                count = ttcAccounts.size,
                isSelected = selectedTab == 0,
                selectedColor = TtcPink,
                icon = Icons.Filled.SwapHoriz,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 0 }
            )

            TtcTabButton(
                label = "Facebook",
                count = fbAccounts.size,
                isSelected = selectedTab == 1,
                selectedColor = FbBlue,
                icon = Icons.Filled.Facebook,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 1 }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ==================== NỘI DUNG BÊN DƯỚI ====================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (selectedTab == 0) {
                // ---------- TAB ACC TTC ----------
                TtcAccountsTabContent(
                    accounts = ttcAccounts,
                    selectedUsernames = selectedTtcUsernames,
                    onToggle = { username ->
                        selectedTtcUsernames = if (username in selectedTtcUsernames) {
                            selectedTtcUsernames - username
                        } else {
                            selectedTtcUsernames + username
                        }
                    },
                    onSelectAll = { checkAll ->
                        selectedTtcUsernames = if (checkAll) ttcAccounts.map { it.username }.toSet() else emptySet()
                    },
                    onAddNew = { showAddTtcSheet = true },
                    onDelete = { username ->
                        TtcAccountsStore.removeAccount(context, username)
                        selectedTtcUsernames = selectedTtcUsernames - username
                        reloadData()
                    }
                )
            } else {
                // ---------- TAB FACEBOOK ----------
                FbAccountsTabContent(
                    accounts = fbAccounts,
                    selectedUids = selectedFbUids,
                    onToggle = { uid ->
                        selectedFbUids = if (uid in selectedFbUids) {
                            selectedFbUids - uid
                        } else {
                            selectedFbUids + uid
                        }
                    },
                    onSelectAll = { checkAll ->
                        selectedFbUids = if (checkAll) fbAccounts.map { it.uid }.toSet() else emptySet()
                    },
                    onAddNew = {
                        navController.navigate(com.cayxu.app.ui.navigation.Routes.ACCOUNT)
                    }
                )
            }
        }

        // ==================== FOOTER / NÚT CHẠY ====================
        Surface(
            color = CardWhite,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Đã chọn: ${selectedTtcUsernames.size} acc TTC  •  ${selectedFbUids.size} acc FB",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        Toast.makeText(
                            context,
                            "Bắt đầu chạy với ${selectedTtcUsernames.size} acc TTC và ${selectedFbUids.size} acc FB",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TtcPink),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chạy", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

/** Thẻ Tab chuyển đổi ở trên */
@Composable
private fun TtcTabButton(
    label: String,
    count: Int,
    isSelected: Boolean,
    selectedColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) selectedColor.copy(alpha = 0.12f) else CardWhite,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) selectedColor else Color(0xFFE5E7EB)
        ),
        shadowElevation = if (isSelected) 0.dp else 0.5.dp,
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$label ($count)",
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) selectedColor else TextPrimary
            )
        }
    }
}

/** Nội dung danh sách tài khoản TTC */
@Composable
private fun TtcAccountsTabContent(
    accounts: List<TtcAccount>,
    selectedUsernames: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAddNew: () -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Thanh công cụ: Checkbox "Tất cả" + Dấu cộng "+"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAllSelected = accounts.isNotEmpty() && selectedUsernames.size == accounts.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelectAll(!isAllSelected) }
            ) {
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onSelectAll(it) },
                    colors = CheckboxDefaults.colors(checkedColor = TtcPink)
                )
                Text(
                    "Tất cả",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            // Nút dấu cộng "+"
            FilledIconButton(
                onClick = onAddNew,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = TtcPink),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Thêm acc TTC", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Chưa có tài khoản Tương tác chéo", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    FilledIconButton(
                        onClick = onAddNew,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = TtcPink),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Thêm acc TTC", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.username }) { acc ->
                    val isSelected = acc.username in selectedUsernames
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) TtcPink.copy(alpha = 0.05f) else CardWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) TtcPink else Color(0xFFE5E7EB)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(acc.username) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggle(acc.username) },
                                colors = CheckboxDefaults.colors(checkedColor = TtcPink)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(TtcPink.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.SwapHoriz,
                                    contentDescription = null,
                                    tint = TtcPink,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    acc.username.ifBlank { "TTC Account" },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (acc.coins > 0) "${acc.coins} xu" else "Sẵn sàng",
                                        fontSize = 12.sp,
                                        color = if (acc.isLive) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (acc.token.isNotBlank()) {
                                        Text(
                                            text = " • Token",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    if (acc.proxy.isNotBlank()) {
                                        Text(
                                            text = " • Proxy",
                                            fontSize = 11.sp,
                                            color = Color(0xFF0284C7),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { onDelete(acc.username) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Xóa",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Nội dung danh sách tài khoản Facebook */
@Composable
private fun FbAccountsTabContent(
    accounts: List<FacebookAccount>,
    selectedUids: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAddNew: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isAllSelected = accounts.isNotEmpty() && selectedUids.size == accounts.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSelectAll(!isAllSelected) }
            ) {
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onSelectAll(it) },
                    colors = CheckboxDefaults.colors(checkedColor = FbBlue)
                )
                Text(
                    "Tất cả (${selectedUids.size}/${accounts.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            OutlinedButton(
                onClick = onAddNew,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FbBlue),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = FbBlue, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Quản lý FB", fontSize = 12.sp, color = FbBlue, fontWeight = FontWeight.Bold)
            }
        }

        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Facebook,
                        contentDescription = null,
                        tint = FbBlue.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Chưa có tài khoản Facebook nào", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onAddNew,
                        colors = ButtonDefaults.buttonColors(containerColor = FbBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ Thêm tài khoản Facebook")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.uid }) { acc ->
                    val isSelected = acc.uid in selectedUids
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) FbBlue.copy(alpha = 0.05f) else CardWhite
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.2.dp else 0.8.dp,
                            color = if (isSelected) FbBlue else Color(0xFFE5E7EB)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(acc.uid) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggle(acc.uid) },
                                colors = CheckboxDefaults.colors(checkedColor = FbBlue)
                            )
                            Spacer(Modifier.width(8.dp))

                            if (acc.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = acc.avatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(FbBlue.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Facebook,
                                        contentDescription = null,
                                        tint = FbBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    acc.name.ifBlank { acc.uid },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "UID: ${acc.uid.take(15)}...",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    if (acc.pages.isNotEmpty()) {
                                        Text(
                                            text = " • ${acc.pages.size} Page",
                                            fontSize = 11.sp,
                                            color = FbBlue,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** BottomSheet thêm tài khoản TTC trượt từ dưới lên */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTtcBottomSheet(
    onDismiss: () -> Unit,
    onLogin: suspend (lines: List<String>, isToken: Boolean, isProxy: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isTokenSelected by remember { mutableStateOf(true) }
    var isProxySelected by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Thêm tài khoản Tương tác chéo",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
            )

            // 2 ô để chọn: Token và Proxy (chọn được 1 hoặc cả 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Ô chọn Token
                FilterChip(
                    selected = isTokenSelected,
                    onClick = {
                        if (isTokenSelected && !isProxySelected) return@FilterChip
                        isTokenSelected = !isTokenSelected
                    },
                    leadingIcon = if (isTokenSelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Token", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TtcPink.copy(alpha = 0.15f),
                        selectedLabelColor = TtcPink,
                        selectedLeadingIconColor = TtcPink
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Ô chọn Proxy
                FilterChip(
                    selected = isProxySelected,
                    onClick = {
                        if (!isTokenSelected && isProxySelected) return@FilterChip
                        isProxySelected = !isProxySelected
                    },
                    leadingIcon = if (isProxySelected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("Proxy", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TtcPink.copy(alpha = 0.15f),
                        selectedLabelColor = TtcPink,
                        selectedLeadingIconColor = TtcPink
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Hướng dẫn định dạng & placeholder theo lựa chọn
            val formatLabel = when {
                isTokenSelected && isProxySelected -> "Dán danh sách (định dạng token|proxy - mỗi dòng 1 acc):"
                isTokenSelected -> "Dán danh sách token (mỗi dòng 1 tài khoản):"
                else -> "Dán danh sách proxy (mỗi dòng 1 proxy):"
            }
            val placeholderText = when {
                isTokenSelected && isProxySelected -> "Dán token|proxy tại đây...\ntoken_1|1.2.3.4:8080\ntoken_2|1.2.3.4:8080:user:pass"
                isTokenSelected -> "Dán token tại đây...\ntoken_acc_1\ntoken_acc_2\ntoken_acc_3"
                else -> "Dán proxy tại đây...\n1.2.3.4:8080\n1.2.3.4:8080:user:pass"
            }

            // Bảng để dán token / proxy
            Column {
                Text(
                    formatLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            placeholderText,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    },
                    minLines = 5,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TtcPink,
                        cursorColor = TtcPink
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2 Nút: Hủy và Đăng nhập
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("Hủy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val lines = inputText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                        if (lines.isEmpty() || isLoggingIn) {
                            return@Button
                        }
                        isLoggingIn = true
                        scope.launch {
                            try {
                                onLogin(lines, isTokenSelected, isProxySelected)
                            } finally {
                                isLoggingIn = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TtcPink),
                    shape = RoundedCornerShape(10.dp),
                    enabled = inputText.isNotBlank() && !isLoggingIn,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Đang xử lý...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Đăng nhập", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
