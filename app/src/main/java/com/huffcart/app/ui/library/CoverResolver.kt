package com.huffcart.app.ui.library

/**
 * covers/ 目录内文件名：规范化名 + .png。导入与联网共用同一槽位——
 * 导入直接覆盖写，即体现「导入 > 联网」优先级（game-cover-art 决策 1）。
 *
 * 审计 R1：本文件原 CoverResolver/CoverSource 全库零引用，优先级语义实际由
 * [CoverStore.decodeDisplayCover]（导入/联网封面 > 运行截图）实现，已删除。
 */
fun coverFileName(gameName: String): String = "${GenreCatalog.normalize(gameName)}.png"
