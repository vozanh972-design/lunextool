package com.cayxu.app.ui.screens.login

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.R
import com.cayxu.app.ui.theme.*
import com.cayxu.app.util.decodeText

// Toàn bộ text hiển thị trên màn Login được mã hoá XOR, chỉ giải mã lúc chạy,
// để tránh lộ nguyên văn khi ai đó decompile APK rồi tìm chuỗi tĩnh.
private val TEXT_TITLE = decodeText(25, 186, 35, 122, 2, 47)
private val TEXT_SUBTITLE = decodeText(25, 186, 35, 122, 34, 47, 122, 55, 7821, 51, 122, 52, 61, 186, 35, 122, 119, 122, 17, 51, 7909, 55, 122, 46, 51, 7835, 52, 122, 62, 7839, 122, 62, 186, 52, 61)
private val TEXT_LOGIN_HEADING = decodeText(330, 345, 52, 61, 122, 52, 50, 7927, 42, 122, 56, 7915, 52, 61, 122, 17, 63, 35)
private val TEXT_LOGIN_DESC = decodeText(20, 50, 7927, 42, 122, 49, 63, 35, 122, 57, 7869, 59, 122, 56, 7931, 52, 122, 331, 7833, 122, 331, 345, 52, 61, 122, 52, 50, 7927, 42, 122, 44, 186, 122, 41, 7863, 122, 62, 7871, 52, 61, 122, 50, 7837, 122, 46, 50, 7819, 52, 61)
private val TEXT_KEY_PLACEHOLDER = decodeText(20, 50, 7927, 42, 122, 49, 63, 35, 122, 57, 7869, 59, 122, 56, 7931, 52)
private val TEXT_SECURITY_TITLE = decodeText(17, 63, 35, 122, 57, 7869, 59, 122, 56, 7931, 52, 122, 331, 490, 7865, 57, 122, 56, 7929, 53, 122, 55, 7927, 46, 122, 46, 47, 35, 7837, 46, 122, 331, 7819, 51)
private val TEXT_SECURITY_DESC = decodeText(23, 7821, 51, 122, 49, 63, 35, 122, 57, 50, 7827, 122, 41, 7863, 122, 62, 7871, 52, 61, 122, 331, 490, 7865, 57, 122, 46, 40, 176, 52, 122, 107, 122, 46, 50, 51, 7909, 46, 122, 56, 7825, 122, 46, 7931, 51, 122, 55, 7811, 46, 122, 46, 50, 7815, 51, 122, 331, 51, 7833, 55, 116)
private val TEXT_BTN_LOGIN = decodeText(330, 345, 52, 61, 122, 52, 50, 7927, 42)
private val TEXT_NO_KEY_TITLE = decodeText(25, 50, 490, 59, 122, 57, 169, 122, 49, 63, 35, 101)
private val TEXT_NO_KEY_DESC = decodeText(23, 47, 59, 122, 49, 63, 35, 122, 331, 7833, 122, 46, 40, 7929, 51, 122, 52, 61, 50, 51, 7837, 55, 122, 331, 7933, 35, 122, 331, 7869, 122, 46, 183, 52, 50, 122, 52, 345, 52, 61, 122, 57, 7869, 59, 122, 25, 186, 35, 122, 2, 47, 116)
private val TEXT_BTN_BUY = decodeText(23, 47, 59, 122, 52, 61, 59, 35)
private val TEXT_FEAT1_TITLE = decodeText(24, 7929, 53, 122, 55, 7927, 46, 122, 46, 47, 35, 7837, 46, 122, 331, 7819, 51)
private val TEXT_FEAT1_DESC = decodeText(17, 63, 35, 122, 331, 490, 7865, 57, 122, 55, 185, 122, 50, 169, 59, 80, 44, 186, 122, 56, 7929, 53, 122, 44, 7837, 122, 104, 110, 117, 109)
private val TEXT_FEAT2_TITLE = decodeText(17, 183, 57, 50, 122, 50, 53, 7931, 46, 122, 52, 50, 59, 52, 50, 122, 57, 50, 169, 52, 61)
private val TEXT_FEAT2_DESC = decodeText(20, 50, 7927, 52, 122, 49, 63, 35, 122, 52, 61, 59, 35, 122, 41, 59, 47, 80, 49, 50, 51, 122, 46, 50, 59, 52, 50, 122, 46, 53, 187, 52)
private val TEXT_FEAT3_TITLE = decodeText(9, 7863, 122, 62, 7871, 52, 61, 122, 62, 7839, 122, 62, 186, 52, 61)
private val TEXT_FEAT3_DESC = decodeText(330, 345, 52, 61, 122, 52, 50, 7927, 42, 122, 52, 50, 59, 52, 50, 118, 80, 61, 51, 59, 53, 122, 62, 51, 7837, 52, 122, 46, 50, 184, 52, 122, 46, 50, 51, 7837, 52)
private val TEXT_FOOTER = decodeText(243, 122, 104, 106, 104, 110, 122, 25, 186, 35, 122, 2, 47, 116, 122, 27, 54, 54, 122, 40, 51, 61, 50, 46, 41, 122, 40, 63, 41, 63, 40, 44, 63, 62, 116)

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Kích thước ảnh ví tính theo % chiều rộng màn hình thật (responsive):
        // máy màn hình to -> ảnh to hơn, máy màn hình nhỏ -> ảnh nhỏ lại theo tỉ lệ,
        // có chặn min/max để không bị quá bé hoặc quá to bất hợp lý.
        val walletImageSize = (maxWidth * 0.30f).coerceIn(84.dp, 168.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
        Spacer(Modifier.height(24.dp))

        // Logo + tiêu đề, kèm hình minh hoạ ví bên phải cho sinh động hơn
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(TEXT_TITLE, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    TEXT_SUBTITLE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Image(
                painter = painterResource(R.drawable.ill_wallet_growth),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(walletImageSize)
            )
        }

        Spacer(Modifier.height(20.dp))

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
                Image(
                    painter = painterResource(R.drawable.ill_key_3d),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    TEXT_LOGIN_HEADING,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    TEXT_LOGIN_DESC,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = uiState.keyInput,
                    onValueChange = viewModel::onKeyInputChange,
                    placeholder = { Text(TEXT_KEY_PLACEHOLDER) },
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
                        Text(TEXT_SECURITY_TITLE, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(TEXT_SECURITY_DESC, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
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
                        Text(TEXT_BTN_LOGIN, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
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
                Image(
                    painter = painterResource(R.drawable.ill_key_3d),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(TEXT_NO_KEY_TITLE, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(TEXT_NO_KEY_DESC, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { /* TODO: mở link mua key */ },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(TEXT_BTN_BUY)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FeatureItem(Icons.Filled.Shield, TEXT_FEAT1_TITLE, TEXT_FEAT1_DESC, Modifier.weight(1f))
            FeatureItem(Icons.Filled.CheckCircle, TEXT_FEAT2_TITLE, TEXT_FEAT2_DESC, Modifier.weight(1f))
            FeatureItem(Icons.Filled.Autorenew, TEXT_FEAT3_TITLE, TEXT_FEAT3_DESC, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        Text(
            TEXT_FOOTER,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        }
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
