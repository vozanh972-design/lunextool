package com.cayxu.app.ui.screens.xsmm

import android.widget.Toast
import androidx.compose.foundation.background
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
import com.cayxu.app.data.local.InstagramAccount
import com.cayxu.app.data.local.InstagramAccountsStore
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.instagram.InstagramApiClient
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class IgInputField(
    val key: String,
    val label: String,
    val isRequired: Boolean = false
)

private fun isCookieContent(text: String): Boolean =
    text.contains("sessionid=") || text.contains("ds_user_id=") || text.contains("csrftoken=")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramCookieBottomSheet(
    onDismiss: () -> Unit,
    onCookieSaved: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val availableFields = remember {
        listOf(
            IgInputField("UID_MAIL", "1. UID / Mail", isRequired = true),
            IgInputField("PASS", "2. Pass", isRequired = true),
            IgInputField("TWO_FA", "3. 2FA"),
            IgInputField("PROXY", "4. Proxy")
        )
    }

    var selectedFields by remember {
        mutableStateOf(listOf(availableFields[0], availableFields[1], availableFields[2]))
    }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val formatDisplay = remember(selectedFields) {
        selectedFields.joinToString(" | ") { it.label.substringAfter(". ") }
    }

    val placeholderText = "Mỗi dòng 1 tài khoản theo định dạng đã chọn:\nusername|password\nemail@example.com|password"


    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
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
                        .background(Color(0xFFE1306C).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color(0xFFE1306C),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Thêm tài khoản Instagram",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "UID/Mail | Pass | 2FA | Proxy (hoặc dán Cookie)",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Chọn trường định dạng
            Text(
                "Chọn các trường dữ liệu:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                availableFields.forEach { field ->
                    val isSelected = selectedFields.any { it.key == field.key }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (field.isRequired) {
                                // Không thể bỏ chọn trường bắt buộc
                            } else {
                                selectedFields = if (isSelected) {
                                    selectedFields.filter { it.key != field.key }
                                } else {
                                    val newOrder = availableFields.filter { f ->
                                        f.key == field.key || selectedFields.any { it.key == f.key }
                                    }
                                    newOrder
                                }
                            }
                        },
                        label = { Text(field.label, fontSize = 11.5.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE1306C).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFFE1306C)
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Ô hiển thị định dạng hiện tại
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFDF2F8))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Định dạng nhập: $formatDisplay",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE1306C)
                )
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
                    enabled = !isLoading,
                    placeholder = {
                        Text(
                            placeholderText,
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 11.5.sp
                        )
                    },
                    minLines = 5,
                    maxLines = 9,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE1306C),
                        cursorColor = Color(0xFFE1306C)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (statusMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = statusMessage ?: "",
                    fontSize = 12.5.sp,
                    color = if (statusMessage?.contains("thành công", ignoreCase = true) == true ||
                        statusMessage?.contains("Đã thêm", ignoreCase = true) == true
                    ) Color(0xFF16A34A) else Color(0xFFDC2626),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Hủy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val rawInput = inputText.trim()
                        if (rawInput.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập tài khoản", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        statusMessage = "Đang xử lý đăng nhập..."

                        val currentSelectedKeys = selectedFields.map { it.key }

                        scope.launch {
                            val lines = rawInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val addedAccounts = mutableListOf<String>()
                            var failedCount = 0
                            var lastErrorMessage = ""

                            withContext(Dispatchers.IO) {
                                for (line in lines) {
                                    try {
                                        if (!isCookieContent(line)) {
                                            // ---- Flow Credential: UID/Mail | Pass | 2FA | Proxy ----
                                            val parts = line.split("|").map { it.trim() }
                                            var username = ""
                                            var password = ""
                                            var twoFa = ""
                                            var proxy = ""

                                            // Map theo thứ tự các trường được chọn
                                            var partIdx = 0
                                            for (key in currentSelectedKeys) {
                                                val value = parts.getOrNull(partIdx).orEmpty()
                                                when (key) {
                                                    "UID_MAIL" -> username = value
                                                    "PASS" -> password = value
                                                    "TWO_FA" -> twoFa = value
                                                    "PROXY" -> proxy = value
                                                }
                                                partIdx++
                                            }

                                            // Fallback thông minh nếu số cột nhập khác với chip
                                            if (username.isBlank() && parts.isNotEmpty()) username = parts[0]
                                            if (password.isBlank() && parts.size > 1) password = parts[1]
                                            if (twoFa.isBlank() && parts.size > 2) {
                                                val candidate = parts[2]
                                                if (candidate.contains(":") && candidate.any { it.isDigit() } && !candidate.contains(" ")) {
                                                    if (proxy.isBlank()) proxy = candidate
                                                } else {
                                                    twoFa = candidate
                                                }
                                            }
                                            if (proxy.isBlank() && parts.size > 3) {
                                                proxy = parts[3]
                                            }

                                            if (username.isBlank() || password.isBlank()) {
                                                failedCount++
                                                lastErrorMessage = "Thiếu tài khoản hoặc mật khẩu"
                                                continue
                                            }

                                            withContext(Dispatchers.Main) {
                                                statusMessage = "Đang đăng nhập $username..."
                                            }

                                            val result = InstagramApiClient.loginWithCredentials(
                                                usernameInput = username,
                                                passwordRaw = password,
                                                twoFaSecret = twoFa.ifBlank { null },
                                                proxy = proxy.ifBlank { null }
                                            )

                                            if (result.isSuccess && result.cookie.isNotBlank()) {
                                                val dev = InstagramApiClient.getDeviceProfileFor(
                                                    result.userId.ifBlank { username }
                                                )
                                                withContext(Dispatchers.Main) {
                                                    InstagramAccountsStore.addAccount(
                                                        context,
                                                        InstagramAccount(
                                                            username = result.username.ifBlank { username },
                                                            userId = result.userId,
                                                            cookie = result.cookie,
                                                            userAgent = dev.userAgent,
                                                            proxy = proxy,
                                                            fullName = result.fullName,
                                                            avatar = result.avatarUrl,
                                                            password = password,
                                                            twoFactor = twoFa,
                                                            isLive = true
                                                        )
                                                    )
                                                    LinkedAccountsStore.addAccount(
                                                        context, "Instagram",
                                                        result.username.ifBlank { username }
                                                    )
                                                    addedAccounts.add(result.username.ifBlank { username })
                                                }
                                            } else {
                                                failedCount++
                                                lastErrorMessage = result.message.ifBlank { "Lỗi đăng nhập từ máy chủ" }
                                            }

                                        } else {
                                            // ---- Flow Cookie: dán cookie trực tiếp ----
                                            var cookiePart = ""
                                            var proxyPart = ""
                                            if (line.contains("|")) {
                                                for (p in line.split("|").map { it.trim() }) {
                                                    when {
                                                        p.contains("sessionid=") || p.contains("ds_user_id=") || p.contains("csrftoken=") ->
                                                            cookiePart = if (cookiePart.isBlank()) p else "$cookiePart; $p"
                                                        p.contains(":") && p.any { c -> c.isDigit() } && !p.contains("=") ->
                                                            proxyPart = p
                                                        else -> if (cookiePart.isBlank()) cookiePart = p
                                                    }
                                                }
                                            } else {
                                                cookiePart = line
                                            }

                                            val normCookie = InstagramApiClient.normalizeToIosCookie(cookiePart)
                                            if (!normCookie.contains("sessionid")) {
                                                failedCount++
                                                continue
                                            }

                                            val dsUidMatch = Regex("ds_user_id=([0-9]+)").find(normCookie)
                                            val sessIdMatch = Regex("sessionid=([^;]+)").find(normCookie)
                                            var dsUserId = dsUidMatch?.groupValues?.getOrNull(1).orEmpty()
                                            if (dsUserId.isBlank() && sessIdMatch != null) {
                                                val sv = sessIdMatch.groupValues[1]
                                                val uid = sv.substringBefore("%3A").substringBefore(":")
                                                if (uid.all { it.isDigit() } && uid.isNotEmpty()) dsUserId = uid
                                            }
                                            if (dsUserId.isBlank()) dsUserId = "${System.currentTimeMillis() % 1000000}"

                                            val check = InstagramApiClient.checkCookieIg(normCookie, proxyPart)
                                            val resolvedUser = check.username.ifBlank { "IG_$dsUserId" }
                                            val userId = check.userId.ifBlank { dsUserId }
                                            val dev = InstagramApiClient.getDeviceProfileFor(userId.ifBlank { resolvedUser })

                                            withContext(Dispatchers.Main) {
                                                InstagramAccountsStore.addAccount(
                                                    context,
                                                    InstagramAccount(
                                                        username = resolvedUser,
                                                        userId = userId,
                                                        cookie = normCookie,
                                                        userAgent = dev.userAgent,
                                                        proxy = proxyPart,
                                                        fullName = check.fullName,
                                                        avatar = check.profilePicUrl,
                                                        fbDtsg = check.fbDtsg,
                                                        lsd = check.lsd,
                                                        biography = check.biography,
                                                        followersCount = check.followersCount,
                                                        followingCount = check.followingCount,
                                                        postsCount = check.postsCount,
                                                        isLive = check.isLive
                                                    )
                                                )
                                                LinkedAccountsStore.addAccount(context, "Instagram", resolvedUser)
                                                addedAccounts.add(resolvedUser)
                                            }
                                        }
                                    } catch (_: Exception) {
                                        failedCount++
                                    }
                                }
                            }

                            isLoading = false
                            if (addedAccounts.isNotEmpty()) {
                                val msg = "Đã thêm thành công ${addedAccounts.size} tài khoản (${addedAccounts.joinToString(", ")})"
                                statusMessage = msg
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onCookieSaved?.invoke(addedAccounts.first())
                                onDismiss()
                            } else {
                                val msg = if (failedCount > 0) {
                                    if (lastErrorMessage.isNotBlank()) "Không thể đăng nhập: $lastErrorMessage"
                                    else "Không thể đăng nhập $failedCount tài khoản. Vui lòng kiểm tra lại thông tin!"
                                } else "Không có dữ liệu hợp lệ để xử lý."
                                statusMessage = msg
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = CardWhite,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Đăng nhập", color = CardWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
