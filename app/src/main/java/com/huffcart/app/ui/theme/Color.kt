package com.huffcart.app.ui.theme

import androidx.compose.ui.graphics.Color

val HcRed = Color(0xFFE60012) // 任天堂红，主色
val HcYellow = Color(0xFFF7B500) // FC 黄，点缀
val HcCream = Color(0xFFF6F1E7) // 米白
val HcRedDark = Color(0xFFB50010) // 品牌页深红底

// 浅色主题（retro-ui-redesign：强制浅色，不随系统翻转）
val HcSurfaceLight = Color(0xFFFFFFFF)
val HcOnLight = Color(0xFF33291F)
val HcOnLightVariant = Color(0xFF8A7E6E)
val HcOutlineLight = Color(0xFFDCD2C2)
val HcChipBg = Color(0xFFEFE6D6)

// 游戏屏专属深色（画面区黑底与控制面板，浅色主题的豁免区；色值取素材手柄实测）
// 审计 R2：删除 6 个零引用常量（HcPanelRecess/HcDpadArm/HcPadRedBright/HcPillBg/HcPillBorder/HcBrick），
// 手柄面板实际使用 PadControls 私有色板
val HcGameBlack = Color(0xFF000000)
val HcPanelDark = Color(0xFF1F1F1F) // 控制面板
val HcPadRed = Color(0xFFF22C2E) // A/B 红
val HcPadRedDeep = Color(0xFFC21E20) // A/B 立体底影
val HcBannerTag = Color(0xFF6E1414) // 游戏屏横幅标签底

// 分类页品类色（game-library「分类页」：彩色卡片）
val HcGenreShooter = Color(0xFF0072BC)
val HcGenreAdventure = Color(0xFF2FA84F)
val HcGenrePuzzle = Color(0xFF8E44AD)
val HcGenreRacing = Color(0xFF26A9E0)
