package com.cayxu.app.ui.screens.linkaccount.facebook

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
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
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.InfoBlueBg
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Màn hình thêm tài khoản Facebook - RIÊNG BIỆT, không dùng chung với AddAccountScreen
 * (TikTok/Instagram/...). Mọi thay đổi cho Facebook chỉ sửa ở đây.
 *
 * Chỉ có các trường CÔNG KHAI: UID, Tên, Link trang cá nhân, Ghi chú - KHÔNG có ô mật khẩu/
 * 2FA/cookie/token/proxy, không thu thập thông tin đăng nhập hay hạ tầng ẩn danh của bất kỳ ai.
 *
 * Có 2 chế độ: nhập 1 tài khoản (UID + tên + link + ghi chú) hoặc nhập nhiều UID cùng lúc.
 */
@Composable
fun FacebookAddAccountScreen(navController: NavController) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }

    var singleUid by remember { mutableStateOf("") }
    var singleName by remember { mutableStateOf("") }
    var singleLink by remember { mutableStateOf("") }
    var singleNote by remember { mutableStateOf("") }

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
                "Thêm tài khoản Facebook",
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
                        "Chỉ cần nhập UID (mã định danh công khai) của tài khoản Facebook, không cần mật khẩu.",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bộ chọn 2 chế độ, dạng viên thuốc nhỏ gọn thay cho TabRow to chiếm nhiều chỗ.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .padding(4.dp)
            ) {
                SegmentButton(
                    text = "Nhập UID",
                    selected = tabIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 0 }
                )
                Spacer(Modifier.width(4.dp))
                SegmentButton(
                    text = "Nhập nhiều UID",
                    selected = tabIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 1 }
                )
            }

            Spacer(Modifier.height(20.dp))

            if (tabIndex == 0) {
                Text("UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleUid,
                    onValueChange = { singleUid = it },
                    placeholder = { Text("Nhập UID tài khoản Facebook") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "UID là mã định danh công khai của tài khoản, không phải mật khẩu.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(16.dp))
                Text("Tên", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleName,
                    onValueChange = { singleName = it },
                    placeholder = { Text("Nhập tên hiển thị (không bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleLink,
                    onValueChange = { singleLink = it },
                    placeholder = { Text("Nhập link trang cá nhân (không bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Link là địa chỉ công khai của trang cá nhân, ví dụ facebook.com/ten-trang.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(16.dp))
                Text("Ghi chú", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleNote,
                    onValueChange = { singleNote = it },
                    placeholder = { Text("Ghi chú thêm về tài khoản (không bắt buộc)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(120.dp)
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
                            FacebookAccountsStore.addAccount(context, singleUid, singleName, singleLink, singleNote)
                            Toast.makeText(context, "Đã thêm tài khoản Facebook", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } else {
                        val uids = multiUid.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (uids.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập ít nhất một UID", Toast.LENGTH_SHORT).show()
                        } else {
                            FacebookAccountsStore.addAccounts(context, uids.map { FacebookAccount(uid = it) })
                            Toast.makeText(context, "Đã thêm ${uids.size} tài khoản Facebook", Toast.LENGTH_SHORT).show()
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

@Composable
private fun SegmentButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else CardWhite)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) CardWhite else TextSecondary
        )
    }
}
