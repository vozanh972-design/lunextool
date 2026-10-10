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
import androidx.compose.material.icons.outlined.VpnKey
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
import com.cayxu.app.ui.theme.*

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

    val currentLang = LanguageState.language
    val isEnglish = currentLang == AppLanguage.EN

    // Tự động chuyển tiếp nếu key đã lưu hợp lệ
    LaunchedEffect(uiState.isCheckingSavedKey) {
        if (!uiState.isCheckingSavedKey && viewModel.consumeAutoLoginSuccess()) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardWhite)
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
            Spacer(modifier = Modifier.height(20.dp))

            // TOP LOGO CHUẨN FIGMA FRAME 7
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // NỘI DUNG FORM ĐĂNG NHẬP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // TIÊU ĐỀ CHÍNH VÀ MÔ TẢ PHỤ (CHUẨN FIGMA FRAME 7)
                Text(
                    text = if (isEnglish) "Sign in" else "Đăng nhập",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isEnglish) "Glad to see you again." else "Rất vui được gặp lại bạn.",
                    fontSize = 15.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(28.dp))

                // NHÃN TRƯỜNG VÀ Ô NHẬP KEY (CHUẨN FIGMA INPUT)
                Text(
                    text = if (isEnglish) "Activation key" else "Key kích hoạt",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardWhite)
                        .border(
                            width = 1.2.dp,
                            color = if (uiState.keyInput.isNotEmpty()) Primary else BorderLight,
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
                            imageVector = Icons.Outlined.VpnKey,
                            contentDescription = null,
                            tint = if (uiState.keyInput.isNotEmpty()) Primary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.keyInput.isEmpty()) {
                                Text(
                                    text = if (isEnglish) "Enter your activation key" else "Dán mã key kích hoạt tại đây...",
                                    color = TextSecondary,
                                    fontSize = 14.5.sp
                                )
                            }
                            BasicTextField(
                                value = uiState.keyInput,
                                onValueChange = viewModel::onKeyInputChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Primary),
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
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.onKeyInputChange("") }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Paste",
                                tint = Primary,
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
                                checkedColor = Primary,
                                uncheckedColor = BorderLight,
                                checkmarkColor = Color.White
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEnglish) "Remember login" else "Ghi nhớ đăng nhập",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Text(
                        text = if (isEnglish) "Get key now?" else "Mua key ngay?",
                        fontSize = 13.sp,
                        color = Primary,
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
                        containerColor = Primary,
                        disabledContainerColor = Primary.copy(alpha = 0.6f)
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
                            fontSize = 16.sp,
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
                    HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight, thickness = 1.dp)
                    Text(
                        text = if (isEnglish) "or" else "hoặc",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight, thickness = 1.dp)
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = BorderStroke(1.dp, BorderLight),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = "Language",
                                tint = Primary,
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
                                tint = TextSecondary,
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = BorderStroke(1.dp, BorderLight),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.HeadsetMic,
                            contentDescription = null,
                            tint = Primary,
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
                        color = TextSecondary
                    )
                    Text(
                        text = if (isEnglish) "Get key now" else "Mua key ngay",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }
            }
        }

        // Overlay Bottom Sheet tự động trượt lên để recheck key ngay trên nền màn nhập key chuẩn Figma
        if (uiState.autoVerifyStatus != AutoVerifyStatus.IDLE) {
            KeyCheckBottomSheetScreen(
                status = uiState.autoVerifyStatus,
                errorMessage = uiState.errorMessage,
                isEnglish = isEnglish,
                brandBlue = Primary,
                textPrimary = TextPrimary,
                textSecondary = TextSecondary,
                onDismiss = {
                    viewModel.dismissAutoVerify()
                }
            )
        }
    }
}
