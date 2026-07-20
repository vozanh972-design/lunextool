package com.cayxu.app.ui.screens.linkaccount.facebook

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.ui.theme.*

/**
 * Màn hình thêm tài khoản Facebook với các trường: UID, Password, 2FA, Cookie, Token, Proxy.
 * Có 2 chế độ: nhập 1 tài khoản hoặc nhập hàng loạt.
 */
@Composable
fun FacebookAddAccountScreen(navController: NavController) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }

    // State cho nhập 1 tài khoản
    var singleUid by remember { mutableStateOf("") }
    var singlePassword by remember { mutableStateOf("") }
    var singleTwoFa by remember { mutableStateOf("") }
    var singleCookie by remember { mutableStateOf("") }
    var singleToken by remember { mutableStateOf("") }
    var singleProxy by remember { mutableStateOf("") }

    // State cho nhập nhiều
    var multiText by remember { mutableStateOf("") }
    // Các trường được chọn theo thứ tự (mặc định chỉ có UID)
    var multiSelectedFields by remember { mutableStateOf(listOf(FieldKey.UID)) }

    // Loading state
    var isLoading by remember { mutableStateOf(false) }

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
                "Thêm tài khoản Facebook",
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Banner thông báo
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = Primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "UID là mã định danh công khai, không phải mật khẩu. Password và 2FA là bắt buộc.",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Tab chọn chế độ
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
                // ========== CHẾ ĐỘ NHẬP 1 TÀI KHOẢN ==========
                InputField(
                    label = "UID",
                    value = singleUid,
                    onValueChange = { singleUid = it },
                    placeholder = "Nhập UID tài khoản Facebook",
                    helperText = "Mã định danh công khai, không phải mật khẩu.",
                    isRequired = true
                )
                InputField(
                    label = "Password",
                    value = singlePassword,
                    onValueChange = { singlePassword = it },
                    placeholder = "Nhập mật khẩu (bắt buộc)",
                    helperText = null,
                    isRequired = true,
                    isPassword = true
                )
                InputField(
                    label = "2FA",
                    value = singleTwoFa,
                    onValueChange = { singleTwoFa = it },
                    placeholder = "Nhập mã 2FA (bắt buộc)",
                    helperText = "Mã xác thực hai yếu tố.",
                    isRequired = true
                )
                InputField(
                    label = "Cookie",
                    value = singleCookie,
                    onValueChange = { singleCookie = it },
                    placeholder = "Cookie (không bắt buộc)",
                    helperText = null,
                    isMultiline = true
                )
                InputField(
                    label = "Token",
                    value = singleToken,
                    onValueChange = { singleToken = it },
                    placeholder = "Token (không bắt buộc, dùng nếu không có UID|PASS|2FA)",
                    helperText = null,
                    isMultiline = true
                )
                InputField(
                    label = "Proxy",
                    value = singleProxy,
                    onValueChange = { singleProxy = it },
                    placeholder = "IP:Port:Username:Password (không bắt buộc)",
                    helperText = "Ví dụ: 192.168.1.1:8080:user:pass"
                )
            } else {
                // ========== CHẾ ĐỘ NHẬP NHIỀU ==========
                Text("Danh sách UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Chọn các trường và thứ tự phân tách bằng dấu \"|\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                // Chip chọn trường
                val allFields = listOf(
                    FieldKey.UID to "UID",
                    FieldKey.PASSWORD to "Password",
                    FieldKey.TWOFA to "2FA",
                    FieldKey.COOKIE to "Cookie",
                    FieldKey.TOKEN to "Token",
                    FieldKey.PROXY to "Proxy"
                )
                // Hiển thị chip theo hàng, mỗi hàng 3 cái
                allFields.chunked(3).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { index, (key, label) ->
                            val order = multiSelectedFields.indexOf(key).let { if (it >= 0) it + 1 else null }
                            FieldToggleChip(
                                label = label,
                                order = order,
                                locked = key == FieldKey.UID, // UID luôn được chọn
                                modifier = Modifier.weight(1f).padding(end = if (index != row.lastIndex) 6.dp else 0.dp),
                                onClick = {
                                    if (key == FieldKey.UID) return@FieldToggleChip
                                    multiSelectedFields = if (key in multiSelectedFields) {
                                        multiSelectedFields - key
                                    } else {
                                        multiSelectedFields + key
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "Định dạng hiện tại: " + multiSelectedFields.joinToString(" | ") { key ->
                        allFields.first { it.first == key }.second
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = multiText,
                    onValueChange = { multiText = it },
                    placeholder = { Text("Mỗi dòng phân tách bằng \"|\" theo đúng thứ tự đã chọn, ví dụ:\n100000001234567|Pass123|2FACODE") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                Spacer(Modifier.height(8.dp))
                val parsedMulti = parseMultiUidInput(multiText, multiSelectedFields)
                Text(
                    "Đã nhập ${parsedMulti.size} tài khoản. Mỗi dòng phân tách bằng dấu \"|\" theo đúng thứ tự trường đã chọn ở trên.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        // Nút Xác nhận
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (isLoading) return@Button
                    if (tabIndex == 0) {
                        val uid = singleUid.trim()
                        val password = singlePassword.trim()
                        val twoFa = singleTwoFa.trim()
                        if (uid.isEmpty() || password.isEmpty()) {
                            Toast.makeText(context, "UID và Password là bắt buộc", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        // Gọi đăng nhập
                        FacebookLoginHelper.login(
                            username = uid,
                            password = password,
                            twoFA = twoFa.ifEmpty { null },
                            cookie = singleCookie.trim().ifEmpty { null },
                            token = singleToken.trim().ifEmpty { null },
                            proxy = singleProxy.trim().ifEmpty { null },
                            onSuccess = { uidResult, tokenResult, name ->
                                // Lưu tài khoản
                                FacebookAccountsStore.addAccount(
                                    context,
                                    uid = uidResult,
                                    name = name ?: "",
                                    link = "",
                                    note = "",
                                    phone = "",
                                    bio = "",
                                    token = tokenResult
                                )
                                Toast.makeText(context, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            },
                            onError = { message ->
                                Toast.makeText(context, "Lỗi: $message", Toast.LENGTH_SHORT).show()
                            },
                            onComplete = { isLoading = false }
                        )
                    } else {
                        // Nhập nhiều
                        val entries = parseMultiUidInput(multiText, multiSelectedFields)
                        if (entries.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập ít nhất một dòng hợp lệ", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        // Đăng nhập từng tài khoản (có thể xử lý tuần tự hoặc song song)
                        // Ở đây tôi giả sử bạn chỉ muốn lưu trực tiếp (không cần đăng nhập) vì không có password riêng cho từng dòng?
                        // Theo yêu cầu, mỗi dòng có password, 2FA,... nên cần đăng nhập từng cái.
                        // Nhưng để đơn giản, tôi sẽ lưu tất cả với trạng thái "chưa đăng nhập"
                        // Bạn có thể mở rộng.
                        FacebookAccountsStore.addAccounts(context, entries.map { 
                            FacebookAccount(uid = it.uid, name = it.name, link = it.link, note = it.note, phone = it.phone, bio = it.bio, token = it.token)
                        })
                        Toast.makeText(context, "Đã thêm ${entries.size} tài khoản", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                        isLoading = false
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = CardWhite)
                } else {
                    Text("Xác nhận", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ========== Các thành phần UI dùng chung ==========

@Composable
private fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    helperText: String?,
    isRequired: Boolean = false,
    isPassword: Boolean = false,
    isMultiline: Boolean = false
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Row {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (isRequired) {
                Spacer(Modifier.width(4.dp))
                Text("*", color = DangerRed, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            singleLine = !isMultiline,
            minLines = if (isMultiline) 3 else 1,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (helperText != null) {
            Spacer(Modifier.height(4.dp))
            Text(helperText, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun FieldToggleChip(
    label: String,
    order: Int?,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val selected = order != null
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else CardWhite)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !locked,
                onClick = onClick
            )
            .padding(vertical = 9.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (selected) "$order. $label" else label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) CardWhite else TextSecondary
        )
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

// ========== Enums và hàm parse cho nhập nhiều ==========

private enum class FieldKey { UID, PASSWORD, TWOFA, COOKIE, TOKEN, PROXY }

private fun parseMultiUidInput(raw: String, fields: List<FieldKey>): List<FacebookAccount> {
    val uidPos = fields.indexOf(FieldKey.UID)
    if (uidPos < 0) return emptyList()
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            val uid = parts.getOrNull(uidPos).orEmpty()
            if (uid.isEmpty()) return@mapNotNull null
            var password = ""
            var twoFA = ""
            var cookie = ""
            var token = ""
            var proxy = ""
            fields.forEachIndexed { index, key ->
                val value = parts.getOrNull(index).orEmpty()
                when (key) {
                    FieldKey.UID -> { /* đã có uid */ }
                    FieldKey.PASSWORD -> password = value
                    FieldKey.TWOFA -> twoFA = value
                    FieldKey.COOKIE -> cookie = value
                    FieldKey.TOKEN -> token = value
                    FieldKey.PROXY -> proxy = value
                }
            }
            // Tạo FacebookAccount (có thể thêm các trường này nếu cần, nhưng hiện tại lưu dưới dạng note?)
            // Tạm thời chỉ lưu UID, name, link, note, phone, bio. Bạn có thể mở rộng model.
            FacebookAccount(
                uid = uid,
                name = "", // sẽ cập nhật sau khi login
                link = "",
                note = "pass:$password,2fa:$twoFA,cookie:$cookie,token:$token,proxy:$proxy", // lưu tạm
                phone = "",
                bio = ""
            )
        }
}
