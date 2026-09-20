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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
 * Giao diện Tương Tác Chéo:
 * Chia làm 2 ô song song:
 *   - Ô bên trái: Danh sách tài khoản TTC (Tương tác chéo)
 *   - Ô bên phải: Danh sách tài khoản Facebook
 * Bỏ hoàn toàn các code mẫu/placeholder cũ.
 */
@Composable
fun TuongTacCheoScreen(navController: NavController) {
    val context = LocalContext.current

    // Danh sách tài khoản
    var ttcAccounts by remember { mutableStateOf(TtcAccountsStore.getAccounts(context)) }
    var fbAccounts by remember { mutableStateOf(FacebookAccountsStore.getAccounts(context)) }

    // Quản lý selection
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
        // ---- Top Bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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

        // ---- Summary Badge Bar ----
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = CardWhite,
            shadowElevation = 0.5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Đã chọn: ${selectedTtcUsernames.size} acc TTC  |  ${selectedFbUids.size} acc FB",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "Tổng: ${ttcAccounts.size} TTC / ${fbAccounts.size} FB",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 2 Ô Song Song (Bên trái: TTC | Bên phải: Facebook) ----
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ==================== Ô BÊN TRÁI: ACC TTC ====================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF3E8FF)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Ô TTC
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFDF2F8))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(TtcPink.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = TtcPink,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Acc TTC (${ttcAccounts.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TtcPink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { showAddTtcDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Thêm TTC", tint = TtcPink, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Tùy chọn Chọn tất cả TTC
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isAllSelected = ttcAccounts.isNotEmpty() && selectedTtcUsernames.size == ttcAccounts.size
                        Checkbox(
                            checked = isAllSelected,
                            onCheckedChange = { check ->
                                selectedTtcUsernames = if (check) ttcAccounts.map { it.username }.toSet() else emptySet()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = TtcPink),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Chọn tất cả", fontSize = 11.sp, color = TextSecondary)
                    }

                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 0.8.dp)

                    // Danh sách Acc TTC
                    if (ttcAccounts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Chưa có acc TTC", fontSize = 12.sp, color = TextSecondary)
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { showAddTtcDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("+ Thêm acc", fontSize = 11.sp, color = TtcPink)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(ttcAccounts, key = { it.username }) { acc ->
                                val isSelected = acc.username in selectedTtcUsernames
                                TtcAccountItemRow(
                                    account = acc,
                                    isSelected = isSelected,
                                    onToggle = {
                                        selectedTtcUsernames = if (isSelected) {
                                            selectedTtcUsernames - acc.username
                                        } else {
                                            selectedTtcUsernames + acc.username
                                        }
                                    },
                                    onDelete = {
                                        TtcAccountsStore.removeAccount(context, acc.username)
                                        selectedTtcUsernames = selectedTtcUsernames - acc.username
                                        reloadData()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ==================== Ô BÊN PHẢI: ACC FACEBOOK ====================
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Ô Facebook
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEFF6FF))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(FbBlue.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Facebook,
                                contentDescription = null,
                                tint = FbBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Acc FB (${fbAccounts.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = FbBlue,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = {
                                // Điều hướng hoặc mở thêm tài khoản FB
                                navController.navigate(com.cayxu.app.ui.navigation.Routes.ACCOUNT)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Thêm FB", tint = FbBlue, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Tùy chọn Chọn tất cả FB
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isAllSelected = fbAccounts.isNotEmpty() && selectedFbUids.size == fbAccounts.size
                        Checkbox(
                            checked = isAllSelected,
                            onCheckedChange = { check ->
                                selectedFbUids = if (check) fbAccounts.map { it.uid }.toSet() else emptySet()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = FbBlue),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Chọn tất cả", fontSize = 11.sp, color = TextSecondary)
                    }

                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 0.8.dp)

                    // Danh sách Acc Facebook
                    if (fbAccounts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Chưa có nick FB", fontSize = 12.sp, color = TextSecondary)
                                Spacer(Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { navController.navigate(com.cayxu.app.ui.navigation.Routes.ACCOUNT) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("+ Thêm FB", fontSize = 11.sp, color = FbBlue)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(fbAccounts, key = { it.uid }) { acc ->
                                val isSelected = acc.uid in selectedFbUids
                                FbAccountItemRow(
                                    account = acc,
                                    isSelected = isSelected,
                                    onToggle = {
                                        selectedFbUids = if (isSelected) {
                                            selectedFbUids - acc.uid
                                        } else {
                                            selectedFbUids + acc.uid
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- Nút Chạy ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = {
                    Toast.makeText(
                        context,
                        "Đã chọn: ${selectedTtcUsernames.size} acc TTC và ${selectedFbUids.size} acc FB",
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

/** 1 dòng hiển thị tài khoản TTC trong ô bên trái */
@Composable
private fun TtcAccountItemRow(
    account: TtcAccount,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) TtcPink.copy(alpha = 0.08f) else Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isSelected) TtcPink.copy(alpha = 0.5f) else Color(0xFFE5E7EB)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = TtcPink),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.username.ifBlank { "TTC Acc" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (account.coins > 0) "${account.coins} xu" else "Live",
                    fontSize = 10.sp,
                    color = if (account.isLive) Color(0xFF16A34A) else Color(0xFFDC2626)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Xóa",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** 1 dòng hiển thị tài khoản Facebook trong ô bên phải */
@Composable
private fun FbAccountItemRow(
    account: FacebookAccount,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) FbBlue.copy(alpha = 0.08f) else Color(0xFFF9FAFB),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isSelected) FbBlue.copy(alpha = 0.5f) else Color(0xFFE5E7EB)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = FbBlue),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name.ifBlank { account.uid },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val extraText = when {
                    account.pages.isNotEmpty() -> "${account.pages.size} Page"
                    account.uid.isNotBlank() -> account.uid.take(10) + "..."
                    else -> "Sẵn sàng"
                }
                Text(
                    text = extraText,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Tên đăng nhập TTC", fontSize = 12.sp) },
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
