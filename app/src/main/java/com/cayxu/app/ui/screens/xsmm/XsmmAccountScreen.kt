package com.cayxu.app.ui.screens.xsmm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cayxu.app.data.local.XsmmAccountStore
import com.cayxu.app.data.repository.XsmmAuthRepository
import com.cayxu.app.data.repository.XsmmLoginResult
import com.cayxu.app.ui.navigation.Routes
import com.cayxu.app.ui.theme.*
import kotlinx.coroutines.launch

private val XsmmAccentStart = Color(0xFF34D399)
private val XsmmAccentEnd = Color(0xFF16A34A)

/** Màn tài khoản XSMM - hiện username + số dư (points) dạng thẻ gradient, có nút làm mới +
 *  đăng xuất, giống bố cục màn tài khoản GoLike trước đây. */
@Composable
fun XsmmAccountScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val username by XsmmSession.username
    val points by XsmmSession.points
    var isRefreshing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
            }
            Spacer(Modifier.width(6.dp))
            Text("XSMM", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    isRefreshing = true
                    val token = XsmmAccountStore.getToken(context)
                    if (token.isNullOrBlank()) {
                        isRefreshing = false
                    } else {
                        scope.launch {
                            when (val result = XsmmAuthRepository.fetchUser(token)) {
                                is XsmmLoginResult.Success -> {
                                    XsmmSession.login(context, token, result.info.username, result.info.points)
                                }
                                is XsmmLoginResult.Error -> Unit
                            }
                            isRefreshing = false
                        }
                    }
                },
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(color = XsmmAccentEnd, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Làm mới", tint = XsmmAccentEnd)
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(XsmmAccentStart, XsmmAccentEnd)))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                username.ifBlank { "Đang tải..." },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Số dư", color = Color(0xFFDCFCE7), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$points", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text("điểm", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = {
                    XsmmSession.logout(context)
                    navController.navigate(Routes.XSMM_LOGIN) {
                        popUpTo(Routes.XSMM_ACCOUNT) { inclusive = true }
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Đăng xuất")
            }
        }
    }
}
