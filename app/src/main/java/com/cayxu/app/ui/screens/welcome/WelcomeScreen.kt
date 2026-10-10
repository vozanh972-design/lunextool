package com.cayxu.app.ui.screens.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.cayxu.app.ui.theme.*

/**
 * MÀN HÌNH CHÀO MỪNG (WELCOME SCREEN)
 * Chuẩn thiết kế Swiss Clean Minimalist:
 * - Đã loại bỏ logo A ở trên đỉnh.
 * - Ảnh minh họa trung tâm là ảnh chân dung người thật chuyên nghiệp, hợp chủ đề.
 * - Đã loại bỏ nút Đăng ký, chỉ giữ 1 nút Đăng nhập chính duy nhất.
 */
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit = {}
) {
    val context = LocalContext.current
    // Ảnh người thật chất lượng cao (chủ đề công nghệ, làm việc, tương tác)
    val realPersonPhotoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600&auto=format&fit=crop&q=80"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // 📌 MINH HỌA TRUNG TÂM: ẢNH NGƯỜI THẬT CHUYÊN NGHIỆP
                Box(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Vòng tròn nền mờ nhẹ bao ngoài
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(InfoBlueBg.copy(alpha = 0.6f))
                    )

                    // Khung ảnh chân dung người thật
                    Surface(
                        shape = CircleShape,
                        color = CardWhite,
                        border = BorderStroke(3.dp, CardWhite),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape)
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(realPersonPhotoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Chân dung thành viên",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Primary,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            },
                            error = {
                                // Fallback nếu thiết bị không có kết nối mạng
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(InfoBlueBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                        )
                    }

                    // Huy hiệu tia sáng / sao chứng nhận ở góc dưới phải
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-12).dp, y = (-12).dp)
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Primary)
                            .border(3.dp, CardWhite, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(44.dp))

                // 📌 TIÊU ĐỀ LỚN & PHỤ ĐỀ
                Text(
                    text = "Chào mừng đến với\nAutolunex",
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Kết nối, hoàn thành nhiệm vụ và mở ra những\nphần thưởng mới mỗi ngày.",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // 📌 NÚT BẤM ĐĂNG NHẬP DUY NHẤT (ĐÃ BỎ NÚT ĐĂNG KÝ)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp, top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onLoginClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = "Đăng nhập",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
