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

private fun isCookieLine(line: String): Boolean =
    line.contains("sessionid=") || line.contains("ds_user_id=") || line.contains("csrftoken=")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramCookieBottomSheet(
    onDismiss: () -> Unit,
    onCookieSaved: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var inputText by remember { mutableStateOf("") }
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
                        "Them tai khoan Instagram",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary
                    )
                    Text(
                        "username|pass | username|pass|2fa | username|pass|2fa|proxy",
                        fontSize = 11.5.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFDF2F8))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Dinh dang moi dong:\n" +
                           "  username|password\n" +
                           "  username|password|2fa_secret\n" +
                           "  username|password|2fa_secret|ip:port:user:pass",
                    fontSize = 11.5.sp,
                    color = Color(0xFFE1306C),
                    lineHeight = 17.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Du lieu tai khoan (moi dong 1 nick):",
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
                            "czzpbkh8745|matkhau123\nczzpbkh8745|matkhau123|JBSWY3DPEHPK3PXP",
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
                    color = if (statusMessage?.startsWith("Da them") == true)
                        Color(0xFF16A34A) else Color(0xFFDC2626),
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
                    Text("Huy", color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        val rawInput = inputText.trim()
                        if (rawInput.isBlank()) {
                            Toast.makeText(context, "Vui long nhap tai khoan", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        statusMessage = "Dang xu ly..."

                        scope.launch {
                            val lines = rawInput.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val addedAccounts = mutableListOf<String>()
                            var failedCount = 0

                            withContext(Dispatchers.IO) {
                                for (line in lines) {
                                    try {
                                        if (!isCookieLine(line)) {
                                            // Credential flow: username|pass|2fa|proxy
                                            val parts = line.split("|").map { it.trim() }
                                            val username = parts.getOrNull(0).orEmpty()
                                            val password = parts.getOrNull(1).orEmpty()
                                            val twoFa    = parts.getOrNull(2).orEmpty()
                                            val proxy    = parts.getOrNull(3).orEmpty()

                                            if (username.isBlank() || password.isBlank()) {
                                                failedCount++; continue
                                            }

                                            withContext(Dispatchers.Main) {
                                                statusMessage = "Dang dang nhap $username..."
                                            }

                                            val result = InstagramApiClient.loginWithCredentials(
                                                usernameInput = username,
                                                passwordRaw   = password,
                                                twoFaSecret   = twoFa.ifBlank { null },
                                                proxy         = proxy.ifBlank { null }
                                            )

                                            if (result.isSuccess && result.cookie.isNotBlank()) {
                                                val dev = InstagramApiClient.getDeviceProfileFor(
                                                    result.userId.ifBlank { username }
                                                )
                                                withContext(Dispatchers.Main) {
                                                    InstagramAccountsStore.addAccount(
                                                        context,
                                                        InstagramAccount(
                                                            username  = result.username.ifBlank { username },
                                                            userId    = result.userId,
                                                            cookie    = result.cookie,
                                                            userAgent = dev.userAgent,
                                                            proxy     = proxy,
                                                            fullName  = result.fullName,
                                                            avatar    = result.avatarUrl,
                                                            password  = password,
                                                            twoFactor = twoFa,
                                                            isLive    = true
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
                                            }

                                        } else {
                                            // Cookie flow: sessionid=...|proxy
                                            var cookiePart = ""
                                            var proxyPart  = ""
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
                                            if (!normCookie.contains("sessionid")) { failedCount++; continue }

                                            val dsUidMatch  = Regex("ds_user_id=([0-9]+)").find(normCookie)
                                            val sessIdMatch = Regex("sessionid=([^;]+)").find(normCookie)
                                            var dsUserId = dsUidMatch?.groupValues?.getOrNull(1).orEmpty()
                                            if (dsUserId.isBlank() && sessIdMatch != null) {
                                                val sv = sessIdMatch.groupValues[1]
                                                val uid = sv.substringBefore("%3A").substringBefore(":")
                                                if (uid.all { it.isDigit() } && uid.isNotEmpty()) dsUserId = uid
                                            }
                                            if (dsUserId.isBlank()) dsUserId = "${System.currentTimeMillis() % 1000000}"

                                            val check = InstagramApiClient.checkCookieIg(normCookie, proxyPart)
                                            val username = check.username.ifBlank { "IG_$dsUserId" }
                                            val userId   = check.userId.ifBlank { dsUserId }
                                            val dev      = InstagramApiClient.getDeviceProfileFor(userId.ifBlank { username })

                                            withContext(Dispatchers.Main) {
                                                InstagramAccountsStore.addAccount(
                                                    context,
                                                    InstagramAccount(
                                                        username       = username,
                                                        userId         = userId,
                                                        cookie         = normCookie,
                                                        userAgent      = dev.userAgent,
                                                        proxy          = proxyPart,
                                                        fullName       = check.fullName,
                                                        avatar         = check.profilePicUrl,
                                                        fbDtsg         = check.fbDtsg,
                                                        lsd            = check.lsd,
                                                        biography      = check.biography,
                                                        followersCount = check.followersCount,
                                                        followingCount = check.followingCount,
                                                        postsCount     = check.postsCount,
                                                        isLive         = check.isLive
                                                    )
                                                )
                                                LinkedAccountsStore.addAccount(context, "Instagram", username)
                                                addedAccounts.add(username)
                                            }
                                        }
                                    } catch (_: Exception) { failedCount++ }
                                }
                            }

                            isLoading = false
                            if (addedAccounts.isNotEmpty()) {
                                val msg = "Da them thanh cong ${addedAccounts.size} tai khoan (${addedAccounts.joinToString(", ")})"
                                statusMessage = msg
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onCookieSaved?.invoke(addedAccounts.first())
                                onDismiss()
                            } else {
                                val msg = if (failedCount > 0)
                                    "Khong the xu ly $failedCount dong. Kiem tra lai username/password!"
                                else "Khong co du lieu hop le."
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
                        Text("Dang nhap", color = CardWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
