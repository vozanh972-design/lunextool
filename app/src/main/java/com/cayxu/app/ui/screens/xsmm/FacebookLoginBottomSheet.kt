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
import com.cayxu.app.facebook.FacebookAccountManager
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import com.example.facebooktoken.FacebookToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Định dạng đăng nhập Facebook trong popup dấu "+" tab Facebook
 */
enum class FbLoginFormat(
    val label: String,
    val description: String,
    val sample: String
) {
    TOKEN(
        label = "Token",
        description = "Định dạng trực tiếp: Dán Access Token Facebook (EAA... / EAAB...)",
        sample = "EAAAAU...\nEAAB..."
    ),
    UID_PASS_2FA_COOKIE(
        label = "UID|PASS|2FA|COOKIE",
        description = "Định dạng chuẩn: UID | Mật khẩu | Mã 2FA (hoặc secret) | Cookie (hoặc DATR)",
        sample = "6159xxxx|matkhau|2FA_SECRET|c_user=... hoặc datr=..."
    ),
    UID_PASS_COOKIE(
        label = "UID|PASS|COOKIE",
        description = "Định dạng cơ bản: UID | Mật khẩu | Cookie",
        sample = "6159xxxx|matkhau|c_user=...; xs=..."
    )
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

    var selectedFormat by remember { mutableStateOf<FbLoginFormat?>(null) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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
                        "Chọn định dạng và dán tài khoản (hỗ trợ Token, UID|Pass|2FA|Cookie)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Chọn định dạng tài khoản (tùy chọn):",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))

            // 3 nút chọn định dạng: [ Token ], [ UID|PASS|2FA|COOKIE ], [ UID|PASS|COOKIE ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FbLoginFormat.values().forEach { format ->
                    val isSelected = selectedFormat == format
                    Box(
                        modifier = Modifier
                            .weight(if (format == FbLoginFormat.TOKEN) 0.85f else 1.25f)
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
                                onClick = {
                                    selectedFormat = if (selectedFormat == format) null else format
                                }
                            )
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = format.label,
                            fontSize = if (format == FbLoginFormat.TOKEN) 13.sp else 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CardWhite else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Hiển thị hướng dẫn theo định dạng đã chọn
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (selectedFormat != null) "Định dạng chọn: ${selectedFormat?.label}" else "Chưa chọn định dạng (Tự động nhận diện)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1877F2)
                    )
                    Text(
                        text = selectedFormat?.description ?: "Bấm chọn 1 nút phía trên nếu muốn cố định định dạng, hoặc dán trực tiếp vào ô dưới (hệ thống sẽ tự nhận diện).",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                "Dữ liệu tài khoản (mỗi dòng 1 nick):",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))

            SelectionContainer {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (selectedFormat != null) "Ví dụ:\n${selectedFormat?.sample}" else "Dán Token (EAA...), Cookie hoặc UID|PASS|... (mỗi dòng 1 nick)",
                            color = TextSecondary.copy(alpha = 0.6f),
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

            Spacer(Modifier.height(8.dp))

            Text(
                text = "💡 Hệ thống tự động kiểm tra Live, lấy Avatar, Tên thật, UID và danh sách Page của tài khoản.",
                fontSize = 11.sp,
                color = TextSecondary,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(20.dp))

            // Nút Hủy & Đăng nhập
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = !isLoading
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

                        isLoading = true
                        scope.launch {
                            val checkedAccounts = withContext(Dispatchers.IO) {
                                raw.lines().mapNotNull { rawLine ->
                                    val line = rawLine.trim()
                                    if (line.isBlank()) return@mapNotNull null

                                    async(Dispatchers.IO) {
                                        val accountManager = FacebookAccountManager()
                                        val parts = if (line.contains("|")) line.split("|").map { it.trim() } else listOf(line)

                                        // 1. Ưu tiên kiểm tra Token trực tiếp (Nút Token hoặc dòng bắt đầu bằng EAA)
                                        val directToken = when {
                                            line.startsWith("EAA") -> line
                                            selectedFormat == FbLoginFormat.TOKEN -> {
                                                parts.find { it.startsWith("EAA") } ?: line
                                            }
                                            else -> parts.find { it.startsWith("EAA") }
                                        }

                                        val proxy = parts.find { it.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d+.*""")) }
                                        val cookiePart = parts.find { it.contains("c_user=") || it.contains("xs=") }

                                        // Gọi trực tiếp hàm fetchAccountDetailsWithToken có sẵn (giữ nguyên logic gốc 100%)
                                        if (!directToken.isNullOrBlank() && (directToken.startsWith("EAA") || selectedFormat == FbLoginFormat.TOKEN)) {
                                            try {
                                                val details = accountManager.fetchAccountDetailsWithToken(directToken, proxy)
                                                if (details.isLive) {
                                                    return@async details.copy(
                                                        bio = directToken,
                                                        note = cookiePart ?: details.note,
                                                        phone = proxy.orEmpty(),
                                                        isLive = true
                                                    )
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // 2. Nếu là UID|PASS|... -> Chạy qua FacebookToken.process từ test để get Token EAAAA
                                        var token: String? = null
                                        var cookie: String? = cookiePart
                                        var uidFromLine: String? = null

                                        try {
                                            val processResults = FacebookToken.process(line)
                                            val firstRes = processResults.firstOrNull()
                                            if (firstRes != null && firstRes.success && !firstRes.token.isNullOrBlank()) {
                                                token = firstRes.token
                                                cookie = firstRes.cookie ?: cookie
                                                uidFromLine = firstRes.uid
                                            }
                                        } catch (_: Exception) {}

                                        // 3. Truyền thẳng Token EAAAA vừa lấy được vào hàm fetchAccountDetailsWithToken có sẵn
                                        if (!token.isNullOrBlank()) {
                                            try {
                                                val details = accountManager.fetchAccountDetailsWithToken(token, proxy)
                                                if (details.isLive) {
                                                    val resolvedUid = if (details.uid.isBlank() || details.uid == "Token") (uidFromLine ?: details.uid) else details.uid
                                                    return@async details.copy(
                                                        uid = resolvedUid,
                                                        bio = token,
                                                        note = cookie ?: details.note,
                                                        phone = proxy.orEmpty(),
                                                        isLive = true
                                                    )
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // 4. Ưu tiên phụ: Thử lấy token qua Cookie nếu có cookie
                                        if (!cookie.isNullOrBlank()) {
                                            try {
                                                val cookieAcc = accountManager.getTokenFromCookie(cookie, proxy)
                                                if (cookieAcc != null && cookieAcc.isLive) {
                                                    return@async cookieAcc.copy(
                                                        note = cookie,
                                                        phone = proxy.orEmpty(),
                                                        isLive = true
                                                    )
                                                }
                                            } catch (_: Exception) {}
                                        }

                                        // 5. Mặc định: Ghi nhận tài khoản không Live
                                        val fallbackUid = uidFromLine ?: cookie?.let { c ->
                                            Regex("""c_user=(\d+)""").find(c)?.groupValues?.get(1)
                                        } ?: parts.getOrNull(0)?.takeIf { it.matches(Regex("""\d+""")) } ?: "N/A"

                                        return@async FacebookAccount(
                                            uid = fallbackUid,
                                            name = fallbackUid,
                                            note = cookie.orEmpty(),
                                            bio = directToken ?: token.orEmpty(),
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
