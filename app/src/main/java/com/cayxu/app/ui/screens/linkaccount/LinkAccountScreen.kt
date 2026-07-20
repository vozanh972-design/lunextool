package com.cayxu.app.ui.screens.linkaccount

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.ui.theme.AppBackground
import com.cayxu.app.ui.theme.CardWhite
import com.cayxu.app.ui.theme.Primary
import com.cayxu.app.ui.theme.SuccessGreen
import com.cayxu.app.ui.theme.TextPrimary
import com.cayxu.app.ui.theme.TextSecondary

/**
 * Màn hình liên kết tài khoản mạng xã hội (Facebook/TikTok/Instagram/LinkedIn/Snapchat/Threads).
 * TODO: chưa nối OAuth thật với từng nền tảng - nút "Thêm tài khoản" hiện chỉ mô phỏng trạng thái
 * đã liên kết trên UI (lưu ở state cục bộ của màn này). Khi có API/OAuth thật, thay hàm
 * `onClick` của nút bằng luồng đăng nhập nền tảng tương ứng rồi gọi API liên kết ở backend.
 */
@Composable
fun LinkAccountScreen(navController: NavController, platform: String, iconRes: Int) {
    val context = LocalContext.current
    var isLinked by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Text(
                "Liên kết tài khoản",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(Modifier.height(36.dp))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardWhite),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != 0) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = platform,
                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(14.dp))
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Thêm tài khoản $platform",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Liên kết $platform để xác minh danh tính và mở thêm nhiệm vụ tăng tương tác trên nền tảng này.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background((if (isLinked) SuccessGreen else TextSecondary).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconRes != 0) {
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(platform, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    Text(
                        if (isLinked) "Đã liên kết" else "Chưa liên kết",
                        color = if (isLinked) SuccessGreen else TextSecondary,
                        fontSize = 12.sp
                    )
                }
                if (isLinked) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = {
                if (!isLinked) {
                    isLoading = true
                    // TODO: thay đoạn này bằng luồng OAuth thật của $platform khi có backend hỗ trợ.
                    isLoading = false
                    isLinked = true
                    Toast.makeText(context, "Đã thêm tài khoản $platform (giao diện minh hoạ, chưa nối OAuth thật)", Toast.LENGTH_LONG).show()
                }
            },
            enabled = !isLinked && !isLoading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLinked) SuccessGreen else Primary,
                disabledContainerColor = SuccessGreen
            ),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = CardWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    if (isLinked) "Đã thêm tài khoản" else "Thêm tài khoản",
                    color = CardWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Tài khoản $platform sẽ chỉ dùng để xác minh, CâyXu không đăng bài hay truy cập dữ liệu riêng tư của bạn.",
            fontSize = 11.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))
    }
}
