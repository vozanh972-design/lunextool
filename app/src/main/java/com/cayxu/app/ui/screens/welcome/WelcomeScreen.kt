package com.cayxu.app.ui.screens.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.R
import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.locale.Str
import com.cayxu.app.ui.theme.*

/**
 * Màn "Chào mừng" - CHỈ hiện đúng 1 LẦN DUY NHẤT ở lần mở app đầu tiên sau khi cài (xem
 * SecurePrefs.hasSeenWelcome). Không đụng gì tới logic key - chỉ là bước giới thiệu trước
 * khi vào màn nhập Key kích hoạt.
 *
 * Bố cục dựng theo ảnh mẫu do bên làm app (Lunex) cung cấp:
 *   [logo Lunex + nút chọn ngôn ngữ] -> [ảnh minh họa] -> [badge CâyXu] -> [tiêu đề] ->
 *   [mô tả] -> [nút Bắt đầu ngay] -> [chấm phân trang] -> [điều khoản].
 * Phần ảnh minh họa ở giữa là ảnh tĩnh cắt từ mẫu gốc (ill_welcome_hero) - không dựng lại
 * bằng code vì đây là ảnh dựng 3D phức tạp, đúng như yêu cầu "cắt gắn vô".
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val context = LocalContext.current
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            // ---- Hàng trên cùng: logo Lunex (thương hiệu làm ra app) + chọn ngôn ngữ ----
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.ic_lunex_logo),
                    contentDescription = "Lunex",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp)
                )

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardWhite)
                            .clickable { languageMenuExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Language,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(Str.welcomeLanguageLabel, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
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
                            text = { Text("Tiếng Việt") },
                            onClick = {
                                LanguageState.setLanguage(context, AppLanguage.VI)
                                languageMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = {
                                LanguageState.setLanguage(context, AppLanguage.EN)
                                languageMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Ảnh minh họa (cắt từ mẫu gốc) ----
            Image(
                painter = painterResource(R.drawable.ill_welcome_hero),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            )

            Spacer(Modifier.height(4.dp))

            // ---- Badge app CâyXu (app thật là CâyXu, Lunex là bên phát triển ở trên) ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_app_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(Str.welcomeAppBadge, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                Str.welcomeTitleLine1,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )
            Text(
                Str.welcomeTitleLine2,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Primary,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )

            Spacer(Modifier.height(12.dp))

            Text(
                Str.welcomeSubtitle,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(22.dp))

            // ---- Nút Bắt đầu ngay - pill xanh nhạt, chữ + icon xanh đậm, đúng màu mẫu ----
            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD7EAFE)),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(Str.welcomeGetStarted, color = Primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(CardWhite),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Primary, modifier = Modifier.size(15.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Chấm phân trang (trang trí, giống mẫu) ----
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Primary))
                Box(Modifier.size(8.dp).clip(CircleShape).background(TextSecondary.copy(alpha = 0.3f)))
                Box(Modifier.size(8.dp).clip(CircleShape).background(TextSecondary.copy(alpha = 0.3f)))
            }

            Spacer(Modifier.height(14.dp))

            Text(
                Str.welcomeTerms,
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}
