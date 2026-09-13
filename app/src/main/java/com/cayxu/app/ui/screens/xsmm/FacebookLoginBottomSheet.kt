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
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import com.cayxu.app.utils.FacebookLiveChecker

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacebookLoginBottomSheet(
    onDismiss: () -> Unit,
    onAccountSaved: ((FacebookAccount) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cookieText by remember { mutableStateOf("") }
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
                        "Dán Cookie hoặc Access Token Facebook",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text("Cookie / Token Facebook", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = cookieText,
                onValueChange = { cookieText = it },
                placeholder = { Text("Dán chuỗi cookie (c_user=...; xs=...) hoặc Access Token (EAAB...)", color = TextSecondary, fontSize = 13.sp) },
                minLines = 5,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1877F2),
                    cursorColor = Color(0xFF1877F2)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(22.dp))

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
                        val trimmed = cookieText.trim()
                        if (trimmed.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập Cookie hoặc Token", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        val extractedUid = extractUidFromCookie(trimmed) ?: if (trimmed.startsWith("EAA")) "Token_${System.currentTimeMillis() % 100000}" else "FB_${System.currentTimeMillis() % 100000}"
                        val isToken = trimmed.startsWith("EAA")
                        val account = FacebookAccount(
                            uid = extractedUid,
                            name = "",
                            note = if (!isToken) trimmed else "",
                            bio = if (isToken) trimmed else "",
                            isLive = false
                        )
                        FacebookAccountsStore.addAccount(context, account)

                        if (!isToken) {
                            FacebookLiveChecker.checkCookieWithAvatarAndName(
                                cookieString = trimmed,
                                onResult = { uid, isLive, avatarUrl, fullName ->
                                    val updated = account.copy(
                                        uid = uid ?: account.uid,
                                        name = fullName ?: account.name,
                                        avatar = avatarUrl ?: account.avatar,
                                        isLive = isLive
                                    )
                                    FacebookAccountsStore.updateAccount(context, updated)
                                    isLoading = false
                                    Toast.makeText(context, "Đã đăng nhập tài khoản Facebook: ${updated.name.ifBlank { updated.uid }}", Toast.LENGTH_SHORT).show()
                                    onAccountSaved?.invoke(updated)
                                    onDismiss()
                                }
                            )
                        } else {
                            isLoading = false
                            Toast.makeText(context, "Đã đăng nhập tài khoản Facebook", Toast.LENGTH_SHORT).show()
                            onAccountSaved?.invoke(account)
                            onDismiss()
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
