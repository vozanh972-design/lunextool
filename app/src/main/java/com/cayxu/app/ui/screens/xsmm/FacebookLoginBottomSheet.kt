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
    USERNAME(AccountFieldType.USERNAME, "Tài khoản", "100088992211334"),
    PASSWORD(AccountFieldType.PASSWORD, "Mật khẩu", "matkhau123"),
    TWOFA(AccountFieldType.TWO_FACTOR, "2FA", "JBSWY3DPEHPK3PXP"),
    COOKIE(AccountFieldType.COOKIE, "Cookie", "c_user=...; xs=..."),
    PROXY(AccountFieldType.PROXY, "Proxy", "1.2.3.4:8080"),
    TOKEN(AccountFieldType.TOKEN, "Token", "EAAB...")
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
        FbFieldKey.USERNAME,
        FbFieldKey.PASSWORD,
        FbFieldKey.TWOFA,
        FbFieldKey.COOKIE,
        FbFieldKey.PROXY,
        FbFieldKey.TOKEN
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
        "Dán UID|Pass|2FA, Cookie hoặc Token (mỗi dòng 1 nick)"
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
                        "Chọn định dạng trường hoặc dán trực tiếp dữ liệu tài khoản",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Chọn trường & thứ tự kết hợp (tùy chọn):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // 2 hàng x 3 nút chọn trường
            val chunked = allFields.chunked(3)
            chunked.forEachIndexed { rowIndex, rowFields ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowFields.forEach { field ->
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
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (orderIndex != null) "$orderIndex. ${field.label}" else field.label,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) CardWhite else TextPrimary
                            )
                        }
                    }
                }
                if (rowIndex < chunked.lastIndex) {
                    Spacer(Modifier.height(8.dp))
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
                                accountsToProcess.map { acc ->
                                    async(Dispatchers.IO) {
                                        var currentAcc = acc
                                        val proxy = currentAcc.phone.ifBlank { null }

                                        // 1. Nếu có UID + Mật khẩu -> Đăng nhập bằng Native Authenticator (như hàm facebookLogin trong PHP)
                                        if (currentAcc.uid.isNotBlank() && currentAcc.password.isNotBlank()) {
                                            try {
                                                val datr = if (currentAcc.note.contains("datr=")) {
                                                    currentAcc.note.substringAfter("datr=").substringBefore(";").trim()
                                                } else null

                                                val authenticator = FacebookAuthenticator()
                                                val authResult = authenticator.login(
                                                    uid = currentAcc.uid,
                                                    pass = currentAcc.password,
                                                    twoFaSecret = currentAcc.link,
                                                    proxyStr = proxy,
                                                    datrCookie = datr
                                                )
                                                if (authResult.isSuccess) {
                                                    return@async authResult.account
                                                } else {
                                                    // Nếu đăng nhập b-graph thất bại nhưng có kèm Cookie trong chuỗi -> thử lấy token qua Cookie
                                                    if (currentAcc.note.contains("c_user=") || currentAcc.note.contains("xs=")) {
                                                        val directAcc = accountManager.getTokenFromCookie(currentAcc.note, proxy)
                                                        if (directAcc != null && directAcc.isLive) {
                                                            return@async directAcc.copy(
                                                                link = currentAcc.link,
                                                                password = currentAcc.password
                                                            )
                                                        }
                                                    }
                                                    return@async authResult.account.copy(isLive = false)
                                                }
                                            } catch (_: Exception) {
                                                if (currentAcc.note.contains("c_user=") || currentAcc.note.contains("xs=")) {
                                                    try {
                                                        val directAcc = accountManager.getTokenFromCookie(currentAcc.note, proxy)
                                                        if (directAcc != null && directAcc.isLive) {
                                                            return@async directAcc.copy(
                                                                link = currentAcc.link,
                                                                password = currentAcc.password
                                                            )
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                                return@async currentAcc.copy(isLive = false)
                                            }
                                        }

                                        // 2. Nếu là Cookie thuần (không có Pass) -> Lấy Token từ Cookie (như getTokenFromCookie trong PHP)
                                        if (currentAcc.note.isNotBlank() && (currentAcc.note.contains("c_user=") || currentAcc.note.contains("xs="))) {
                                            try {
                                                val directAcc = accountManager.getTokenFromCookie(currentAcc.note, proxy)
                                                if (directAcc != null && directAcc.isLive) {
                                                    return@async directAcc.copy(
                                                        link = currentAcc.link,
                                                        password = currentAcc.password
                                                    )
                                                }
                                                val verifiedAcc = accountManager.verifyCookieAndGetInfo(currentAcc.note, proxy)
                                                if (verifiedAcc.isLive) {
                                                    return@async verifiedAcc.copy(
                                                        link = currentAcc.link,
                                                        bio = if (verifiedAcc.bio.isNotBlank()) verifiedAcc.bio else currentAcc.bio,
                                                        password = currentAcc.password,
                                                        email = if (verifiedAcc.email.isNotBlank()) verifiedAcc.email else currentAcc.email
                                                    )
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // 3. Nếu là Token EAA... thuần
                                        if (currentAcc.bio.isNotBlank() && currentAcc.bio.startsWith("EAA")) {
                                            try {
                                                val detailsAcc = accountManager.fetchAccountDetailsWithToken(currentAcc.bio, proxy)
                                                return@async detailsAcc.copy(
                                                    link = currentAcc.link,
                                                    note = currentAcc.note,
                                                    password = currentAcc.password
                                                )
                                            } catch (_: Exception) {
                                                return@async currentAcc.copy(isLive = false)
                                            }
                                        }

                                        // 4. Fallback cookie còn lại
                                        if (currentAcc.note.isNotBlank()) {
                                            try {
                                                val verifiedAcc = accountManager.verifyCookieAndGetInfo(currentAcc.note, proxy)
                                                return@async verifiedAcc.copy(
                                                    link = currentAcc.link,
                                                    password = currentAcc.password
                                                )
                                            } catch (_: Exception) {
                                                return@async currentAcc.copy(isLive = false)
                                            }
                                        }

                                        currentAcc.copy(isLive = false)
                                    }
                                }.awaitAll()
                            }

                            // Lưu vào SharedPreferences và Memory Cache
                            FacebookAccountsStore.addAccounts(context, checkedAccounts)

                            withContext(Dispatchers.Main) {
                                isLoading = false
                                val liveCount = checkedAccounts.count { it.isLive }
                                if (liveCount > 0) {
                                    Toast.makeText(context, "Đăng nhập thành công", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Đăng nhập thất bại: Cookie/Tài khoản DIE hoặc FB chặn", Toast.LENGTH_LONG).show()
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


