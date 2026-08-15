package com.cayxu.app.ui.screens.xsmm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
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
import com.cayxu.app.data.repository.XsmmAuthRepository
import com.cayxu.app.data.repository.XsmmLoginResult
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.launch

private val XsmmAccent = Color(0xFF16A34A)

/**
 * Màn đăng nhập XSMM - dán access token (lấy từ web xsmm.net) vào 1 ô nhập, bấm "Đăng nhập"
 * sẽ gọi GET /api/taskapi/user để xác nhận token hợp lệ + lấy username/points, rồi lưu phiên
 * và chuyển sang màn tài khoản (XsmmAccountScreen).
 */
@Composable
fun XsmmLoginScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tokenInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (tokenInput.isBlank()) {
            errorMessage = "Vui lòng nhập token"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            when (val result = XsmmAuthRepository.fetchUser(tokenInput)) {
                is XsmmLoginResult.Success -> {
                    XsmmSession.login(context, tokenInput.trim(), result.info.username, result.info.points)
                    isLoading = false
                    navController.navigate(Routes.XSMM_ACCOUNT) {
                        popUpTo(Routes.XSMM_LOGIN) { inclusive = true }
                    }
                }
                is XsmmLoginResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("Đăng nhập XSMM", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = XsmmAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Access Token", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Lấy token trong tài khoản trên xsmm.net rồi dán vào đây",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it; errorMessage = null },
                        placeholder = { Text("Dán token vào đây...") },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = XsmmAccent,
                            cursorColor = XsmmAccent
                        )
                    )

                    if (errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMessage.orEmpty(), color = DangerRed, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { submit() },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = XsmmAccent),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text("Đăng nhập", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
