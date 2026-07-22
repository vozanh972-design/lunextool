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

// Hàm trích xuất UID từ cookie
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
    var multiSelectedFields by remember { mutableStateOf(listOf(FieldKey.COOKIE)) }

    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                OutlinedTextField(
                    value = singleUid,
                    onValueChange = { singleUid = it },
                    label = { Text("UID") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singlePassword,
                    onValueChange = { singlePassword = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleTwoFa,
                    onValueChange = { singleTwoFa = it },
                    label = { Text("2FA") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleCookie,
                    onValueChange = { singleCookie = it },
                    label = { Text("Cookie") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (singleCookie.isNotBlank() && singleUid.isNotBlank()) {
                    Text("✅ Đã trích xuất UID: $singleUid", color = SuccessGreen)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleToken,
                    onValueChange = { singleToken = it },
                    label = { Text("Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = singleProxy,
                    onValueChange = { singleProxy = it },
                    label = { Text("Proxy") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("Chọn các trường và thứ tự phân tách bằng dấu \"|\"")
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
                }
                Text("Định dạng hiện tại: " + multiSelectedFields.joinToString(" | ") { key ->
                    ALL_FIELD_OPTIONS.first { it.key == key }.label
                })
                val multiPlaceholder = buildMultiUidPlaceholder(multiSelectedFields)
                OutlinedTextField(
                    value = multiUid,
                    onValueChange = { multiUid = it },
                    placeholder = { Text(multiPlaceholder) },
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                val parsedMultiAccounts = parseMultiUidInput(multiUid, multiSelectedFields)
                Text("Đã nhập ${parsedMultiAccounts.size} tài khoản.")
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Button(
                onClick = {
                    if (isLoading) return@Button
                    if (tabIndex == 0) {
                        // THÊM 1 TÀI KHOẢN
                        val uid = if (singleUid.isNotBlank()) singleUid else extractUidFromCookie(singleCookie) ?: "unknown"
                        val account = FacebookAccount(
                            uid = uid,
                            name = singlePassword,
                            link = singleTwoFa,
                            note = singleCookie,   // LƯU COOKIE VÀO NOTE
                            phone = singleProxy,
                            bio = singleToken,
                            isLive = false
                        )
                        FacebookAccountsStore.addAccount(context, account)

                        if (singleCookie.isNotBlank()) {
                            isLoading = true
                            FacebookLiveChecker.checkCookieWithAvatar(
                                cookieString = singleCookie,
                                onResult = { uid, isLive, avatarUrl ->
                                    val updated = account.copy(
                                        uid = uid ?: account.uid,
                                        avatar = avatarUrl ?: account.avatar,
                                        isLive = isLive
                                    )
                                    FacebookAccountsStore.updateAccount(context, updated)
                                    isLoading = false
                                    Toast.makeText(context, "Đã thêm tài khoản", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                }
                            )
                        } else {
                            Toast.makeText(context, "Đã thêm tài khoản", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        }
                    } else {
                        // THÊM HÀNG LOẠT
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
                                            FacebookLiveChecker.checkCookieWithAvatar(
                                                cookieString = cookie,
                                                onResult = { uid, isLive, avatarUrl ->
                                                    val updated = account.copy(
                                                        isLive = isLive,
                                                        avatar = avatarUrl ?: account.avatar
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
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading
            ) {
                Text("Xác nhận")
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
    return "Mỗi dòng phân tách bằng \"|\", ví dụ:\n$exampleLine"
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

            if (uid.isEmpty() && cookie.isNotBlank()) {
                uid = extractUidFromCookie(cookie) ?: ""
            }
            if (uid.isEmpty()) return@mapNotNull null

            FacebookAccount(
                uid = uid,
                name = password,
                link = twofa,
                note = cookie,   // LƯU COOKIE VÀO NOTE
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
