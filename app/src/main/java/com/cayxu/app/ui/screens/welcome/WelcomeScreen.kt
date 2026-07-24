package com.cayxu.app.ui.screens.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import com.cayxu.app.ui.theme.*
import kotlin.random.Random

/**
 * Màn "Chào mừng" - CHỈ hiện đúng 1 LẦN DUY NHẤT ở lần mở app đầu tiên sau khi cài (xem
 * SecurePrefs.hasSeenWelcome). Không đụng gì tới logic key - chỉ là bước giới thiệu trước
 * khi vào màn nhập Key kích hoạt.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Đồng xu rơi trang trí phía trên - vẽ bằng Canvas (không cần ảnh rời), rắc ngẫu
        // nhiên nhưng cố định 1 lần (remember theo seed) để không "nhảy" mỗi lần recompose.
        val coins = remember {
            val rnd = Random(7)
            List(9) {
                CoinDot(
                    xFrac = rnd.nextFloat(),
                    yFrac = rnd.nextFloat() * 0.42f,
                    radius = rnd.nextInt(10, 34).toFloat(),
                    tilt = rnd.nextFloat() * 0.6f - 0.3f
                )
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(320.dp)) {
            coins.forEach { coin ->
                val cx = size.width * coin.xFrac
                val cy = size.height * coin.yFrac
                drawCircle(
                    color = Color(0xFFFFC83D),
                    radius = coin.radius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = Color(0xFFFFE29A),
                    radius = coin.radius * 0.62f,
                    center = Offset(cx, cy)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(180.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text("CâyXu", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Chào mừng bạn đến với",
                fontSize = 17.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "CâyXu",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) { append("Kiếm xu mỗi ngày") }
                    append("\n")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) { append("Đổi thưởng cực dễ") }
                },
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "Hoàn thành nhiệm vụ, tích xu và đổi thưởng hấp dẫn chỉ trong vài bước đơn giản.",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Bắt đầu ngay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Bằng việc tiếp tục, bạn đồng ý với Điều khoản sử dụng và Chính sách bảo mật của chúng tôi.",
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class CoinDot(val xFrac: Float, val yFrac: Float, val radius: Float, val tilt: Float)
