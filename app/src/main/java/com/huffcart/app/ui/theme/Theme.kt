package com.huffcart.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// retro-ui-redesign：强制米白浅色（不随系统深浅色翻转），游戏屏内部保持黑底
private val LightColors = lightColorScheme(
    primary = HcRed,
    onPrimary = Color.White,
    secondary = HcYellow,
    onSecondary = Color.Black,
    background = HcCream,
    onBackground = HcOnLight,
    surface = HcSurfaceLight,
    onSurface = HcOnLight,
    surfaceVariant = HcChipBg,
    onSurfaceVariant = HcOnLightVariant,
    outline = HcOutlineLight,
)

@Composable
fun HuffcartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = HuffcartTypographyForTheme,
        content = content,
    )
}
