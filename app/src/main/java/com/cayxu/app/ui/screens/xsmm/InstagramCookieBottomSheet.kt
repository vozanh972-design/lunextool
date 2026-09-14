package com.cayxu.app.ui.screens.xsmm

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
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
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.instagram.InstagramAuthService
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramCookieBottomSheet(
    onDismiss: () -> Unit,
    onCookieSaved: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cookieText by remember { mutableStateOf("") }
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
                        "Nhập Cookie Instagram",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "Dán cookie tài khoản Instagram (hỗ trợ nhiều tài khoản, mỗi dòng 1 cookie)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text("Cookie Instagram", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = cookieText,
                onValueChange = { cookieText = it },
                enabled = !isLoading,
                placeholder = { 
                    Text(
                        "Dán cookie hoặc định dạng user|pass|cookie...\n(sessionid=...; ds_user_id=...; csrftoken=...)", 
                        color = TextSecondary, 
                        fontSize = 13.sp
                    ) 
                },
                minLines = 5,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE1306C),
                    cursorColor = Color(0xFFE1306C)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (statusMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = statusMessage ?: "",
                    fontSize = 12.5.sp,
                    color = if (statusMessage?.contains("thành công", ignoreCase = true) == true) Color(0xFF16A34A) else Color(0xFFDC2626),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(22.dp))

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
                        val rawInput = cookieText.trim()
                        if (rawInput.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập cookie", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        statusMessage = "Đang kiểm tra và xác thực cookie..."

                        scope.launch {
                            val authService = InstagramAuthService()
                            val lines = rawInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val addedAccounts = mutableListOf<String>()
                            var failedCount = 0

                            withContext(Dispatchers.IO) {
                                val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                for (line in lines) {
                                    try {
                                        // Tìm chuỗi cookie trong dòng (nếu dòng có dạng uid|pass|cookie)
                                        val cookiePart = if (line.contains("sessionid=") || line.contains("csrftoken=")) {
                                            val parts = line.split("|")
                                            parts.firstOrNull { it.contains("sessionid=") || it.contains("csrftoken=") } ?: line
                                        } else {
                                            line
                                        }

                                        // Trích xuất ds_user_id và csrftoken trực tiếp từ cookie (không cần HTTP)
                                        val dsUserIdMatch = Regex("ds_user_id=([0-9]+)").find(cookiePart)
                                        val csrfMatch = Regex("csrftoken=([^;]+)").find(cookiePart)

                                        if (dsUserIdMatch == null || csrfMatch == null) {
                                            failedCount++
                                            continue
                                        }

                                        val dsUserId = dsUserIdMatch.groupValues[1]
                                        var username = "IG_$dsUserId"
                                        var fullName = ""
                                        var avatar = ""
                                        var fbDtsg = ""
                                        var lsd = ""

                                        // Thử verify để lấy thêm thông tin (username, avatar...)
                                        // Nếu lỗi vẫn lưu account bằng ds_user_id
                                        try {
                                            val userInfo = authService.verifyCookieAndGetUserInfo(cookiePart, desktopUA)
                                            if (userInfo.username.isNotBlank()) username = userInfo.username
                                            fullName = userInfo.fullName
                                            avatar = userInfo.profilePicUrl ?: ""
                                            fbDtsg = userInfo.fbDtsg ?: ""
                                            lsd = userInfo.lsd ?: ""
                                        } catch (_: Exception) {
                                            // Không lấy được thông tin chi tiết — vẫn lưu với ds_user_id
                                        }

                                        withContext(Dispatchers.Main) {
                                            com.cayxu.app.data.local.InstagramAccountsStore.addAccount(
                                                context,
                                                com.cayxu.app.data.local.InstagramAccount(
                                                    username = username,
                                                    userId = dsUserId,
                                                    cookie = cookiePart,
                                                    userAgent = desktopUA,
                                                    fullName = fullName,
                                                    avatar = avatar,
                                                    fbDtsg = fbDtsg,
                                                    lsd = lsd
                                                )
                                            )
                                            LinkedAccountsStore.addAccount(context, "Instagram", username)
                                            addedAccounts.add(username)
                                        }
                                    } catch (e: Exception) {
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
                                val msg = "Không thể xác thực cookie Instagram. Vui lòng kiểm tra lại cookie!"
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
