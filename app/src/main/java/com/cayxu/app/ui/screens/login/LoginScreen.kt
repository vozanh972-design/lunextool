package com.cayxu.app.ui.screens.login

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.ui.theme.*

/**
 * MÀN HÌNH ĐĂNG NHẬP — CHUẨN FIGMA 100% (SCREEN 07)
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Tự động chuyển tiếp nếu session/key đã lưu hợp lệ
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // 1. LOGO TRÊN GÓC TRÁI (CHUẨN FIGMA SCREEN 07)
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. TIÊU ĐỀ: ĐĂNG NHẬP & PHỤ ĐỀ
            Text(
                text = "Đăng nhập",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Rất vui được gặp lại bạn.",
                fontSize = 15.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3. TRƯỜNG EMAIL
            Text(
                text = "Email",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (uiState.keyInput.isEmpty()) {
                            Text(
                                text = "minhanh@email.com",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = uiState.keyInput,
                            onValueChange = viewModel::onKeyInputChange,
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(Primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. TRƯỜNG MẬT KHẨU
            Text(
                text = "Mật khẩu",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (passwordInput.isEmpty()) {
                            Text(
                                text = "••••••••••",
                                color = TextSecondary.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        }
                        BasicTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            textStyle = TextStyle(
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
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

                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = "Toggle password",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. QUÊN MẬT KHẨU?
            Text(
                text = "Quên mật khẩu?",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Primary,
                modifier = Modifier.clickable {
                    Toast.makeText(context, "Chức năng khôi phục mật khẩu đang cập nhật", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 6. NÚT ĐĂNG NHẬP
            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (uiState.keyInput.isNotBlank()) {
                        viewModel.login(onLoginSuccess)
                    } else {
                        Toast.makeText(context, "Vui lòng nhập tài khoản / email", Toast.LENGTH_SHORT).show()
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
                        text = "Đăng nhập",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 7. THANH PHÂN CÁCH "HOẶC"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight, thickness = 1.dp)
                Text(
                    text = "hoặc",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight, thickness = 1.dp)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 8. BA NÚT ĐĂNG NHẬP MẠNG XÃ HỘI (APPLE, GOOGLE, FACEBOOK)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Nút Apple
                SocialRoundButton(
                    symbol = "",
                    symbolColor = TextPrimary,
                    onClick = { Toast.makeText(context, "Đăng nhập Apple", Toast.LENGTH_SHORT).show() }
                )

                Spacer(modifier = Modifier.width(18.dp))

                // Nút Google
                SocialRoundButton(
                    symbol = "G",
                    symbolColor = Color(0xFF4285F4),
                    onClick = { Toast.makeText(context, "Đăng nhập Google", Toast.LENGTH_SHORT).show() }
                )

                Spacer(modifier = Modifier.width(18.dp))

                // Nút Facebook
                SocialRoundButton(
                    symbol = "f",
                    symbolColor = Color(0xFF1877F2),
                    onClick = { Toast.makeText(context, "Đăng nhập Facebook", Toast.LENGTH_SHORT).show() }
                )
            }

            Spacer(modifier = Modifier.height(36.dp))
        }

        // Overlay Bottom Sheet recheck key ngầm (giữ logic kiểm tra nguyên bản)
        if (uiState.autoVerifyStatus != AutoVerifyStatus.IDLE) {
            KeyCheckBottomSheetScreen(
                status = uiState.autoVerifyStatus,
                errorMessage = uiState.errorMessage,
                isEnglish = false,
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

@Composable
private fun SocialRoundButton(
    symbol: String,
    symbolColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = CardWhite,
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = symbolColor
            )
        }
    }
}
