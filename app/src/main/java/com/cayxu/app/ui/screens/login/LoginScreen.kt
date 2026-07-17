package com.cayxu.app.ui.screens.login

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.ui.theme.*

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Splash check: nếu key đã lưu hợp lệ -> tự động vào Home
    LaunchedEffect(uiState.isCheckingSavedKey) {
        if (!uiState.isCheckingSavedKey && viewModel.consumeAutoLoginSuccess()) {
            onLoginSuccess()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    if (uiState.isCheckingSavedKey) {
        Box(Modifier.fillMaxSize().background(AppBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(32.dp))

        // Logo
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Primary, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Cày Xu", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cày xu mỗi ngày - Kiếm tiền dễ dàng",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(28.dp))

        // Card đăng nhập
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(InfoBlueBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null, tint = Primary)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Đăng nhập bằng Key",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Nhập key của bạn để đăng nhập và sử dụng hệ thống",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = uiState.keyInput,
                    onValueChange = viewModel::onKeyInputChange,
                    placeholder = { Text("Nhập key của bạn") },
                    leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InfoBlueBg, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Key của bạn được bảo mật tuyệt đối", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Mỗi key chỉ sử dụng được trên 1 thiết bị tại một thời điểm.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.login(onLoginSuccess) },
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Đăng nhập", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Card mua key
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = Primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Chưa có key?", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Mua key để trải nghiệm đầy đủ tính năng của Cày Xu.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { /* TODO: mở link mua key */ },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Mua ngay")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 3 icon tính năng
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FeatureItem(Icons.Filled.Shield, "Bảo mật tuyệt đối", "Key được mã hóa\nvà bảo vệ 24/7", Modifier.weight(1f))
            FeatureItem(Icons.Filled.CheckCircle, "Kích hoạt nhanh chóng", "Nhận key ngay sau\nkhi thanh toán", Modifier.weight(1f))
            FeatureItem(Icons.Filled.Autorenew, "Sử dụng dễ dàng", "Đăng nhập nhanh,\ngiao diện thân thiện", Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "© 2024 Cày Xu. All rights reserved.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(InfoBlueBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Primary)
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center, fontSize = 12.sp)
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center, fontSize = 11.sp)
    }
}
