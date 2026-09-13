package com.cayxu.app.ui.screens.login

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.R

/**
 * GIAO DIỆN 3: MÀN ĐĂNG NHẬP / NHẬP KEY (LOGIN SCREEN)
 * Thiết kế chuẩn theo mẫu tông cam ấm AutoLunex:
 * - Logo AutoLunex ở trên cùng
 * - Tiêu đề "Đăng nhập vào tài khoản"
 * - Ô nhập Key viền bo góc kèm icon Khóa/Key và nút Dán/Xóa
 * - Hàng tùy chọn "Nhớ tài khoản" & "Mua key"
 * - Nút "Đăng nhập" màu cam nổi bật
 * - Các nút hỗ trợ CSKH và Lấy OTP
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var rememberAccount by remember { mutableStateOf(true) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }

    val brandOrange = Color(0xFFFF5E1E)
    val textDark = Color(0xFF1E293B)
    val textMuted = Color(0xFF64748B)
    val borderColor = Color(0xFFE2E8F0)

    // Tự động chuyển tiếp nếu key đã lưu hợp lệ
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFBF9))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Logo AutoLunex tông cam ấm trên cùng
            Image(
                painter = painterResource(R.drawable.ic_autolunex_warm_logo),
                contentDescription = "AutoLunex",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(54.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Tiêu đề & mô tả
            Text(
                text = "Đăng nhập vào tài khoản",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = textDark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nhập key kích hoạt để bắt đầu kiếm xu tự động",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = textMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Ô NHẬP KEY (Password/Key Input Field)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon Khóa / Key
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    // TextField nhập key
                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.keyInput.isEmpty()) {
                            Text(
                                text = "Nhập key kích hoạt",
                                color = Color(0xFF94A3B8),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = uiState.keyInput,
                            onValueChange = viewModel::onKeyInputChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = textDark,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(brandOrange),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (uiState.keyInput.isNotBlank()) {
                                    viewModel.login(onLoginSuccess)
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Nút Dán hoặc Xóa nhanh
                    if (uiState.keyInput.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Xóa",
                            tint = textMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { viewModel.onKeyInputChange("") }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "Dán từ bộ nhớ tạm",
                            tint = brandOrange,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString().orEmpty().trim()
                                        if (text.isNotEmpty()) {
                                            viewModel.onKeyInputChange(text)
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hàng tùy chọn: "Nhớ tài khoản" & "Mua key"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { rememberAccount = !rememberAccount }
                ) {
                    Checkbox(
                        checked = rememberAccount,
                        onCheckedChange = { rememberAccount = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = brandOrange,
                            uncheckedColor = Color(0xFFCBD5E1),
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nhớ tài khoản",
                        fontSize = 13.sp,
                        color = textMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Mua key ngay?",
                    fontSize = 13.sp,
                    color = brandOrange,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                        runCatching { context.startActivity(intent) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Nút "ĐĂNG NHẬP" (Kích hoạt)
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (uiState.keyInput.isNotBlank()) {
                        viewModel.login(onLoginSuccess)
                    } else {
                        Toast.makeText(context, "Vui lòng nhập key kích hoạt", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandOrange),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(26.dp), spotColor = brandOrange)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "Đăng nhập",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Hàng 2 phím chức năng dưới cùng: "Lấy OTP" & "CSKH"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút Lấy OTP
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable { showOtpDialog = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = textDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lấy OTP",
                        color = textDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Nút CSKH
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.HeadsetMic,
                        contentDescription = null,
                        tint = textDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CSKH",
                        color = textDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Hộp thoại Lấy OTP
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { showOtpDialog = false },
                title = { Text("Lấy Mã OTP", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Mã OTP được gửi qua hệ thống hỗ trợ AutoLunex để xác thực tài khoản của bạn.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showOtpDialog = false }) {
                        Text("Đóng", color = brandOrange)
                    }
                }
            )
        }
    }
}
