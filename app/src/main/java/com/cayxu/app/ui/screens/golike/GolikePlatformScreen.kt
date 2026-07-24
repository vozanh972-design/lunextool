package com.cayxu.app.ui.screens.golike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.*

/**
 * Màn hình khi bấm vào 1 nền tảng trong Golike (Facebook/YouTube/TikTok/Instagram).
 * Card trạng thái đăng nhập Golike ở đây dùng LẠI đúng GolikeStatusCard (component chung với
 * GolikeScreen) - không vẽ card mới riêng cho màn này, đúng yêu cầu "điểm cố định dùng chung".
 */
@Composable
fun GolikePlatformScreen(navController: NavController, platform: String) {
    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text(platform, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            GolikeStatusCard(navController)

            Spacer(Modifier.height(20.dp))
            Text(
                "Nhiệm vụ $platform sẽ hiển thị ở đây khi có API Golike.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}
