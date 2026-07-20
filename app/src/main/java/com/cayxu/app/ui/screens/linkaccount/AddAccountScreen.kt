package com.cayxu.app.ui.screens.linkaccount

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.InfoBlueBg
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Chỉ nhập UID (định danh công khai) - KHÔNG có ô mật khẩu/cookie/token, không thu thập
 * thông tin đăng nhập của bất kỳ ai. Có 2 tab: nhập 1 UID hoặc nhập nhiều UID cùng lúc
 * (mỗi UID một dòng), cả hai đều chỉ dùng chung 1 trường dữ liệu là UID.
 */
@Composable
fun AddAccountScreen(navController: NavController, platform: String, iconRes: Int) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var singleUid by remember { mutableStateOf("") }
    var multiUid by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Thêm tài khoản $platform",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))

            // Banner thông tin
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = Primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Chỉ cần nhập UID (mã định danh công khai) của tài khoản $platform, không cần mật khẩu.",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 2 tab: nhập 1 UID / nhập nhiều UID
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = CardWhite,
                contentColor = Primary
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Nhập UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("Nhập nhiều UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(Modifier.height(20.dp))

            if (tabIndex == 0) {
                Text("UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleUid,
                    onValueChange = { singleUid = it },
                    placeholder = { Text("Nhập UID tài khoản $platform") },
                    leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "UID là mã định danh công khai của tài khoản, không phải mật khẩu.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            } else {
                Text("Danh sách UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = multiUid,
                    onValueChange = { multiUid = it },
                    placeholder = { Text("Mỗi UID một dòng, ví dụ:\n100000001234567\n100000001234568") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                Spacer(Modifier.height(8.dp))
                val previewCount = multiUid.lines().map { it.trim() }.count { it.isNotEmpty() }
                Text(
                    "Đã nhập $previewCount UID. Mỗi dòng là một UID công khai, không phải mật khẩu.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (tabIndex == 0) {
                        if (singleUid.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập UID", Toast.LENGTH_SHORT).show()
                        } else {
                            LinkedAccountsStore.addAccount(context, platform, singleUid)
                            Toast.makeText(context, "Đã thêm tài khoản $platform", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } else {
                        val uids = multiUid.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (uids.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập ít nhất một UID", Toast.LENGTH_SHORT).show()
                        } else {
                            LinkedAccountsStore.addAccounts(context, platform, uids)
                            Toast.makeText(context, "Đã thêm ${uids.size} tài khoản $platform", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Xác nhận", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
