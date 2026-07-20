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
import com.cayxu.app.utils.FacebookLiveChecker

@Composable
fun FacebookAddAccountScreen(navController: NavController) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }

    // Single account
    var singleUid by remember { mutableStateOf("") }
    var singlePassword by remember { mutableStateOf("") }
    var singleTwoFa by remember { mutableStateOf("") }
    var singleCookie by remember { mutableStateOf("") }
    var singleToken by remember { mutableStateOf("") }
    var singleProxy by remember { mutableStateOf("") }

    // Multi account
    var multiUid by remember { mutableStateOf("") }
    var multiSelectedFields by remember { mutableStateOf(listOf(FieldKey.UID)) }

    var isLoading by remember { mutableStateOf(false) }

    // Tự động trích xuất UID từ cookie
    fun extractUidFromCookie(cookie: String): String? {
        val pairs = cookie.split(';')
        for (pair in pairs) {
            val trimmed = pair.trim()
            if (trimmed.startsWith("c_user=")) {
                return trimmed.substringAfter("c_user=").trim()
            }
        }
        return null
    }

    LaunchedEffect(singleCookie) {
        if (singleCookie.isNotBlank() && singleUid.isBlank()) {
            extractUidFromCookie(singleCookie)?.let { uid ->
                singleUid = uid
            }
        }
    }

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

            // Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Badge, contentDescription = null, tint = Primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "UID là mã định danh công khai của tài khoản, không phải mật khẩu.",
                        fontSize = 12.5.sp,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Segment
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
                // UID
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

                // Password
                Text("Password", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singlePassword,
                    onValueChange = { singlePassword = it },
                    placeholder = { Text("Nhập password (bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // 2FA (label "Link")
                Text("Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleTwoFa,
                    onValueChange = { singleTwoFa = it },
                    placeholder = { Text("Nhập 2FA (bắt buộc)") },
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
                    "Mã 2FA (Xác thực 2 yếu tố).",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(16.dp))

                // Cookie
                Text("Cookie", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleCookie,
                    onValueChange = { singleCookie = it },
                    placeholder = { Text("Cookie (không bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (singleCookie.isNotBlank() && singleUid.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "✅ Đã trích xuất UID: $singleUid",
                        fontSize = 11.sp,
                        color = SuccessGreen
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Token
                Text("Token", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleToken,
                    onValueChange = { singleToken = it },
                    placeholder = { Text("Token (không bắt buộc)") },
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
                    "Token (không bắt buộc, dùng nếu không có UID|PASS|2FA)",
                    fontSize = 11.sp,
                    color = TextSecondary
                )

                Spacer(Modifier.height(16.dp))

                // Proxy
                Text("Proxy", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleProxy,
                    onValueChange = { singleProxy = it },
                    placeholder = { Text("Nhập proxy (không bắt buộc)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Chế độ nhập nhiều (giữ nguyên cũ)
                Text("Danh sách UID", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))

                Text(
                    "Chọn các trường và thứ tự phân tách bằng dấu \"|\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))

                val fieldRows = ALL_FIELD_OPTIONS.chunked(3)
                fieldRows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { index, option ->
                            val order = multiSelectedFields.indexOf(option.key).let { if (it >= 0) it + 1 else null }
                            FieldToggleChip(
                                label = option.label,
                                order = order,
                                locked = option.key == FieldKey.UID,
                                modifier = Modifier.weight(1f).padding(end = if (index != row.lastIndex) 6.dp else 0.dp),
                                onClick = {
                                    if (option.key == FieldKey.UID) return@FieldToggleChip
                                    multiSelectedFields = if (option.key in multiSelectedFields) {
                                        multiSelectedFields - option.key
                                    } else {
                                        multiSelectedFields + option.key
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
                        ALL_FIELD_OPTIONS.first { it.key == key }.label
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Primary
                )

                Spacer(Modifier.height(12.dp))
                val multiPlaceholder = buildMultiUidPlaceholder(multiSelectedFields)
                OutlinedTextField(
                    value = multiUid,
                    onValueChange = { multiUid = it },
                    placeholder = { Text(multiPlaceholder) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite
                    ),
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                Spacer(Modifier.height(8.dp))
                val parsedMultiAccounts = parseMultiUidInput(multiUid, multiSelectedFields)
                Text(
                    "Đã nhập ${parsedMultiAccounts.size} tài khoản. Mỗi dòng phân tách bằng dấu \"|\" theo đúng thứ tự trường đã chọn ở trên.",
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
                        if (singleUid.isBlank() && singleCookie.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập UID hoặc Cookie", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Nếu có cookie, kiểm tra live trước
                        if (singleCookie.isNotBlank()) {
                            isLoading = true
                            Toast.makeText(context, "Đang kiểm tra cookie...", Toast.LENGTH_SHORT).show()
                            FacebookLiveChecker.checkCookie(context, singleCookie) { uid, isLive ->
                                isLoading = false
                                if (uid != null && isLive) {
                                    // Đăng nhập thành công
                                    val finalUid = if (singleUid.isBlank()) uid else singleUid
                                    val note = "Cookie: $singleCookie"
                                    FacebookAccountsStore.addAccount(
                                        context,
                                        uid = finalUid,
                                        name = singlePassword,
                                        link = singleTwoFa,
                                        note = note,
                                        phone = singleProxy,
                                        bio = singleToken,
                                        isLive = true
                                    )
                                    Toast.makeText(context, "✅ Đã thêm tài khoản (Live)", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                } else {
                                    // Cookie không hợp lệ, vẫn lưu nhưng đánh dấu Die
                                    val finalUid = if (singleUid.isBlank()) "unknown" else singleUid
                                    val note = "Cookie: $singleCookie"
                                    FacebookAccountsStore.addAccount(
                                        context,
                                        uid = finalUid,
                                        name = singlePassword,
                                        link = singleTwoFa,
                                        note = note,
                                        phone = singleProxy,
                                        bio = singleToken,
                                        isLive = false
                                    )
                                    Toast.makeText(context, "⚠️ Cookie không hợp lệ, đã lưu với trạng thái Die", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            }
                        } else {
                            // Không có cookie, lưu bình thường (isLive = true mặc định)
                            FacebookAccountsStore.addAccount(
                                context,
                                uid = singleUid,
                                name = singlePassword,
                                link = singleTwoFa,
                                note = "",
                                phone = singleProxy,
                                bio = singleToken
                            )
                            Toast.makeText(context, "Đã thêm tài khoản Facebook", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } else {
                        // Chế độ nhiều UID (không kiểm tra cookie)
                        val entries = parseMultiUidInput(multiUid, multiSelectedFields)
                        if (entries.isEmpty()) {
                            Toast.makeText(context, "Vui lòng nhập ít nhất một UID", Toast.LENGTH_SHORT).show()
                        } else {
                            FacebookAccountsStore.addAccounts(context, entries)
                            Toast.makeText(context, "Đã thêm ${entries.size} tài khoản Facebook", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = CardWhite, modifier = Modifier.size(24.dp))
                } else {
                    Text("Xác nhận", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ===== Các helper không thay đổi =====

private enum class FieldKey { UID, PASSWORD, TWOFA, COOKIE, TOKEN, PROXY }

private data class FieldOption(val key: FieldKey, val label: String)

private val ALL_FIELD_OPTIONS = listOf(
    FieldOption(FieldKey.UID, "UID"),
    FieldOption(FieldKey.PASSWORD, "Password"),
    FieldOption(FieldKey.TWOFA, "2FA"),
    FieldOption(FieldKey.COOKIE, "Cookie"),
    FieldOption(FieldKey.TOKEN, "Token"),
    FieldOption(FieldKey.PROXY, "Proxy")
)

private val SAMPLE_VALUES = mapOf(
    FieldKey.UID to "100000001234567",
    FieldKey.PASSWORD to "matkhau123",
    FieldKey.TWOFA to "123456",
    FieldKey.COOKIE to "c_user=123456; xs=abc",
    FieldKey.TOKEN to "token_value",
    FieldKey.PROXY to "proxy:port"
)

private fun buildMultiUidPlaceholder(fields: List<FieldKey>): String {
    val exampleLine = fields.joinToString("|") { SAMPLE_VALUES[it].orEmpty() }
    return "Mỗi dòng phân tách bằng \"|\" theo đúng thứ tự đã chọn, ví dụ:\n$exampleLine"
}

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
            var twofa = ""
            var cookie = ""
            var token = ""
            var proxy = ""
            fields.forEachIndexed { index, key ->
                val value = parts.getOrNull(index).orEmpty()
                when (key) {
                    FieldKey.PASSWORD -> password = value
                    FieldKey.TWOFA -> twofa = value
                    FieldKey.COOKIE -> cookie = value
                    FieldKey.TOKEN -> token = value
                    FieldKey.PROXY -> proxy = value
                    FieldKey.UID -> {}
                }
            }
            FacebookAccount(
                uid = uid,
                name = password,
                link = twofa,
                note = cookie,
                phone = proxy,
                bio = token
            )
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
