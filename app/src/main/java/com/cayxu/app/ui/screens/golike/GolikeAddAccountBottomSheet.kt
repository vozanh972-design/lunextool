package com.cayxu.app.ui.screens.golike

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
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
fun GolikeAddAccountBottomSheet(
    platform: String,
    onDismiss: () -> Unit,
    onAccountAdded: () -> Unit
) {
    val context = LocalContext.current
    var usernameOrUid by remember { mutableStateOf("") }
    var proxyInput by remember { mutableStateOf("") }

    val platformTitle = when (platform.lowercase()) {
        "facebook" -> "Facebook"
        "instagram" -> "Instagram"
        else -> "TikTok"
    }

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
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            tint = GolikeBrandOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Thêm tài khoản $platformTitle",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Quản lý & làm nhiệm vụ cho Golike",
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

            // Tên tài khoản / UID
            Text(
                text = "Tên đăng nhập / UID / @handle $platformTitle",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = usernameOrUid,
                onValueChange = { usernameOrUid = it },
                placeholder = {
                    Text(
                        when (platform.lowercase()) {
                            "facebook" -> "Nhập UID Facebook hoặc link trang"
                            "instagram" -> "Nhập username Instagram (ví dụ: nguyen_van_a)"
                            else -> "Nhập @username TikTok"
                        }
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Proxy (tùy chọn)
            Text(
                text = "Proxy gắn riêng cho nick (Tùy chọn: host:port hoặc host:port:user:pass)",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = proxyInput,
                onValueChange = { proxyInput = it },
                placeholder = { Text("127.0.0.1:8080:user:pass") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // Nút Thêm tài khoản
            Button(
                onClick = {
                    val rawInput = usernameOrUid.trim()
                    if (rawInput.isBlank()) {
                        Toast.makeText(context, "Vui lòng nhập tên tài khoản hoặc UID", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val cleanId = rawInput.removePrefix("@").trim()
                    val newAccount = GolikeAccount(
                        id = cleanId,
                        platform = platform.lowercase(),
                        username = rawInput,
                        isLive = true,
                        isGolikeLinked = true,
                        proxy = proxyInput.trim(),
                        lastStatus = "Đã thêm vào Golike • Sẵn sàng"
                    )
                    GolikeAccountsStore.addOrUpdateAccount(context, newAccount)
                    Toast.makeText(context, "Đã thêm tài khoản $cleanId vào Golike!", Toast.LENGTH_SHORT).show()
                    onAccountAdded()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GolikeBrandOrange),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lưu tài khoản vào danh sách",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
