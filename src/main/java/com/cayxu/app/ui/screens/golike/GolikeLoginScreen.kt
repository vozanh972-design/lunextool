package com.cayxu.app.ui.screens.golike

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.repository.GolikeAuthRepository
import com.cayxu.app.data.repository.GolikeLoginResult
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Màn đăng nhập Golike - đăng nhập THẬT bằng token Bearer (KHÔNG phải tài khoản/mật khẩu).
 *
 * Người dùng tự lấy token từ tài khoản Golike của họ (ví dụ mở app.golike.net trên trình
 * duyệt, đăng nhập, mở DevTools > Network > copy header Authorization của request bất kỳ
 * tới gateway.golike.net) rồi dán nguyên vào ô bên dưới (dán CẢ chữ "Bearer " ở đầu hay chỉ
 * riêng chuỗi JWT đều được, app tự thêm "Bearer " nếu người dùng chưa gõ).
 *
 * Bấm "Đăng nhập" sẽ gọi THẬT GET https://gateway.golike.net/api/users/me kèm token đó -
 * đúng y hệt cách app.golike.net gọi khi người dùng đăng nhập trên web. Thành công thì lưu
 * phiên lại (GolikeSession/GolikeAccountStore) và quay lại màn trước; sai/hết hạn thì báo lỗi
 * ngay dưới ô nhập, không rời màn.
 */
@Composable
fun GolikeLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("Đăng nhập Golike", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(20.dp))

            Text(
                "Dán token Bearer lấy từ tài khoản Golike của bạn (KHÔNG phải mật khẩu) để đăng nhập.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    errorMessage = null
                },
                label = { Text("Token Bearer") },
                placeholder = { Text("Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...") },
                leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                isError = errorMessage != null,
                minLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(errorMessage.orEmpty(), color = DangerRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    if (token.isBlank()) {
                        errorMessage = "Vui lòng dán token"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        when (val result = GolikeAuthRepository.fetchMe(token)) {
                            is GolikeLoginResult.Success -> {
                                GolikeSession.login(
                                    context = context,
                                    token = token.trim(),
                                    userName = result.info.name,
                                    userHandle = result.info.handle,
                                    userEmail = result.info.email,
                                    userCoin = result.info.coin,
                                    userTasksToday = result.info.tasksToday,
                                    userRewardToday = result.info.rewardToday
                                )
                                isLoading = false
                                Toast.makeText(context, "Đăng nhập Golike thành công", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                            is GolikeLoginResult.Error -> {
                                isLoading = false
                                errorMessage = result.message
                            }
                        }
                    }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text("Đăng nhập", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
