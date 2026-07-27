package com.cayxu.app.ui.screens.login

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cayxu.app.R
import com.cayxu.app.data.local.SecurePrefs
import com.cayxu.app.ui.theme.*
import com.cayxu.app.util.decodeText

// Toàn bộ text hiển thị trên màn nhập Key được mã hoá XOR, chỉ giải mã lúc chạy,
// để tránh lộ nguyên văn khi ai đó decompile APK rồi tìm chuỗi tĩnh.
private val TEXT_LOGIN_HEADING_1 = decodeText(20, 50, 7927, 42, 122, 17, 63, 35)
private val TEXT_LOGIN_HEADING_2 = decodeText(49, 183, 57, 50, 122, 50, 53, 7931, 46)
private val TEXT_LOGIN_DESC = decodeText(20, 50, 7927, 42, 122, 49, 63, 35, 122, 56, 7931, 52, 122, 331, 185, 122, 55, 47, 59, 122, 331, 7833, 122, 49, 183, 57, 50, 122, 50, 53, 7931, 46, 122, 46, 186, 51, 122, 49, 50, 53, 7929, 52, 122, 44, 186, 122, 41, 7863, 122, 62, 7871, 52, 61, 122, 57, 187, 57, 122, 46, 183, 52, 50, 122, 52, 345, 52, 61, 122, 57, 59, 53, 122, 57, 7935, 42, 116)
private val TEXT_KEY_LABEL = decodeText(17, 63, 35, 122, 57, 7869, 59, 122, 56, 7931, 52)
private val TEXT_KEY_PLACEHOLDER = decodeText(20, 50, 7927, 42, 122, 49, 63, 35, 122, 46, 7931, 51, 122, 331, 184, 35)
private val TEXT_BTN_ACTIVATE = decodeText(17, 183, 57, 50, 122, 50, 53, 7931, 46, 122, 52, 61, 59, 35)
private val TEXT_OR = decodeText(18, 53, 7917, 57)
private val TEXT_BUY_KEY_TITLE = "Chưa có key?"
private val TEXT_BUY_KEY_DESC = "Bấm để mua key kích hoạt mới"
private val TEXT_NOTE_TITLE = decodeText(22, 490, 47, 122, 167, 122, 43, 47, 59, 52, 122, 46, 40, 7831, 52, 61)
private val TEXT_NOTE_1 = decodeText(23, 7821, 51, 122, 49, 63, 35, 122, 57, 50, 7827, 122, 62, 163, 52, 61, 122, 331, 7833, 122, 49, 183, 57, 50, 122, 50, 53, 7931, 46, 122, 107, 122, 46, 186, 51, 122, 49, 50, 53, 7929, 52, 116)
private val TEXT_NOTE_2 = decodeText(17, 50, 174, 52, 61, 122, 57, 50, 51, 59, 122, 41, 7905, 122, 49, 63, 35, 122, 331, 7833, 122, 46, 40, 187, 52, 50, 122, 56, 7825, 122, 49, 50, 53, 187, 116)
private val TEXT_NOTE_3 = decodeText(22, 51, 176, 52, 122, 50, 7837, 122, 50, 7821, 122, 46, 40, 7865, 122, 52, 7909, 47, 122, 61, 7917, 42, 122, 44, 7935, 52, 122, 331, 7835, 122, 49, 50, 51, 122, 49, 183, 57, 50, 122, 50, 53, 7931, 46, 116)

// Nội dung bảng "Hướng dẫn" khi bấm nút góc trên phải màn Nhập Key.
private val TEXT_GUIDE_TITLE = "Hướng dẫn kích hoạt"
// STEP_1 và STEP_5 có nhắc tới domain server nên cũng được mã hoá XOR như các
// TEXT_* khác ở trên, tránh grep/jadx thấy domain lộ ra ở dạng chữ trực tiếp.
private val TEXT_GUIDE_STEP_1 = decodeText(
    107, 116, 122, 23, 47, 59, 122, 49, 63, 35, 122, 49, 183, 57, 50, 122, 50, 53, 7931, 46,
    122, 46, 7931, 51, 122, 54, 47, 52, 63, 34, 116, 51, 53, 116, 44, 52, 122, 114, 56, 7935,
    55, 122, 120, 25, 50, 490, 59, 122, 57, 169, 122, 49, 63, 35, 101, 120, 122, 56, 176, 52,
    122, 62, 490, 7809, 51, 122, 174, 122, 52, 50, 7927, 42, 122, 49, 63, 35, 115, 116
)
private val TEXT_GUIDE_STEP_2 = "2. Dán key bạn nhận được vào ô \"Key của bạn\"."
private val TEXT_GUIDE_STEP_3 = "3. Bấm \"Kích hoạt ngay\" để xác thực và bắt đầu sử dụng ứng dụng."
private val TEXT_GUIDE_STEP_4 = "4. Mỗi key chỉ dùng để kích hoạt 1 tài khoản/thiết bị - không chia sẻ key cho người khác để tránh bị khoá."
private val TEXT_GUIDE_STEP_5 = decodeText(
    111, 116, 122, 29, 7917, 42, 122, 44, 7935, 52, 122, 331, 7835, 122, 49, 50, 51, 122, 49,
    183, 57, 50, 122, 50, 53, 7931, 46, 101, 122, 22, 51, 176, 52, 122, 50, 7837, 122, 50, 7821,
    122, 46, 40, 7865, 122, 43, 47, 59, 122, 54, 47, 52, 63, 34, 116, 51, 53, 116, 44, 52, 122,
    331, 7833, 122, 331, 490, 7865, 57, 122, 46, 40, 7865, 122, 61, 51, 160, 42, 116
)

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showGuideDialog by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            // Không có nơi nào để "quay lại" trước màn này khi chưa đăng nhập -> icon back
            // chỉ mang tính thị giác cho khớp ảnh mẫu, bấm vào không làm gì (đã ở đầu luồng).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = TextPrimary)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardWhite)
                        .clickable { showGuideDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Hướng dẫn", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(TEXT_LOGIN_HEADING_1, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, lineHeight = 32.sp)
                    Text(TEXT_LOGIN_HEADING_2, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Primary, lineHeight = 32.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(TEXT_LOGIN_DESC, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                }
                Image(
                    painter = painterResource(R.drawable.ic_mascot_coin),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VpnKey, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(TEXT_KEY_LABEL, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = uiState.keyInput,
                        onValueChange = viewModel::onKeyInputChange,
                        placeholder = { Text(TEXT_KEY_PLACEHOLDER) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.login(onLoginSuccess) },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Text(TEXT_BTN_ACTIVATE, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.2f))
                        Text("  $TEXT_OR  ", color = TextSecondary, fontSize = 12.sp)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.2f))
                    }
                    Spacer(Modifier.height(12.dp))

                    // "Chưa có key? Mua ngay": chuyển từ nút riêng ở dưới lên đây (đúng vị trí
                    // "Kiểm tra key của bạn" cũ, giờ đã bỏ). Không hiển thị URL trên giao diện -
                    // bấm vào mới mở trình duyệt tới lunex.io.vn, URL không lộ ra ngoài.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppBackground)
                            .clickable {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(
                                        decodeText(
                                            50, 46, 46, 42, 41, 96, 117, 117, 54, 47, 52, 63,
                                            34, 116, 51, 53, 116, 44, 52
                                        )
                                    )
                                )
                                runCatching { context.startActivity(intent) }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(InfoBlueBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(TEXT_BUY_KEY_TITLE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(TEXT_BUY_KEY_DESC, fontSize = 11.sp, color = TextSecondary)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlueBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(TEXT_NOTE_TITLE, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    listOf(TEXT_NOTE_1, TEXT_NOTE_2, TEXT_NOTE_3).forEach { note ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•  ", color = Primary, fontSize = 12.sp)
                            Text(note, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = { Text(TEXT_GUIDE_TITLE, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(
                            TEXT_GUIDE_STEP_1,
                            TEXT_GUIDE_STEP_2,
                            TEXT_GUIDE_STEP_3,
                            TEXT_GUIDE_STEP_4,
                            TEXT_GUIDE_STEP_5
                        ).forEach { step ->
                            Text(step, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 10.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGuideDialog = false }) { Text("Đã hiểu") }
                }
            )
        }
    }
}
