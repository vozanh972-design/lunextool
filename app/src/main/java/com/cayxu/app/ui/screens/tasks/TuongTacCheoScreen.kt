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
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

private val TtcPink = Color(0xFFEC4899)
private val FbBlue = Color(0xFF1877F2)

/**
 * Màn hình Tương Tác Chéo dạng 2 THẺ (Tabs):
 *   - Bấm vào thẻ "Acc TTC" -> Nội dung bên dưới hiển thị danh sách tài khoản TTC
 *   - Bấm qua thẻ "Facebook" -> Nội dung bên dưới hiển thị danh sách tài khoản Facebook
 * Không hiển thị song song dọc chật chội.
 */
@Composable
fun TuongTacCheoScreen(navController: NavController) {
    val context = LocalContext.current

    // Tab đang chọn: 0 = Acc TTC, 1 = Facebook
    var selectedTab by remember { mutableIntStateOf(0) }

    // Dữ liệu tài khoản
    var ttcAccounts by remember { mutableStateOf(TtcAccountsStore.getAccounts(context)) }
    var fbAccounts by remember { mutableStateOf(FacebookAccountsStore.getAccounts(context)) }

    // Quản lý selection (được lưu giữ độc lập giữa 2 tab)
    var selectedTtcUsernames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFbUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Dialog thêm acc TTC
    var showAddTtcDialog by remember { mutableStateOf(false) }

    fun reloadData() {
        ttcAccounts = TtcAccountsStore.getAccounts(context)
        fbAccounts = FacebookAccountsStore.getAccounts(context)
    }

    if (showAddTtcDialog) {
        AddTtcAccountDialog(
            onDismiss = { showAddTtcDialog = false },
            onSave = { username, token ->
                TtcAccountsStore.addAccount(
                    context,
                    TtcAccount(username = username.trim(), token = token.trim())
                )
                reloadData()
                showAddTtcDialog = false
                Toast.makeText(context, "Đã thêm tài khoản TTC: $username", Toast.LENGTH_SHORT).show()
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
            IconButton(onClick = { reloadData() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Tải lại", tint = TextSecondary)
            }
        }

        // ==================== 2 THẺ (TABS) CHỌN Ở TRÊN ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thẻ 1: Acc TTC
            TtcTabButton(
                label = "Acc TTC",
                count = ttcAccounts.size,
                isSelected = selectedTab == 0,
                selectedColor = TtcPink,
                icon = Icons.Filled.SwapHoriz,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = 0 }
            )

            // Thẻ 2: Facebook
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

        // ==================== NỘI DUNG BÊN DƯỚI TÙY THEO THẺ ====================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (selectedTab == 0) {
                // ---------- NỘI DUNG THẺ ACC TTC ----------
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
                    onAddNew = { showAddTtcDialog = true },
                    onDelete = { username ->
                        TtcAccountsStore.removeAccount(context, username)
                        selectedTtcUsernames = selectedTtcUsernames - username
                        reloadData()
                    }
                )
            } else {
                // ---------- NỘI DUNG THẺ ACC FACEBOOK ----------
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Đã chọn: ${selectedTtcUsernames.size} acc TTC  •  ${selectedFbUids.size} acc FB",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

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
        // Thanh công cụ
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
                    "Chọn tất cả (${selectedUsernames.size}/${accounts.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            OutlinedButton(
                onClick = onAddNew,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TtcPink),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = TtcPink, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("+ Thêm acc TTC", fontSize = 12.sp, color = TtcPink, fontWeight = FontWeight.Bold)
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
                    Button(
                        onClick = onAddNew,
                        colors = ButtonDefaults.buttonColors(containerColor = TtcPink),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ Thêm tài khoản TTC ngay")
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
                                            text = " • Token đã lưu",
                                            fontSize = 11.sp,
                                            color = TextSecondary
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
        // Thanh công cụ
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
                    "Chọn tất cả (${selectedUids.size}/${accounts.size})",
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
                Text("+ Quản lý FB", fontSize = 12.sp, color = FbBlue, fontWeight = FontWeight.Bold)
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

                            // Avatar Facebook
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

/** Dialog thêm tài khoản TTC mới */
@Composable
private fun AddTtcAccountDialog(
    onDismiss: () -> Unit,
    onSave: (username: String, token: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Thêm tài khoản TTC", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Tên đăng nhập / Username TTC", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Cookie / Access Token TTC (tùy chọn)", fontSize = 12.sp) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotBlank()) {
                        onSave(username, token)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TtcPink),
                enabled = username.isNotBlank()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
