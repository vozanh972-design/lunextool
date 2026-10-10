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
    primary = Color(0xFF2F6BFF),
    primaryDark = Color(0xFF1D55E0),
    appBackground = Color(0xFFF5F6FA),
    cardWhite = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF191C29),
    textSecondary = Color(0xFF767B90),
    successGreen = Color(0xFF10B981),
    warningOrange = Color(0xFFF59E0B),
    dangerRed = Color(0xFFEF4444),
    infoBlueBg = Color(0xFFEAF0FF),
    borderLight = Color(0xFFE7EAF2)
)

private val DarkPalette = CayXuColorPalette(
    primary = Color(0xFF4D82FF),
    primaryDark = Color(0xFF2F6BFF),
    appBackground = Color(0xFF0F121C),
    cardWhite = Color(0xFF1B202E),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    successGreen = Color(0xFF34D399),
    warningOrange = Color(0xFFFBBF24),
    dangerRed = Color(0xFFF87171),
    infoBlueBg = Color(0xFF202A42),
    borderLight = Color(0xFF2D3548)
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
