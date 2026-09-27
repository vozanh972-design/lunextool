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
import com.cayxu.app.instagram.GoMaxInstagramEngine
import com.cayxu.app.instagram.InstagramApiClient
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Giao diện thêm tài khoản Instagram chuẩn GoMax Engine:
 * - Ô 1: Nhập Cookie Instagram (Bắt buộc - chứa sessionid, ds_user_id, csrftoken)
 * - Ô 2: Proxy (Tùy chọn - IP:Port hoặc IP:Port:User:Pass)
 * - Bỏ hẳn toàn bộ các nút chọn trường UID/Pass/2FA
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramCookieBottomSheet(
    onDismiss: () -> Unit,
    onCookieSaved: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var inputMultiLineText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

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
                        "Chuẩn GoMax: Định dạng Cookie hoặc Cookie|Proxy",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Ô duy nhất: Danh sách tài khoản (mỗi dòng 1 nick)
            Text(
                "Danh sách tài khoản (mỗi dòng 1 nick):",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))

            SelectionContainer {
                OutlinedTextField(
                    value = inputMultiLineText,
                    onValueChange = { inputMultiLineText = it },
                    enabled = !isLoading,
                    placeholder = {
                        Text(
                            "Định dạng: Cookie hoặc Cookie|Proxy\nVí dụ:\nsessionid=...; ds_user_id=123...; csrftoken=...|103.152.118.23:8080:user:pass\nsessionid=...; ds_user_id=456...; csrftoken=...|103.152.118.24:8080\nsessionid=...; ds_user_id=789...; csrftoken=...",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    },
                    minLines = 6,
                    maxLines = 12,
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
                    color = if (statusMessage?.contains("thành công", ignoreCase = true) == true) {
                        Color(0xFF16A34A)
                    } else {
                        Color(0xFFDC2626)
                    },
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(20.dp))

            // Nút Hủy & Thêm tài khoản
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Hủy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val rawInput = inputMultiLineText.trim()
                        if (rawInput.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập danh sách tài khoản", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val lines = rawInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                        val hasValidCookieLine = lines.any { line ->
                            val rawCookie = if (line.contains("|")) line.substring(0, line.lastIndexOf("|")).trim() else line
                            rawCookie.contains("sessionid") && rawCookie.contains("ds_user_id") && rawCookie.contains("csrftoken")
                        }

                        if (!hasValidCookieLine) {
                            val msg = "Cookie thiếu các trường bắt buộc (cần có sessionid, ds_user_id, csrftoken)"
                            statusMessage = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        isLoading = true
                        statusMessage = "Đang kiểm tra tài khoản Instagram..."

                        scope.launch {
                            val addedAccounts = mutableListOf<String>()
                            var hasCheckpointOrDie = false

                            withContext(Dispatchers.IO) {
                                for (line in lines) {
                                    var rawCookie = ""
                                    var proxyStr: String? = null
                                    if (line.contains("|")) {
                                        val lastPipeIndex = line.lastIndexOf("|")
                                        rawCookie = line.substring(0, lastPipeIndex).trim()
                                        proxyStr = line.substring(lastPipeIndex + 1).trim().takeIf { it.isNotBlank() }
                                    } else {
                                        rawCookie = line
                                    }

                                    if (!rawCookie.contains("sessionid") || !rawCookie.contains("ds_user_id") || !rawCookie.contains("csrftoken")) {
                                        continue
                                    }

                                    val engine = GoMaxInstagramEngine.create(proxyStr)
                                    val igAccount = engine.checkLiveCookie(rawCookie, proxyStr)

                                    if (igAccount != null && igAccount.isLive) {
                                        val dev = InstagramApiClient.getDeviceProfileFor(igAccount.userId)
                                        val pic = try {
                                            InstagramApiClient.fetchProfilePic(igAccount.username, rawCookie, proxyStr)
                                        } catch (_: Exception) { null }

                                        withContext(Dispatchers.Main) {
                                            InstagramAccountsStore.addAccount(
                                                context,
                                                InstagramAccount(
                                                    username = igAccount.username,
                                                    userId = igAccount.userId,
                                                    cookie = igAccount.cookie,
                                                    userAgent = dev.userAgent,
                                                    proxy = proxyStr.orEmpty(),
                                                    fullName = igAccount.username,
                                                    avatar = pic.orEmpty(),
                                                    fbDtsg = igAccount.fbDtsg.orEmpty(),
                                                    lsd = igAccount.lsd.orEmpty(),
                                                    isLive = true
                                                )
                                            )
                                            LinkedAccountsStore.addAccount(context, "Instagram", igAccount.username)
                                            addedAccounts.add(igAccount.username)
                                        }
                                    } else {
                                        hasCheckpointOrDie = true
                                    }
                                }
                            }

                            isLoading = false
                            if (addedAccounts.isNotEmpty()) {
                                val successMsg = if (addedAccounts.size == 1) {
                                    "Thêm tài khoản @${addedAccounts.first()} thành công!"
                                } else {
                                    "Đã thêm thành công ${addedAccounts.size} tài khoản (${addedAccounts.joinToString(", ")})"
                                }
                                statusMessage = successMsg
                                Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
                                onCookieSaved?.invoke()
                                onDismiss()
                            } else {
                                val errMsg = if (hasCheckpointOrDie) {
                                    "Cookie không hợp lệ hoặc tài khoản bị Checkpoint"
                                } else {
                                    "Không thể xác thực cookie Instagram. Vui lòng kiểm tra lại!"
                                }
                                statusMessage = errMsg
                                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = CardWhite,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Thêm tài khoản", color = CardWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
