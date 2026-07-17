package com.cayxu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = CardWhite,
    background = AppBackground,
    surface = CardWhite,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun CayXuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = CayXuTypography,
        content = content
    )
}
