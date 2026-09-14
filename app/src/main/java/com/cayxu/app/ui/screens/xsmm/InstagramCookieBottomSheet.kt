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

private data class IgFieldItem(
    val key: String,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramCookieBottomSheet(
    onDismiss: () -> Unit,
    onCookieSaved: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val availableIgFields = remember {
        listOf(
            IgFieldItem("COOKIE", "Cookie (bắt buộc)"),
            IgFieldItem("PROXY", "Proxy (ip:port:user:pass)")
        )
    }

    var selectedFields by remember { mutableStateOf(listOf(availableIgFields[0])) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val formatString = remember(selectedFields) {
        if (selectedFields.isEmpty()) "Cookie"
        else selectedFields.joinToString(" | ") { it.label.substringBefore(" (") }
    }

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
                        "Thêm tài khoản Instagram",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "Hỗ trợ nhập Cookie hoặc Cookie | Proxy (Mỗi dòng 1 nick)",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Chọn trường định dạng (Cookie / Proxy)
            Text("Chọn trường định dạng nhập:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableIgFields.forEach { field ->
                    val isSelected = selectedFields.any { it.key == field.key }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (field.key == "COOKIE") {
                                // Cookie luôn luôn bắt buộc
                            } else {
                                selectedFields = if (isSelected) {
                                    selectedFields.filter { it.key != field.key }
                                } else {
                                    selectedFields + field
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

            Spacer(Modifier.height(10.dp))

            // Box hiển thị định dạng hiện tại
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFDF2F8))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Định dạng: $formatString",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE1306C)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text("Dữ liệu tài khoản Instagram (mỗi dòng 1 nick):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))

            SelectionContainer {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    enabled = !isLoading,
                    placeholder = { 
                        Text(
                            if (selectedFields.any { it.key == "PROXY" })
                                "c_user=...; xs=...; datr=... | 128.0.0.1:3098:user:pass\n(hoặc dán sessionid=...; ds_user_id=... | proxy)"
                            else
                                "sessionid=...; ds_user_id=...; csrftoken=...\n(Mỗi dòng 1 cookie)",
                            color = TextSecondary.copy(alpha = 0.7f), 
                            fontSize = 12.5.sp
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
            }

            if (statusMessage != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = statusMessage ?: "",
                    fontSize = 12.5.sp,
                    color = if (statusMessage?.contains("thành công", ignoreCase = true) == true) Color(0xFF16A34A) else Color(0xFFDC2626),
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
                            Toast.makeText(context, "Vui lòng nhập cookie", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isLoading = true
                        statusMessage = "Đang kiểm tra và xác thực tài khoản Instagram..."

                        scope.launch {
                            val lines = rawInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val addedAccounts = mutableListOf<String>()
                            var failedCount = 0

                            withContext(Dispatchers.IO) {
                                val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                for (line in lines) {
                                    try {
                                        var cookiePart = ""
                                        var proxyPart = ""

                                        if (line.contains("|")) {
                                            val parts = line.split("|").map { it.trim() }
                                            for (p in parts) {
                                                if (p.contains("sessionid=") || p.contains("csrftoken=") || p.contains("ds_user_id=") || p.contains("c_user=") || p.contains("mid=")) {
                                                    cookiePart = if (cookiePart.isBlank()) p else "$cookiePart; $p"
                                                } else if (p.contains(":") && p.any { it.isDigit() } && !p.contains("=")) {
                                                    proxyPart = p
                                                } else if (cookiePart.isBlank()) {
                                                    cookiePart = p
                                                }
                                            }
                                        } else {
                                            cookiePart = line
                                        }

                                        val dsUserIdMatch = Regex("ds_user_id=([0-9]+)").find(cookiePart)
                                        val sessionIdMatch = Regex("sessionid=([^;]+)").find(cookiePart)

                                        if (dsUserIdMatch == null && sessionIdMatch == null && !cookiePart.contains("sessionid")) {
                                            failedCount++
                                            continue
                                        }

                                        var dsUserId = dsUserIdMatch?.groupValues?.getOrNull(1) ?: ""
                                        if (dsUserId.isBlank() && sessionIdMatch != null) {
                                            val sVal = sessionIdMatch.groupValues[1]
                                            val potentialUid = sVal.substringBefore("%3A").substringBefore(":")
                                            if (potentialUid.all { it.isDigit() } && potentialUid.isNotEmpty()) {
                                                dsUserId = potentialUid
                                            }
                                        }
                                        if (dsUserId.isBlank()) {
                                            dsUserId = "${System.currentTimeMillis() % 1000000}"
                                        }
                                        var username = "IG_$dsUserId"
                                        var fullName = ""
                                        var avatar = ""
                                        var fbDtsg = ""
                                        var lsd = ""
                                        var biography = ""
                                        var followersCount = 0
                                        var followingCount = 0
                                        var postsCount = 0

                                        val proxyConfig = com.cayxu.app.instagram.InstagramApiClient.parseProxy(proxyPart)
                                        val client = com.cayxu.app.instagram.InstagramApiClient(
                                            cookie = cookiePart,
                                            userAgent = desktopUA,
                                            proxyConfig = proxyConfig
                                        )

                                        try {
                                            val userInfo = client.fetchAccountDetails()
                                            if (userInfo.username.isNotBlank()) username = userInfo.username
                                            fullName = userInfo.fullName
                                            avatar = userInfo.profilePicUrl ?: ""
                                            fbDtsg = userInfo.fbDtsg ?: ""
                                            lsd = userInfo.lsd ?: ""
                                            biography = userInfo.biography
                                            followersCount = userInfo.followersCount
                                            followingCount = userInfo.followingCount
                                            postsCount = userInfo.postsCount
                                        } catch (_: Exception) {
                                            try {
                                                val profile = client.fetchUserInfo()
                                                if (profile.username.isNotBlank()) username = profile.username
                                                fullName = profile.fullName
                                                avatar = profile.profilePicUrl ?: ""
                                                fbDtsg = profile.fbDtsg ?: ""
                                                lsd = profile.lsd ?: ""
                                            } catch (_: Exception) {}
                                        }

                                        withContext(Dispatchers.Main) {
                                            com.cayxu.app.data.local.InstagramAccountsStore.addAccount(
                                                context,
                                                com.cayxu.app.data.local.InstagramAccount(
                                                    username = username,
                                                    userId = dsUserId,
                                                    cookie = cookiePart,
                                                    userAgent = desktopUA,
                                                    proxy = proxyPart,
                                                    fullName = fullName,
                                                    avatar = avatar,
                                                    fbDtsg = fbDtsg,
                                                    lsd = lsd,
                                                    biography = biography,
                                                    followersCount = followersCount,
                                                    followingCount = followingCount,
                                                    postsCount = postsCount,
                                                    isLive = true
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
                                val msg = "Không thể xác thực tài khoản Instagram. Vui lòng kiểm tra lại Cookie/Proxy!"
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
