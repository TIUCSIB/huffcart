package com.huffcart.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = HcRed,
    onPrimary = Color.White,
    secondary = HcYellow,
    onSecondary = Color.Black,
    background = HcBackground,
    onBackground = HcOnBackground,
    surface = HcSurface,
    onSurface = HcOnBackground,
    surfaceVariant = HcSurfaceVariant,
    onSurfaceVariant = HcOnSurfaceVariant,
    outline = HcOutline,
)

// 深色 only 是刻意的产品选择（design.md Risks）：系统浅色下同样强制深色。
@Composable
fun HuffcartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = HuffcartTypographyForTheme,
        content = content,
    )
}
