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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
 * GIAO DIỆN 2: MÀN ĐĂNG NHẬP BẰNG KEY (LOGIN SCREEN)
 * Thiết kế chuẩn phong cách Digital Banking:
 * - Header có nút Back và Logo AutoLunex
 * - Tiêu đề "Đăng nhập"
 * - CHỈ CÓ DUY NHẤT 1 Ô NHẬP: Key kích hoạt (không dùng tài khoản / mật khẩu)
 * - Checkbox "Ghi nhớ đăng nhập" & link "Mua key ngay?"
 * - Nút "Đăng nhập" Cobalt Banking
 * - Các nút tiện ích Lấy OTP / CSKH
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBackToIntro: (() -> Unit)? = null,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var rememberKey by remember { mutableStateOf(true) }
    var showOtpDialog by remember { mutableStateOf(false) }

    val cobalt600 = Color(0xFF1D4ED8)
    val textPrimary = Color(0xFF0B1730)
    val textSecondary = Color(0xFF5B6B85)
    val surface2 = Color(0xFFF7F9FB)
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
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.Start
        ) {
            // TOP BAR: Nút Back + Logo thương hiệu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (onBackToIntro != null) {
                    IconButton(
                        onClick = onBackToIntro,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(surface2)
                            .border(1.dp, borderColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "AutoLunex Logo",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AUTOLUNEX",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }

            // NỘI DUNG FORM ĐĂNG NHẬP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Đăng nhập",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Nhập key kích hoạt để tiếp tục sử dụng dịch vụ.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(32.dp))

                // LABEL VÀ Ô NHẬP KEY DUY NHẤT
                Text(
                    text = "Key kích hoạt",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface2)
                        .border(1.5.dp, if (uiState.keyInput.isNotEmpty()) cobalt600 else borderColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = if (uiState.keyInput.isNotEmpty()) cobalt600 else Color(0xFF8E9BB0),
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.keyInput.isEmpty()) {
                                Text(
                                    text = "Nhập mã key của bạn",
                                    color = Color(0xFF8E9BB0),
                                    fontSize = 14.5.sp
                                )
                            }
                            BasicTextField(
                                value = uiState.keyInput,
                                onValueChange = viewModel::onKeyInputChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = textPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(cobalt600),
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
                                tint = textSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.onKeyInputChange("") }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Dán từ bộ nhớ tạm",
                                tint = cobalt600,
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

                // HÀNG TÙY CHỌN: Ghi nhớ & Mua key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { rememberKey = !rememberKey }
                    ) {
                        Checkbox(
                            checked = rememberKey,
                            onCheckedChange = { rememberKey = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = cobalt600,
                                uncheckedColor = Color(0xFFCBD5E1),
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ghi nhớ đăng nhập",
                            fontSize = 13.sp,
                            color = textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = "Mua key ngay?",
                        fontSize = 13.sp,
                        color = cobalt600,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // NÚT ĐĂNG NHẬP CHÍNH
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
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cobalt600),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(28.dp), spotColor = cobalt600)
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

                Spacer(modifier = Modifier.height(24.dp))

                // HÀNG PHÍM TIỆN ÍCH HỖ TRỢ: LẤY OTP & CSKH
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Lấy OTP
                    OutlinedButton(
                        onClick = { showOtpDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = cobalt600,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lấy OTP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Nút CSKH
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HeadsetMic,
                            contentDescription = null,
                            tint = cobalt600,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Hỗ trợ CSKH",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // DÒNG DƯỚI CÙNG: Chưa có key? Mua key ngay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chưa có key? ",
                        fontSize = 13.5.sp,
                        color = textSecondary
                    )
                    Text(
                        text = "Mua key ngay",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = cobalt600,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            }
        }

        // HỘP THOẠI LẤY OTP
        if (showOtpDialog) {
            AlertDialog(
                onDismissRequest = { showOtpDialog = false },
                title = { Text("Lấy Mã OTP", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Mã OTP được cấp qua trang hỗ trợ AutoLunex để kích hoạt và xác minh thiết bị của bạn.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showOtpDialog = false }) {
                        Text("Đóng", color = cobalt600)
                    }
                }
            )
        }
    }
}
