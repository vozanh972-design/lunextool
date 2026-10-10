package com.cayxu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val CayXuTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.1).sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.5.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.1.sp)
)
