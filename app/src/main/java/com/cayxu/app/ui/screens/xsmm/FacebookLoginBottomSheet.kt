package com.cayxu.app.ui.screens.xsmm

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.data.local.FacebookAccount
import com.cayxu.app.data.local.FacebookAccountsStore
import com.cayxu.app.facebook.AccountFieldType
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.facebook.FacebookAuthenticator
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class FbFieldKey(val type: AccountFieldType, val label: String, val sample: String) {
    TOKEN(AccountFieldType.TOKEN, "Token", "EAAB..."),
    COOKIE(AccountFieldType.COOKIE, "Cookie", "c_user=...; xs=..."),
    PROXY(AccountFieldType.PROXY, "Proxy", "1.2.3.4:8080")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookLoginBottomSheet(
    onDismiss: () -> Unit,
    onAccountSaved: ((FacebookAccount) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Không chọn mặc định trường nào, người dùng tự do chọn
    var selectedFields by remember { mutableStateOf<List<FbFieldKey>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val allFields = listOf(
        FbFieldKey.TOKEN,
        FbFieldKey.COOKIE,
        FbFieldKey.PROXY
    )

    fun toggleField(field: FbFieldKey) {
        selectedFields = if (field in selectedFields) {
            selectedFields - field
        } else {
            selectedFields + field
        }
    }

    val formatString = if (selectedFields.isEmpty()) "Tự động nhận diện"
    else selectedFields.joinToString(" | ") { it.label }

    val placeholderExample = if (selectedFields.isEmpty()) {
        "Dán Token, Cookie hoặc Token|Cookie|Proxy (mỗi dòng 1 nick)"
    } else {
        selectedFields.joinToString(" | ") { it.sample }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1877F2).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color(0xFF1877F2),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Đăng nhập Facebook",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "Chọn định dạng trường hoặc dán trực tiếp Token / Cookie",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Chọn trường & thứ tự kết hợp (tùy chọn):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // 1 hàng x 3 nút chọn trường: Token, Cookie, Proxy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allFields.forEach { field ->
                    val orderIndex = selectedFields.indexOf(field).let { if (it >= 0) it + 1 else null }
                    val isSelected = orderIndex != null
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF1877F2) else CardWhite)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFF1877F2) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { toggleField(field) }
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (orderIndex != null) "$orderIndex. ${field.label}" else field.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CardWhite else TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Hiển thị định dạng hiện tại
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Định dạng: $formatString",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1877F2)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text("Dữ liệu tài khoản (mỗi dòng 1 nick):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))

            SelectionContainer {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            placeholderExample,
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    },
                    minLines = 5,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1877F2),
                        cursorColor = Color(0xFF1877F2)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            // Nút Hủy & Đăng nhập
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Hủy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        val raw = inputText.trim()
                        if (raw.isBlank()) {
                            Toast.makeText(context, "Vui lòng dán dữ liệu tài khoản", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val accountManager = FacebookAccountManager()
                        val fieldTypes = selectedFields.map { it.type }
                        val accountsToProcess = accountManager.parseAccountsInput(raw, fieldTypes, "|")

                        if (accountsToProcess.isEmpty()) {
                            Toast.makeText(context, "Không có dữ liệu hợp lệ", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            val checkedAccounts = withContext(Dispatchers.IO) {
                                raw.lines().mapNotNull { rawLine ->
                                    val line = rawLine.trim()
                                    if (line.isBlank()) return@mapNotNull null
                                    
                                    async(Dispatchers.IO) {
                                        val authenticator = FacebookAuthenticator()

                                        val parts = if (line.contains("|")) line.split("|").map { it.trim() } else listOf(line)

                                        // 1. Tìm Token (EAA...)
                                        val token = parts.find { it.startsWith("EAA") } ?: if (line.startsWith("EAA")) line else null

                                        // 2. Tìm Cookie (chứa c_user hoặc xs)
                                        val cookie = parts.find { it.contains("c_user=") || it.contains("xs=") }
                                            ?: if (line.contains("c_user=") || line.contains("xs=")) line else null

                                        // 3. Tìm Proxy (chứa ip:port)
                                        val proxy = parts.find { it.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+.*""")) }

                                        // Ưu tiên 1: Đăng nhập bằng Token EAA nếu có
                                        if (!token.isNullOrBlank()) {
                                            try {
                                                val details = accountManager.fetchAccountDetailsWithToken(token, proxy)
                                                if (details.isLive) {
                                                    return@async details.copy(
                                                        bio = token,
                                                        note = cookie ?: details.note,
                                                        phone = proxy.orEmpty(),
                                                        isLive = true
                                                    )
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // Ưu tiên 2: Đăng nhập bằng Cookie nếu có
                                        if (!cookie.isNullOrBlank()) {
                                            try {
                                                val cookieAcc = accountManager.getTokenFromCookie(cookie, proxy)
                                                if (cookieAcc != null && cookieAcc.isLive) {
                                                    return@async cookieAcc.copy(
                                                        bio = token ?: cookieAcc.bio,
                                                        note = cookie,
                                                        phone = proxy.orEmpty(),
                                                        isLive = true
                                                    )
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // Ưu tiên 3: Dạng UID|Pass|2FA
                                        if (parts.size >= 2 && !parts[0].startsWith("EAA") && !parts[0].contains("c_user=")) {
                                            val uid = parts[0]
                                            val pwd = parts[1]
                                            val twofa = parts.getOrNull(2)?.takeIf { !it.contains("=") && !it.startsWith("EAA") }.orEmpty()

                                            try {
                                                val authResult = authenticator.login(
                                                    uid = uid,
                                                    pass = pwd,
                                                    twoFaSecret = twofa,
                                                    proxyStr = proxy,
                                                    rawCookie = cookie
                                                )
                                                if (authResult.isSuccess && authResult.account.isLive) {
                                                    return@async authResult.account.copy(
                                                        password = pwd,
                                                        link = twofa,
                                                        note = cookie ?: authResult.account.note,
                                                        phone = proxy.orEmpty(),
                                                        isLive = true
                                                    )
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // Mặc định: Không xác thực được
                                        val fallbackUid = cookie?.let { c ->
                                            Regex("""c_user=(\d+)""").find(c)?.groupValues?.get(1)
                                        } ?: parts.getOrNull(0)?.takeIf { it.matches(Regex("""\d+""")) } ?: "N/A"

                                        return@async FacebookAccount(
                                            uid = fallbackUid,
                                            name = fallbackUid,
                                            note = cookie.orEmpty(),
                                            bio = token.orEmpty(),
                                            phone = proxy.orEmpty(),
                                            isLive = false
                                        )
                                    }
                                }.awaitAll()
                            }

                            // Lưu vào SharedPreferences và Memory Cache
                            FacebookAccountsStore.addAccounts(context, checkedAccounts)

                            withContext(Dispatchers.Main) {
                                isLoading = false
                                val liveCount = checkedAccounts.count { it.isLive }
                                if (liveCount > 0) {
                                    Toast.makeText(context, "Đăng nhập thành công ($liveCount tài khoản)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Đăng nhập thất bại: Tài khoản DIE hoặc sai pass/2FA", Toast.LENGTH_SHORT).show()
                                }
                                checkedAccounts.firstOrNull()?.let { onAccountSaved?.invoke(it) }
                                onDismiss()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = CardWhite, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Đăng nhập", color = CardWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


