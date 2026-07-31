package com.cayxu.app.ui.screens.golike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.TikTokAccount
import com.cayxu.app.data.local.TikTokAccountsStore
import com.cayxu.app.data.local.TikTokAppVariant
import com.cayxu.app.ui.theme.*

/**
 * Màn Golike riêng cho TikTok - hiển thị 3 loại app TikTok (TikTok / TikTok Lite / TikTok Studio).
 * Mỗi loại chỉ ĐỌC LẠI tài khoản đã được bật (enabled = true) từ TikTokLinkAccountScreen,
 * KHÔNG cho check/tick lại ở đây (đúng yêu cầu "check bên nào thì đọc lại thôi").
 * Dưới mỗi loại là 2 nút "Cấu hình chạy" và "Chạy" - CHƯA gắn logic chạy gì cả.
 * Không đụng tới TikTokLinkAccountScreen/TikTokAccountsStore hay các màn khác.
 */
@Composable
fun GolikeTikTokScreen(navController: NavController) {
    val context = LocalContext.current
    val allAccounts = remember { mutableStateOf(TikTokAccountsStore.getAccounts(context)) }
    val enabledAccounts = allAccounts.value.filter { it.enabled }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("TikTok", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            GolikeStatusCard(navController)

            Spacer(Modifier.height(20.dp))
            Text("Loại TikTok", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tài khoản hiển thị bên dưới là tài khoản đã bật ở phần Quản lý tài khoản TikTok, không cần chọn lại ở đây.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TikTokVariantCard(
                    label = "TikTok",
                    accounts = enabledAccounts.filter { it.variant == TikTokAppVariant.STANDARD }
                )
                TikTokVariantCard(
                    label = "TikTok Lite",
                    accounts = enabledAccounts.filter { it.variant == TikTokAppVariant.LITE }
                )
                TikTokVariantCard(
                    label = "TikTok Studio",
                    accounts = enabledAccounts.filter { it.variant == TikTokAppVariant.STUDIO }
                )
            }
        }
    }
}

@Composable
private fun TikTokVariantCard(label: String, accounts: List<TikTokAccount>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(Primary.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("${accounts.size} tài khoản", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))
            if (accounts.isEmpty()) {
                Text(
                    "Chưa có tài khoản nào được bật cho loại này.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(6.dp).background(SuccessGreen, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            account.displayName.ifBlank { account.handle.ifBlank { "Chưa xác định" } },
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { /* Chưa gắn logic - chỉ hiển thị nút */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cấu hình chạy")
                }
                Button(
                    onClick = { /* Chưa gắn logic - chỉ hiển thị nút */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chạy")
                }
            }
        }
    }
}
