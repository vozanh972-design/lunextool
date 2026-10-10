package com.cayxu.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Toàn bộ màu dùng trong app (nền, chữ, card...) đi qua palette này thay vì hằng số cố định,
 * để "Chế độ tối" ở màn Cài đặt có thể đổi màu thật cho MỌI màn hình cùng lúc.
 */
data class CayXuColorPalette(
    val primary: Color,
    val primaryDark: Color,
    val appBackground: Color,
    val cardWhite: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val successGreen: Color,
    val warningOrange: Color,
    val dangerRed: Color,
    val infoBlueBg: Color,
    val borderLight: Color = Color(0xFFE7EAF2)
)

private val LightPalette = CayXuColorPalette(
    primary = Color(0xFF2563EB), // Precision Sapphire Blue (Stripe / Linear)
    primaryDark = Color(0xFF1D4ED8),
    appBackground = Color(0xFFF8FAFC), // Slate 50 (Immaculate light background)
    cardWhite = Color(0xFFFFFFFF), // Pure white
    textPrimary = Color(0xFF0F172A), // Slate 900 (High-contrast charcoal)
    textSecondary = Color(0xFF64748B), // Slate 500 (Clean neutral)
    successGreen = Color(0xFF2563EB), // Precision Blue (0% cash green)
    warningOrange = Color(0xFFEA580C), // Clean orange (0% coin gold)
    dangerRed = Color(0xFFEF4444), // Clean crimson
    infoBlueBg = Color(0xFFEFF6FF), // Blue 50 (Subtle tint)
    borderLight = Color(0xFFE2E8F0) // Slate 200 (Hairline border)
)

// Toàn bộ app chuẩn hoá 100% Light Mode cao cấp (Swiss Clean Minimalist), loại bỏ Dark Mode
private val DarkPalette = LightPalette

val LocalCayXuColors = staticCompositionLocalOf { LightPalette }

@Composable
fun CayXuTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    // Luôn áp dụng Light Mode cao cấp, không dùng Dark Mode theo chuẩn thẩm mỹ Swiss Minimalist
    val palette = LightPalette

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.cardWhite,
            background = palette.appBackground,
            surface = palette.cardWhite,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.cardWhite,
            background = palette.appBackground,
            surface = palette.cardWhite,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary
        )
    }

    CompositionLocalProvider(LocalCayXuColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CayXuTypography,
            content = content
        )
    }
}
