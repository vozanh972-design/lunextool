package com.cayxu.app.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.components.CayXuBottomBar
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary
import com.cayxu.app.util.IntegrityGuard

@Composable
fun AccountScreen(navController: NavController) {
    val context = LocalContext.current
    var showDebugInfo by remember { mutableStateOf(false) }

    Scaffold(containerColor = AppBackground, bottomBar = { CayXuBottomBar(navController) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppBackground)
                .padding(16.dp)
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Tài khoản (đang phát triển)", color = TextSecondary)
            }

            // Bấm để xem/ẩn mã chữ ký thật của APK đang chạy trên máy này -
            // dùng để tự đối chiếu với EXPECTED_SIGNATURE_SHA256 trong code,
            // xác nhận app đang chạy đúng bản đã ký, không bị patch/thay thế.
            TextButton(onClick = { showDebugInfo = !showDebugInfo }) {
                Text(if (showDebugInfo) "Ẩn thông tin chữ ký" else "Xem thông tin chữ ký (debug)")
            }

            if (showDebugInfo) {
                val hash = remember { IntegrityGuard.currentSignatureSha256(context) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(CardWhite, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text("SHA-256 chữ ký APK hiện tại:", color = TextPrimary, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(hash, color = TextSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "So sánh chuỗi này với EXPECTED_SIGNATURE_SHA256 trong IntegrityGuard.kt - " +
                            "trùng nhau nghĩa là app đang chạy đúng bản đã ký, không bị patch.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
