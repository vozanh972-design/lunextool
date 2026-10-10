package com.cayxu.app.ui.screens.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cayxu.app.ui.locale.AppLanguage
import com.cayxu.app.ui.locale.LanguageState
import com.cayxu.app.ui.theme.*

/**
 * MÀN HÌNH CHỌN NGÔN NGỮ (LANGUAGE SELECTION SCREEN)
 * Chuẩn thiết kế Swiss Clean Minimalist
 * Xuất hiện sau 3 màn Onboarding và trước khi đến màn Chào mừng / Đăng nhập.
 */
@Composable
fun LanguageSelectionScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf(LanguageState.language) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Biểu tượng ngôn ngữ tối giản
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(InfoBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tiêu đề & phụ đề
                Text(
                    text = if (selectedLang == AppLanguage.VI) "Chọn ngôn ngữ" else "Select Language",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (selectedLang == AppLanguage.VI) 
                        "Vui lòng chọn ngôn ngữ hiển thị bạn muốn sử dụng."
                    else 
                        "Please select your preferred display language.",
                    fontSize = 15.sp,
                    color = TextSecondary,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(36.dp))

                // ── LỰA CHỌN 1: TIẾNG VIỆT ──
                LanguageCard(
                    title = "Tiếng Việt",
                    subtitle = "Giao diện Tiếng Việt",
                    flagEmoji = "🇻🇳",
                    code = "VI",
                    isSelected = selectedLang == AppLanguage.VI,
                    onClick = {
                        selectedLang = AppLanguage.VI
                        LanguageState.setLanguage(context, AppLanguage.VI)
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // ── LỰA CHỌN 2: ENGLISH ──
                LanguageCard(
                    title = "English",
                    subtitle = "English interface",
                    flagEmoji = "🇺🇸",
                    code = "EN",
                    isSelected = selectedLang == AppLanguage.EN,
                    onClick = {
                        selectedLang = AppLanguage.EN
                        LanguageState.setLanguage(context, AppLanguage.EN)
                    }
                )
            }

            // NÚT TIẾP TỤC Ở ĐÁY MÀN HÌNH
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = {
                        LanguageState.setLanguage(context, selectedLang)
                        onFinished()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = if (selectedLang == AppLanguage.VI) "Tiếp tục" else "Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(
    title: String,
    subtitle: String,
    flagEmoji: String,
    code: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Primary else BorderLight,
        label = "borderColor"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) InfoBlueBg.copy(alpha = 0.5f) else CardWhite,
        label = "containerColor"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hộp hiển thị cờ hoặc mã quốc gia
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Primary.copy(alpha = 0.12f) else Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = flagEmoji,
                        fontSize = 24.sp
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BorderLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = code,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // Radio Indicator / Checkmark
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Primary else Color.Transparent)
                    .border(
                        width = if (isSelected) 0.dp else 1.5.dp,
                        color = if (isSelected) Color.Transparent else BorderLight,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
