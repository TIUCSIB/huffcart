package com.huffcart.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.huffcart.app.R

// 像素字体链：英文/数字命中 Press Start 2P，缺字（中文）回落融合像素字体；
// 正文不使用像素字体，保证信息密集页可读（design.md 决策 3）。
val PixelFontFamily = FontFamily(
    Font(R.font.press_start_2p),
    Font(R.font.fusion_pixel_12px_zh_hans),
)

private val HuffcartTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = PixelFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = PixelFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = PixelFontFamily),
    )
}

internal val HuffcartTypographyForTheme = HuffcartTypography
