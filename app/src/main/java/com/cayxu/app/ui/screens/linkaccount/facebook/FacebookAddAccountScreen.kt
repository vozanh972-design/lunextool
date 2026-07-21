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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

// Hàm trích xuất UID từ cookie (dùng chung)
private fun extractUidFromCookie(cookie: String): String? {
    val pairs = cookie.split(';')
    for (pair in pairs) {
        val trimmed = pair.trim()
        if (trimmed.startsWith("c_user=")) {
            return trimmed.substringAfter("c_user=").trim()
        }
    }
    return null
}

@Composable
fun FacebookAddAccountScreen(navController: NavController) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }

    var singleUid by remember { mutableStateOf("") }
    var singlePassword by remember { mutableStateOf("") }
    var singleTwoFa by remember { mutableStateOf("") }
    var singleCookie by remember { mutableStateOf("") }
    var singleToken by remember { mutableStateOf("") }
    var singleProxy by remember { mutableStateOf("") }

    var multiUid by remember { mutableStateOf("") }
    var multiSelectedFields by remember { mutableStateOf(listOf(FieldKey.COOKIE)) } // Mặc định chọn Cookie

    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Tự động trích xuất UID từ cookie cho nhập đơn
    LaunchedEffect(singleCookie) {
        if (singleCookie.isNotBlank() && singleUid.isBlank()) {
            extractUidFromCookie(singleCookie)?.let { uid ->
                singleUid = uid
            }
        }
    }

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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

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
                    text = "Hàng loạt",
                    selected = tabIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { tabIndex = 1 }
                )
            }

            Spacer(Modifier.height(20.dp))

            if (tabIndex == 0) {
                // ===== NHẬP 1 TÀI KHOẢN ===== (giữ nguyên)
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

                Text("2FA", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
                // ===== HÀNG LOẠT =====
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
                                locked = false,
                                modifier = Modifier.weight(1f).padding(end = if (index != row.lastIndex) 6.dp else 0.dp),
                                onClick = {
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

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (isLoading) return@Button
                    if (tabIndex == 0) {
                        // Xử lý 1 tài khoản (có thể kiểm tra Live nếu có cookie)
                        val finalUid = if (singleUid.isBlank()) "unknown" else singleUid
                        val note = if (singleCookie.isNotBlank()) singleCookie else ""
                        // Nếu có cookie, kiểm tra Live để lấy tên + avatar
                        if (note.isNotBlank()) {
                            isLoading = true
                            FacebookLiveChecker.checkCookieWithAvatarAndName(
                                cookieString = note,
                                onResult = { uid, isLive, avatarUrl, fullName ->
                                    val finalUid2 = uid ?: finalUid
                                    val finalName = fullName ?: singlePassword
                                    val finalBio = avatarUrl ?: ""
                                    FacebookAccountsStore.addAccount(
                                        context,
                                        uid = finalUid2,
                                        name = finalName,
                                        link = singleTwoFa,
                                        note = note,
                                        phone = singleProxy,
                                        bio = finalBio,
                                        isLive = isLive
                                    )
                                    isLoading = false
                                    Toast.makeText(context, "Đã thêm tài khoản Facebook", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            )
                        } else {
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
                            Toast.makeText(context, "Đã thêm tài khoản Facebook", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } else {
                        // HÀNG LOẠT – kiểm tra Live cho từng tài khoản có cookie
                        val entries = parseMultiUidInput(multiUid, multiSelectedFields)
                        if (entries.isEmpty()) {
                            Toast.makeText(context, "Không có dữ liệu hợp lệ", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            val checkedAccounts = entries.map { account ->
                                async {
                                    val cookie = account.note
                                    if (cookie.isNotBlank()) {
                                        suspendCancellableCoroutine { continuation ->
                                            FacebookLiveChecker.checkCookieWithAvatarAndName(
                                                cookieString = cookie,
                                                onResult = { uid, isLive, avatarUrl, fullName ->
                                                    val updated = account.copy(
                                                        isLive = isLive,
                                                        bio = avatarUrl ?: account.bio,
                                                        name = fullName ?: account.name
                                                    )
                                                    continuation.resume(updated)
                                                }
                                            )
                                        }
                                    } else {
                                        account
                                    }
                                }
                            }
                            val finalAccounts = checkedAccounts.awaitAll()
                            FacebookAccountsStore.addAccounts(context, finalAccounts)
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                Toast.makeText(context, "Đã thêm ${finalAccounts.size} tài khoản", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                Text("Xác nhận", color = CardWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ===== Helpers =====
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
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            var uid = ""
            var password = ""
            var twofa = ""
            var cookie = ""
            var token = ""
            var proxy = ""

            fields.forEachIndexed { index, key ->
                val value = parts.getOrNull(index).orEmpty()
                when (key) {
                    FieldKey.UID -> uid = value
                    FieldKey.PASSWORD -> password = value
                    FieldKey.TWOFA -> twofa = value
                    FieldKey.COOKIE -> cookie = value
                    FieldKey.TOKEN -> token = value
                    FieldKey.PROXY -> proxy = value
                }
            }

            // Nếu UID không được cung cấp, thử trích xuất từ cookie
            if (uid.isEmpty() && cookie.isNotBlank()) {
                uid = extractUidFromCookie(cookie) ?: ""
            }

            // Nếu vẫn không có UID, bỏ qua
            if (uid.isEmpty()) return@mapNotNull null

            FacebookAccount(
                uid = uid,
                name = password,
                link = twofa,
                note = cookie,
                phone = proxy,
                bio = token,
                isLive = false
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
