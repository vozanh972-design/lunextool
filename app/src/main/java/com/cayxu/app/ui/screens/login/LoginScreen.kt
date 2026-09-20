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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
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
import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState

/**
 * GIAO DIỆN 2: MÀN ĐĂNG NHẬP BẰNG KEY (LOGIN SCREEN)
 * - Không có nút quay lại (Back button)
 * - Top Bar hiển thị Logo thương hiệu AutoLunex mới
 * - CHỈ CÓ 1 Ô NHẬP: Key kích hoạt
 * - Chỗ "Lấy OTP" được thay thế bằng nút chuyển đổi Ngôn ngữ (Mặc định: English)
 * - Nút Hỗ trợ CSKH và link Mua key
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var rememberKey by remember { mutableStateOf(true) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    val cobalt600 = Color(0xFF1D4ED8)
    val textPrimary = Color(0xFF0B1730)
    val textSecondary = Color(0xFF5B6B85)
    val surface2 = Color(0xFFF7F9FB)
    val borderColor = Color(0xFFE2E8F0)

    val currentLang = LanguageState.language
    val isEnglish = currentLang == AppLanguage.EN

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

    // Nếu đang tự động kiểm tra key khi mở app -> hiển thị màn hình animated loading/success/error
    if (uiState.autoVerifyStatus != AutoVerifyStatus.IDLE) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // Logo ứng dụng
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "AutoLunex Logo",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "AUTOLUNEX",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = textPrimary
                )

                Spacer(Modifier.height(48.dp))

                // Hiệu ứng animated loading / checkmark / cross
                AnimatedContent(
                    targetState = uiState.autoVerifyStatus,
                    label = "VerifyStatusAnim"
                ) { status ->
                    when (status) {
                        AutoVerifyStatus.CHECKING -> {
                            Box(
                                modifier = Modifier.size(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = cobalt600,
                                    strokeWidth = 3.5.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        AutoVerifyStatus.SUCCESS -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Success",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        AutoVerifyStatus.ERROR -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Error",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        AutoVerifyStatus.IDLE -> {}
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Tiêu đề & mô tả trạng thái mượt mà
                val titleText = when (uiState.autoVerifyStatus) {
                    AutoVerifyStatus.CHECKING -> if (isEnglish) "Verifying activation key..." else "Đang kiểm tra key kích hoạt..."
                    AutoVerifyStatus.SUCCESS -> if (isEnglish) "Key verified successfully!" else "Đã xác minh key thành công!"
                    AutoVerifyStatus.ERROR -> if (isEnglish) "Verification failed" else "Xác minh key thất bại"
                    AutoVerifyStatus.IDLE -> ""
                }
                val subtitleText = when (uiState.autoVerifyStatus) {
                    AutoVerifyStatus.CHECKING -> if (isEnglish) "Please wait a moment" else "Vui lòng chờ trong giây lát"
                    AutoVerifyStatus.SUCCESS -> if (isEnglish) "Entering application..." else "Đang vào ứng dụng..."
                    AutoVerifyStatus.ERROR -> uiState.errorMessage ?: (if (isEnglish) "Invalid or expired key" else "Key không hợp lệ hoặc đã hết hạn")
                    AutoVerifyStatus.IDLE -> ""
                }

                Text(
                    text = titleText,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (uiState.autoVerifyStatus) {
                        AutoVerifyStatus.SUCCESS -> Color(0xFF10B981)
                        AutoVerifyStatus.ERROR -> Color(0xFFEF4444)
                        else -> textPrimary
                    },
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitleText,
                    fontSize = 13.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
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
            // TOP BAR: Logo & Tên thương hiệu (KHÔNG CÓ MŨI TÊN QUAY LẠI)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = "AutoLunex Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AUTOLUNEX",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
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
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isEnglish) "Sign in" else "Đăng nhập",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isEnglish) "Enter your activation key to continue using the service." else "Nhập key kích hoạt để tiếp tục sử dụng dịch vụ.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(30.dp))

                // LABEL VÀ Ô NHẬP KEY DUY NHẤT
                Text(
                    text = if (isEnglish) "Activation key" else "Key kích hoạt",
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
                                    text = if (isEnglish) "Enter your key" else "Nhập mã key của bạn",
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
                                contentDescription = "Clear",
                                tint = textSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.onKeyInputChange("") }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Paste",
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
                            text = if (isEnglish) "Remember key" else "Ghi nhớ đăng nhập",
                            fontSize = 13.sp,
                            color = textSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = if (isEnglish) "Get key now?" else "Mua key ngay?",
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
                            val msg = if (isEnglish) "Please enter your activation key" else "Vui lòng nhập key kích hoạt"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                            text = if (isEnglish) "Sign in" else "Đăng nhập",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // HÀNG PHÍM TIỆN ÍCH: CHỌN NGÔN NGỮ (MẶC ĐỊNH ENGLISH) & CSKH
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // NÚT CHỌN NGÔN NGỮ (THAY THẾ LẤY OTP)
                    Box {
                        OutlinedButton(
                            onClick = { languageMenuExpanded = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = "Language",
                                tint = cobalt600,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "English" else "Tiếng Việt",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("English") },
                                onClick = {
                                    LanguageState.setLanguage(context, AppLanguage.EN)
                                    languageMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tiếng Việt") },
                                onClick = {
                                    LanguageState.setLanguage(context, AppLanguage.VI)
                                    languageMenuExpanded = false
                                }
                            )
                        }
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
                            text = if (isEnglish) "Support" else "Hỗ trợ CSKH",
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
                        text = if (isEnglish) "Don't have a key? " else "Chưa có key? ",
                        fontSize = 13.5.sp,
                        color = textSecondary
                    )
                    Text(
                        text = if (isEnglish) "Get key now" else "Mua key ngay",
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
    }
}
