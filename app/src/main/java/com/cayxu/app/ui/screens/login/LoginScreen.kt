package com.cayxu.app.ui.screens.login

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.ui.theme.*

/**
 * MÀN HÌNH ĐĂNG NHẬP BẰNG KEY BẢN QUYỀN (LICENSE KEY LOGIN)
 * Chuẩn thiết kế Xếp lớp Bất đối xứng (Asymmetrical 3D Overlap):
 * - Tiêu đề phân cấp nhịp nhàng chuẩn Facebook.
 * - Tổ hợp thẻ tác vụ xếp lớp đan cài bất đối xứng có chiều sâu quang học.
 * - Cụm nhập key và xác thực Bottom Sheet Apple NFC 2 giai đoạn.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

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
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // 1. BRAND HEADER GỌN GÀNG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AL",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "AUTOLUNEX",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = TextPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Primary.copy(alpha = 0.18f))
                    ) {
                        Text(
                            text = "Bản Quyền 2026",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2. TIÊU ĐỀ PHÂN CẤP CHỮ
                Text(
                    text = buildAnnotatedString {
                        append("Tự động hóa\n")
                        withStyle(SpanStyle(color = Primary)) {
                            append("tác vụ bạn cần.")
                        }
                    },
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Nhập mã License Key để mở khóa toàn bộ tính năng cày xu đa kênh.",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. TỔ HỢP TÁC VỤ XẾP LỚP BẤT ĐỐI XỨNG ĐA TẦNG (ASYMMETRICAL 3D OVERLAP)
                AsymmetricalTaskLayeredCluster()

                Spacer(modifier = Modifier.height(20.dp))

                // 4. KHU VỰC NHẬP MÃ BẢN QUYỀN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mã kích hoạt (License Key)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Nút Dán nhanh
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val clipText = clipboardManager.getText()?.text?.trim()
                                if (!clipText.isNullOrBlank()) {
                                    viewModel.onKeyInputChange(clipText)
                                    Toast.makeText(context, "Đã dán key từ bộ nhớ tạm", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Bộ nhớ tạm không có nội dung", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = "Dán",
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Dán key",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ô nhập key
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AppBackground,
                    border = BorderStroke(1.2.dp, if (uiState.errorMessage != null && uiState.autoVerifyStatus == AutoVerifyStatus.IDLE) DangerRed else BorderLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VpnKey,
                            contentDescription = null,
                            tint = if (uiState.keyInput.isNotBlank()) Primary else TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.keyInput.isEmpty()) {
                                Text(
                                    text = "Dán hoặc nhập mã key tại đây...",
                                    fontSize = 13.5.sp,
                                    color = TextSecondary.copy(alpha = 0.6f)
                                )
                            }
                            BasicTextField(
                                value = uiState.keyInput,
                                onValueChange = { viewModel.onKeyInputChange(it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                cursorBrush = SolidColor(Primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    focusManager.clearFocus()
                                    viewModel.login(onLoginSuccess)
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.keyInput.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onKeyInputChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Xóa",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                if (uiState.errorMessage != null && uiState.autoVerifyStatus == AutoVerifyStatus.IDLE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DangerRed.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ ${uiState.errorMessage}",
                            fontSize = 12.5.sp,
                            color = DangerRed,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Nút Xác thực & Kích hoạt
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        if (uiState.keyInput.isNotBlank()) {
                            viewModel.login(onLoginSuccess)
                        } else {
                            Toast.makeText(context, "Vui lòng nhập hoặc dán mã key", Toast.LENGTH_SHORT).show()
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
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Xác thực & Kích hoạt",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 5. CHÂN TRANG: LINK MUA KEY & HỖ TRỢ
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Bạn chưa có key? ",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Mua ngay",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Primary,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lunex.io.vn/"))
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/lunexsupport"))
                            runCatching { context.startActivity(intent) }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HeadsetMic,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Hỗ trợ kỹ thuật 24/7",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Overlay Bottom Sheet xác thực key (Apple NFC style 2 giai đoạn)
        if (uiState.autoVerifyStatus != AutoVerifyStatus.IDLE) {
            KeyCheckBottomSheetScreen(
                status = uiState.autoVerifyStatus,
                errorMessage = uiState.errorMessage,
                isEnglish = false,
                brandBlue = Primary,
                textPrimary = TextPrimary,
                textSecondary = TextSecondary,
                onDismiss = { viewModel.dismissAutoVerify() }
            )
        }
    }
}

/**
 * Khối tác vụ hệ sinh thái xếp lớp bất đối xứng đa tầng (Asymmetrical 3D Overlap):
 * - Thẻ 1 (Góc trên trái, z = 1): Facebook Reels lùi nhẹ (-4 độ).
 * - Thẻ 2 (Phía bên phải, z = 2): TikTok Live Auto dạng story đứng cao (+3 độ).
 * - Thẻ 3 (Góc dưới trái, z = 3): Thẻ Thống kê ví xu gối chéo lên đáy của cả 2 thẻ (-1 độ).
 * - Không có icon ngôi sao, không có icon robot, đổ bóng và viền hài hòa với nền trắng.
 */
@Composable
private fun AsymmetricalTaskLayeredCluster() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(235.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFF8FAFC).copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Hào quang mờ êm dịu hòa vào nền
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFDBEAFE).copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )

        // 📌 THẺ 1: FACEBOOK REELS (Góc trên trái - Phía sau, nghiêng -4 độ)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = 6.dp)
                .width(150.dp)
                .rotate(-4f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "f",
                            color = Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SuccessGreen.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "+350 Xu",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Facebook Reels",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen)
                    )
                    Text(
                        text = "Đang chạy ngầm",
                        fontSize = 9.5.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF1F5F9))
                )
            }
        }

        // 📌 THẺ 2: TIKTOK LIVE DẠNG STORY (Bên phải - Tiền cảnh, đứng cao, nghiêng +3 độ)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 2.dp)
                .width(170.dp)
                .rotate(3f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFF1F5F9))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF43F5E))
                        )
                        Text(
                            text = "TikTok LIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "16:45",
                        fontSize = 9.5.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Inner Video action preview box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE0E7FF).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#TK-8924",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                            Text(text = "❤️", fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Auto Thả tim & Follow",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "+250 Xu / nhiệm vụ",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(Primary)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Primary))
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color(0xFFCBD5E1)))
                        Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color(0xFFCBD5E1)))
                    }
                }
            }
        }

        // 📌 THẺ 3: THỐNG KÊ VÍ XU TỔNG THỂ (Góc dưới trái - Gối chéo lên chân cả 2 thẻ, nghiêng -1 độ)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 16.dp, y = (-4).dp)
                .width(175.dp)
                .rotate(-1f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Primary)
                        )
                        Text(
                            text = "Ví Xu Auto",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Primary.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = "24/7",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Hôm nay",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "+168,500",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SuccessGreen.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "342 nhiệm vụ ↗",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Thanh tiến độ mượt mà
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF1F5F9))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Primary, Color(0xFF6366F1))
                                )
                            )
                    )
                }
            }
        }
    }
}
