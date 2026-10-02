package com.cayxu.app.ui.screens.login

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
 * GIAO DIỆN ĐĂNG NHẬP BẰNG KEY (LOGIN SCREEN)
 * Chuẩn phong cách thiết kế Figma Nexa Minimalist:
 * - Nền trắng tinh khôi #FFFFFF
 * - Màu chủ đạo Apple Blue #0A84FF & Chữ than đậm #1C1C1E
 * - Thay thế hoàn toàn form tài khoản/mật khẩu bằng Ô NHẬP KEY KÍCH HOẠT duy nhất
 * - Hỗ trợ nút dán nhanh từ clipboard và nút xóa nhanh
 * - Giữ nguyên 100% chức năng xác thực key, ghi nhớ, đổi ngôn ngữ và hỗ trợ CSKH
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

    val bgWhite = Color(0xFFFFFFFF)
    val brandBlue = Color(0xFF0A84FF)
    val textPrimary = Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF8E8E93)
    val borderColor = Color(0xFFE5E5EA)
    val inputBg = Color(0xFFFFFFFF)

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
                .background(bgWhite),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // Logo ứng dụng phong cách Figma
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(brandBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = "LunexTool Logo",
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "LUNEXTOOL",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = textPrimary
                )

                Spacer(Modifier.height(44.dp))

                // Hiệu ứng animated loading / checkmark / cross
                AnimatedContent(
                    targetState = uiState.autoVerifyStatus,
                    label = "VerifyStatusAnim"
                ) { status ->
                    when (status) {
                        AutoVerifyStatus.CHECKING -> {
                            Box(
                                modifier = Modifier.size(60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = brandBlue,
                                    strokeWidth = 3.5.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        AutoVerifyStatus.SUCCESS -> {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34C759)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Success",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                        AutoVerifyStatus.ERROR -> {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3B30).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3B30)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Error",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                        AutoVerifyStatus.IDLE -> {}
                    }
                }

                Spacer(Modifier.height(18.dp))

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
                        AutoVerifyStatus.SUCCESS -> Color(0xFF34C759)
                        AutoVerifyStatus.ERROR -> Color(0xFFFF3B30)
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
            .background(bgWhite)
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            // TOP BAR: Logo & Tên thương hiệu phong cách Figma Nexa
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(brandBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_logo),
                        contentDescription = "LunexTool Logo",
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "LUNEXTOOL",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = textPrimary
                )
            }

            // NỘI DUNG FORM ĐĂNG NHẬP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // TIÊU ĐỀ CHÍNH VÀ MÔ TẢ PHỤ (CHUẨN FIGMA)
                Text(
                    text = if (isEnglish) "Welcome back" else "Chào mừng trở lại",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isEnglish) "Enter your activation key to continue your tasks." else "Đăng nhập bằng key để tiếp tục nhiệm vụ và theo dõi điểm của bạn.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = textSecondary
                )

                Spacer(modifier = Modifier.height(28.dp))

                // NHÃN TRƯỜNG VÀ Ô NHẬP KEY (CHUẨN FIGMA INPUT)
                Text(
                    text = if (isEnglish) "Activation key" else "Key kích hoạt",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(inputBg)
                        .border(
                            width = 1.2.dp,
                            color = if (uiState.keyInput.isNotEmpty()) brandBlue else borderColor,
                            shape = RoundedCornerShape(14.dp)
                        )
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
                            tint = if (uiState.keyInput.isNotEmpty()) brandBlue else textSecondary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.keyInput.isEmpty()) {
                                Text(
                                    text = if (isEnglish) "Enter your key" else "Nhập mã key kích hoạt",
                                    color = textSecondary,
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
                                cursorBrush = SolidColor(brandBlue),
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

                        // Nút Dán nhanh hoặc Xóa
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
                                tint = brandBlue,
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

                // HÀNG TÙY CHỌN: Ghi nhớ đăng nhập & Mua key ngay (chuẩn Figma)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { rememberKey = !rememberKey }
                    ) {
                        Checkbox(
                            checked = rememberKey,
                            onCheckedChange = { rememberKey = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = brandBlue,
                                uncheckedColor = borderColor,
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Remember login" else "Ghi nhớ đăng nhập",
                            fontSize = 13.sp,
                            color = textPrimary,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Text(
                        text = if (isEnglish) "Get key now?" else "Mua key ngay?",
                        fontSize = 13.sp,
                        color = brandBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // NÚT ĐĂNG NHẬP CHÍNH (CHUẨN FIGMA)
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
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandBlue,
                        disabledContainerColor = brandBlue.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (isEnglish) "Sign in" else "Đăng nhập",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // THANH PHÂN CÁCH "HOẶC" (CHUẨN FIGMA)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = borderColor, thickness = 0.8.dp)
                    Text(
                        text = if (isEnglish) "or utilities" else "hoặc",
                        fontSize = 12.5.sp,
                        color = textSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = borderColor, thickness = 0.8.dp)
                }

                Spacer(modifier = Modifier.height(22.dp))

                // HÀNG PHÍM TIỆN ÍCH: CHỌN NGÔN NGỮ & HỖ TRỢ CSKH (CHUẨN FIGMA BUTTONS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút chọn ngôn ngữ
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { languageMenuExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = "Language",
                                tint = brandBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEnglish) "English" else "Tiếng Việt",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(2.dp))
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

                    // Nút Hỗ trợ CSKH
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(borderColor)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HeadsetMic,
                            contentDescription = null,
                            tint = brandBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Support" else "Hỗ trợ CSKH",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // DÒNG DƯỚI CÙNG (FOOTER PROMPT CHUẨN FIGMA)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
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
                        color = brandBlue,
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
