package com.huffcart.app.ui.library

import java.net.URLDecoder

/**
 * libretro 缩略图库索引（game-cover-art 决策 2，纯 JVM 可测）：
 * thumbnails.libretro.com 的 Named_Boxarts / Named_Snaps 为平铺目录，文件名是完整发布名
 * （如 "1942 (1985-12-11)(Capcom)(JP-US).png"），无首字母子目录，直拼 URL 不可行——
 * 故先抓目录索引 HTML，解析出「裸标题规范化名 → 精确文件名」映射，再精确抓取。
 */
object CoverIndex {

    const val BOXARTS = "Named_Boxarts"
    const val SNAPS = "Named_Snaps"
    const val SYSTEM_PATH = "Nintendo%20-%20Nintendo%20Entertainment%20System"
    private const val BASE = "https://thumbnails.libretro.com"

    /** 目录索引页 URL（NAS 风格 HTML 列表）。 */
    fun listingUrl(collection: String): String = "$BASE/$SYSTEM_PATH/$collection/"

    /** 封面文件 URL：集合目录下接索引中原样保留的百分号编码文件名。 */
    fun fileUrl(collection: String, encodedFileName: String): String =
        "$BASE/$SYSTEM_PATH/$collection/$encodedFileName"

    /**
     * 解析目录索引 HTML：href="...png" → 键为裸标题规范化名。
     * 跳过排序链接（?C=…）与父目录（/…）；同一游戏的多个 dump 变体（[b]/[h] 等）
     * 优先无标记的干净版；解析不了的条目静默跳过。
     */
    fun parse(listingHtml: String): Map<String, String> {
        val best = HashMap<String, Pair<String, Boolean>>()
        Regex("href=\"([^\"]+\\.png)\"").findAll(listingHtml).forEach { match ->
            val href = match.groupValues[1].replace("&amp;", "&")
            if (href.startsWith("?") || href.startsWith("/")) return@forEach
            val name = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }
                .getOrNull() ?: return@forEach
            val key = keyFor(name) ?: return@forEach
            val clean = !name.contains('[')
            val existing = best[key]
            if (existing == null || (clean && !existing.second)) {
                best[key] = href to clean
            }
        }
        return best.mapValues { it.value.first }
    }

    /** 发布名 → 规范化键：去扩展后复用 [GenreCatalog.normalize]（去区域/转储标记、只留字母数字）。 */
    fun keyFor(releaseName: String): String? {
        val key = GenreCatalog.normalize(releaseName.substringBeforeLast('.'))
        return key.ifEmpty { null }
    }
}
