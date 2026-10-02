package com.huffcart.app.ui.library

/** 封面来源（spec「封面墙网格」的优先级顺序）。 */
enum class CoverSource { IMPORTED, FETCHED, SHOT, PLACEHOLDER }

/** 封面优先级解析（纯逻辑）：用户导入 > 联网抓取 > 运行截图 > 确定性占位图。 */
object CoverResolver {

    fun resolve(importedExists: Boolean, fetchedExists: Boolean, screenshotExists: Boolean = false): CoverSource = when {
        importedExists -> CoverSource.IMPORTED
        fetchedExists -> CoverSource.FETCHED
        screenshotExists -> CoverSource.SHOT
        else -> CoverSource.PLACEHOLDER
    }
}

/**
 * covers/ 目录内文件名：规范化名 + .png。导入与联网共用同一槽位——
 * 导入直接覆盖写，即体现「导入 > 联网」优先级（game-cover-art 决策 1）。
 */
fun coverFileName(gameName: String): String = "${GenreCatalog.normalize(gameName)}.png"
