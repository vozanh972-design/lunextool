package com.cayxu.app.ui.screens.linkaccount

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cayxu.app.data.local.LinkedAccount
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.DangerRed
import com.cayxu.app.ui.theme.InfoBlueBg
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.SuccessGreen
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Danh sách UID đã liên kết cho 1 nền tảng (Facebook/TikTok/...).
 * Chỉ lưu/hiển thị UID (định danh công khai) do người dùng tự nhập - KHÔNG có mật khẩu,
 * cookie hay token. Nút "Kiểm tra Live" chỉ đổi cờ hiển thị cho vui mắt (mock UI), không
 * gọi mạng hay xác thực gì cả.
 */
@Composable
fun LinkAccountScreen(navController: NavController, platform: String, iconRes: Int) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(LinkedAccountsStore.getAccounts(context, platform)) }
    var query by remember { mutableStateOf("") }
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    // TODO: seed demo chỉ để giao diện danh sách có sẵn vài mục khi chưa có dữ liệu thật.
    LaunchedEffect(platform) {
        if (LinkedAccountsStore.getAccounts(context, platform).isEmpty()) {
            LinkedAccountsStore.addAccounts(context, platform, listOf("uid_mau_001 (mẫu)", "uid_mau_002 (mẫu)"))
            accounts = LinkedAccountsStore.getAccounts(context, platform)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accounts = LinkedAccountsStore.getAccounts(context, platform)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filtered = if (query.isBlank()) accounts else accounts.filter { it.uid.contains(query, ignoreCase = true) }
    val liveCount = accounts.count { it.isLive }
    val dieCount = accounts.size - liveCount

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Tài khoản $platform",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Card tổng quan
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(InfoBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconRes != 0) {
                            Image(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = Primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Tổng tài khoản", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Row {
                            Text("Live: $liveCount", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                            Text("Die: $dieCount", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Ô tìm kiếm + nút lọc
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Tìm kiếm tài khoản...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.weight(1f).height(52.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Lọc", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Hàng chọn tất cả / kiểm tra Live / xóa
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectMode && selected.size == filtered.size && filtered.isNotEmpty(),
                    onCheckedChange = { checked ->
                        selectMode = true
                        selected = if (checked) filtered.map { it.uid }.toSet() else emptySet()
                    }
                )
                Text("Chọn tất cả", fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))

                TextButton(onClick = {
                    val targets = if (selected.isEmpty()) filtered.map { it.uid } else selected.toList()
                    LinkedAccountsStore.markLive(context, platform, targets)
                    accounts = LinkedAccountsStore.getAccounts(context, platform)
                    Toast.makeText(context, "Đã cập nhật trạng thái", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kiểm tra Live", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            LinkedAccountsStore.removeAccounts(context, platform, selected.toList())
                            accounts = LinkedAccountsStore.getAccounts(context, platform)
                            selected = emptySet()
                            selectMode = false
                        }
                    },
                    enabled = selected.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Xóa", color = DangerRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (accounts.isEmpty()) "Chưa có tài khoản nào được thêm" else "Không tìm thấy tài khoản phù hợp",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        filtered.forEachIndexed { index, account ->
                            AccountRow(
                                iconRes = iconRes,
                                account = account,
                                checked = account.uid in selected,
                                onCheckedChange = { checked ->
                                    selectMode = true
                                    selected = if (checked) selected + account.uid else selected - account.uid
                                },
                                onRemove = {
                                    LinkedAccountsStore.removeAccount(context, platform, account.uid)
                                    accounts = LinkedAccountsStore.getAccounts(context, platform)
                                    selected = selected - account.uid
                                }
                            )
                            if (index != filtered.lastIndex) {
                                HorizontalDivider(color = AppBackground, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = { navController.navigate(Routes.addAccount(platform, iconRes)) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("+  Thêm tài khoản mới", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun AccountRow(
    iconRes: Int,
    account: LinkedAccount,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)

        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(TextSecondary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != 0) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.uid, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (account.isLive) SuccessGreen.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                if (account.isLive) "Live" else "Die",
                color = if (account.isLive) SuccessGreen else DangerRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Tuỳ chọn", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Xoá") },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    }
                )
            }
        }
    }
}
