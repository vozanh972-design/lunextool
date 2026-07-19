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
    val dangerRed: Color,
    val infoBlueBg: Color
)

private val LightPalette = CayXuColorPalette(
    primary = Color(0xFF2563EB),
    primaryDark = Color(0xFF1D4ED8),
    appBackground = Color(0xFFF6F8FC),
    cardWhite = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    successGreen = Color(0xFF16A34A),
    dangerRed = Color(0xFFDC2626),
    infoBlueBg = Color(0xFFEFF4FF)
)

private val DarkPalette = CayXuColorPalette(
    primary = Color(0xFF60A5FA),
    primaryDark = Color(0xFF3B82F6),
    appBackground = Color(0xFF0B1220),
    cardWhite = Color(0xFF1B2536),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    successGreen = Color(0xFF4ADE80),
    dangerRed = Color(0xFFF87171),
    infoBlueBg = Color(0xFF23324A)
)

val LocalCayXuColors = staticCompositionLocalOf { LightPalette }

@Composable
fun CayXuTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette

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
