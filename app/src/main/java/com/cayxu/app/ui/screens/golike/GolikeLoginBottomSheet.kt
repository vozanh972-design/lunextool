package com.cayxu.app.ui.screens.golike

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
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
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

private val GolikeBrandOrange = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GolikeLoginBottomSheet(
    onDismiss: () -> Unit,
    onLoginSuccess: (username: String) -> Unit
) {
    val context = LocalContext.current
    var usernameInput by remember { mutableStateOf(GolikeSession.username.value) }
    var tokenInput by remember { mutableStateOf(GolikeSession.token.value) }
    var balanceInput by remember { mutableStateOf(if (GolikeSession.balance.value > 0) GolikeSession.balance.value.toString() else "") }
    var isSubmitting by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GolikeBrandOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = GolikeBrandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Tài khoản Golike",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Đăng nhập hoặc đổi tài khoản Golike",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(18.dp))

            // Tên tài khoản Golike
            Text(
                text = "Tên tài khoản Golike (Username)",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = usernameInput,
                onValueChange = { usernameInput = it },
                placeholder = { Text("Ví dụ: golike_vip88") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Authorization Token
            Text(
                text = "Authorization Token (Tùy chọn hoặc bắt buộc nếu có)",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                placeholder = { Text("Nhập Authorization Token của Golike") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Số dư ban đầu
            Text(
                text = "Số dư hiện tại (VNĐ / Xu)",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = balanceInput,
                onValueChange = { if (it.all { c -> c.isDigit() }) balanceInput = it },
                placeholder = { Text("0") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // Nút Lưu / Đăng nhập
            Button(
                onClick = {
                    val uname = usernameInput.trim()
                    if (uname.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập tên tài khoản Golike", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val token = tokenInput.trim().ifBlank { "token_$uname" }
                    val balance = balanceInput.toLongOrNull() ?: 0L

                    isSubmitting = true
                    GolikeSession.login(context, token, uname, balance)
                    Toast.makeText(context, "Đã lưu tài khoản Golike: $uname", Toast.LENGTH_SHORT).show()
                    onLoginSuccess(uname)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GolikeBrandOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Login, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Xác nhận & Lưu phiên",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
