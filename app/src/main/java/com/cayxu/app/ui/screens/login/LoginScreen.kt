package com.cayxu.app.ui.screens.login

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.R
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showGuideDialog by remember { mutableStateOf(false) }

    // Quản lý animation chuyển cảnh ACB ONE
    var isSplashPhase by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Đợi 1.5 giây ở trạng thái Splash Logo giữa màn hình rồi bắt đầu đẩy lên
        delay(1500)
        isSplashPhase = false
    }

    // Animation chuyển động logo từ chính giữa (bias = 0f) lên trên đỉnh (bias = -0.85f)
    val logoVerticalBias by animateFloatAsState(
        targetValue = if (isSplashPhase) 0f else -0.85f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "LogoVerticalBias"
    )

    val logoScale by animateFloatAsState(
        targetValue = if (isSplashPhase) 1.0f else 0.85f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "LogoScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isSplashPhase) 0f else 1f,
        animationSpec = tween(durationMillis = 800, delayMillis = 400, easing = FastOutSlowInEasing),
        label = "ContentAlpha"
    )

    // Auto-login check
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        // Nền sạch sẽ phong cảnh xanh không chứa bất kỳ nút bấm/chữ nào của ảnh mẫu
        Image(
            painter = painterResource(R.drawable.bg_autolunex_clean),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Logo AutoLunex chuyển động mượt mà từ chính giữa (Center) lên trên đỉnh (Top)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = BiasAlignment(0f, logoVerticalBias)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_autolunex_logo_clean),
                contentDescription = "AutoLunex",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.85f * logoScale)
                    .wrapContentHeight()
            )
        }

        // Nội dung giao diện bên dưới (Hiện ra sau khi logo trượt lên)
        if (!isSplashPhase) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha)
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ô Nhập Key - Thiết kế dạng Pill Glassmorphism viền sáng
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VpnKey,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.keyInput.isEmpty()) {
                                Text(
                                    text = "Nhập key",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 16.sp
                                )
                            }
                            BasicTextField(
                                value = uiState.keyInput,
                                onValueChange = viewModel::onKeyInputChange,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color.White),
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

                        // Nút Dán / Xóa nhanh
                        if (uiState.keyInput.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Xóa",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.onKeyInputChange("") }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.ContentPaste,
                                contentDescription = "Dán",
                                tint = Color.White.copy(alpha = 0.9f),
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

                // Nút KÍCH HOẠT / MUA KEY
                val hasKey = uiState.keyInput.isNotBlank()
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (hasKey) {
                            viewModel.login(onLoginSuccess)
                        } else {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://lunex.io.vn/")
                            )
                            runCatching { context.startActivity(intent) }
                        }
                    },
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasKey) Color(0xFF0052CC) else Color(0xFF0077FF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp), spotColor = Color(0xFF0044BB))
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (!hasKey) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MUA KEY",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "KÍCH HOẠT NGAY",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Hàng 2 phím tắt dưới cùng: "Lấy OTP" & "CSKH"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showGuideDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lấy OTP",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://lunex.io.vn/")
                                )
                                runCatching { context.startActivity(intent) }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HeadsetMic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CSKH",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Bảng Hướng dẫn kích hoạt
        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = { Text("Hướng Dẫn Kích Hoạt AutoLunex", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("1. Mua key kích hoạt tại trang web lunex.io.vn.", fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Text("2. Dán key bạn nhận được vào ô \"Nhập key\".", fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Text("3. Bấm \"Kích hoạt ngay\" để bắt đầu sử dụng.", fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                        Text("4. Mỗi key dùng riêng cho 1 thiết bị.", fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) {
                        Text("Đã hiểu")
                    }
                }
            )
        }
    }
}
