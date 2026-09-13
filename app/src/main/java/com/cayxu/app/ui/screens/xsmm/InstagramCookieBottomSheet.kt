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
import com.cayxu.app.data.local.LinkedAccountsStore
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramCookieBottomSheet(
    onDismiss: () -> Unit,
    onCookieSaved: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cookieText by remember { mutableStateOf("") }

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
                        "Dán cookie tài khoản Instagram để chạy đa luồng",
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
                placeholder = { Text("Dán toàn bộ chuỗi cookie vào đây (sessionid=...; ds_user_id=...)", color = TextSecondary, fontSize = 13.sp) },
                minLines = 5,
                maxLines = 8,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE1306C),
                    cursorColor = Color(0xFFE1306C)
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
                            Toast.makeText(context, "Vui lòng nhập cookie", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // Extract ds_user_id or ds_user if present
                        val dsUserIdRegex = Regex("""ds_user_id=([0-9a-zA-Z_.-]+)""")
                        val match = dsUserIdRegex.find(trimmed)
                        val accountIdentifier = if (match != null) {
                            "IG_${match.groupValues[1]}"
                        } else {
                            "IG_${System.currentTimeMillis() % 100000}"
                        }
                        LinkedAccountsStore.addAccount(context, "Instagram", accountIdentifier)
                        Toast.makeText(context, "Đã lưu Cookie Instagram: $accountIdentifier", Toast.LENGTH_SHORT).show()
                        onCookieSaved?.invoke(accountIdentifier)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Lưu Cookie", color = CardWhite, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
