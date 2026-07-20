package com.cayxu.app.ui.screens.linkaccount.facebook

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.cayxu.app.R
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*

/**
 * Màn hình danh sách tài khoản Facebook - RIÊNG BIỆT, không dùng chung.
 * Hiển thị dữ liệu thật từ FacebookAccountsStore, không có dữ liệu mẫu.
 */
@Composable
fun FacebookLinkAccountScreen(navController: NavController) {
    val context = LocalContext.current
    val iconRes = R.drawable.ic_social_facebook

    var accounts by remember { mutableStateOf(FacebookAccountsStore.getAccounts(context)) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    // Tự động cập nhật khi quay lại màn hình
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accounts = FacebookAccountsStore.getAccounts(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filtered = if (query.isBlank()) {
        accounts
    } else {
        accounts.filter {
            it.uid.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
        }
    }
    val liveCount = accounts.count { it.isLive }
    val dieCount = accounts.size - liveCount

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Tài khoản Facebook",
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
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                        )
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
                    checked = selected.size == filtered.size && filtered.isNotEmpty(),
                    onCheckedChange = { checked ->
                        selected = if (checked) filtered.map { it.uid }.toSet() else emptySet()
                    }
                )
                Text("Chọn tất cả", fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))

                TextButton(onClick = {
                    val targets = if (selected.isEmpty()) filtered.map { it.uid } else selected.toList()
                    FacebookAccountsStore.markLive(context, targets)
                    accounts = FacebookAccountsStore.getAccounts(context)
                    Toast.makeText(context, "Đã cập nhật trạng thái", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kiểm tra Live", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(
                    onClick = {
                        if (selected.isNotEmpty()) {
                            FacebookAccountsStore.removeAccounts(context, selected.toList())
                            accounts = FacebookAccountsStore.getAccounts(context)
                            selected = emptySet()
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
                            FacebookAccountRow(
                                iconRes = iconRes,
                                account = account,
                                checked = account.uid in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + account.uid else selected - account.uid
                                },
                                onRemove = {
                                    FacebookAccountsStore.removeAccount(context, account.uid)
                                    accounts = FacebookAccountsStore.getAccounts(context)
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
                onClick = { navController.navigate(Routes.ADD_ACCOUNT_FACEBOOK) },
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
private fun FacebookAccountRow(
    iconRes: Int,
    account: FacebookAccount,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = account.name.ifBlank { account.uid }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)

        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(TextSecondary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            // Luôn hiển thị UID
            Text("UID: ${account.uid}", color = TextSecondary, fontSize = 11.sp)
            if (account.link.isNotBlank()) {
                Text(account.link, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
            }
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
