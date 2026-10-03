package com.huffcart.app.ui.game

/** ROM 平台(gb-gbc-platform 决策 1):库角标、格式文案、核心选择、存档键的共同维度。 */
enum class RomPlatform(val label: String, val ext: String, val formatText: String, val coreLibName: String) {
    FC("FC", "nes", "iNES (.nes)", "fceumm"),
    GB("GB", "gb", "Game Boy (.gb)", "gambatte"),
    GBC("GBC", "gbc", "Game Boy Color (.gbc)", "gambatte");

    companion object {

        /** iNES 魔数。 */
        private val INES_MAGIC = byteArrayOf(
            'N'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 0x1A.toByte(),
        )

        /** GB 头部 Nintendo logo($104–$133,pandocs The Cartridge Header)。 */
        private val GB_LOGO = byteArrayOf(
            0xCE.toByte(), 0xED.toByte(), 0x66.toByte(), 0x66.toByte(), 0xCC.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x0B.toByte(),
            0x03.toByte(), 0x73.toByte(), 0x00.toByte(), 0x83.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x00.toByte(), 0x0D.toByte(),
            0x00.toByte(), 0x08.toByte(), 0x11.toByte(), 0x1F.toByte(), 0x88.toByte(), 0x89.toByte(), 0x00.toByte(), 0x0E.toByte(),
            0xDC.toByte(), 0xCC.toByte(), 0x6E.toByte(), 0xE6.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xD9.toByte(), 0x99.toByte(),
            0xBB.toByte(), 0xBB.toByte(), 0x67.toByte(), 0x63.toByte(), 0x6E.toByte(), 0x0E.toByte(), 0xEC.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xDC.toByte(), 0x99.toByte(), 0x9F.toByte(), 0xBB.toByte(), 0xB9.toByte(), 0x33.toByte(), 0x3E.toByte(),
        )

        /** 按扩展名取平台(游戏会话/呈现用——入库文件已过头部校验)。 */
        fun fromExtension(fileName: String): RomPlatform? {
            val ext = fileName.substringAfterLast('.').lowercase()
            return entries.firstOrNull { it.ext == ext }
        }

        /** 头部判定:文件前缀(至少 0x150 字节,不足时传全部)→ 平台;无一匹配返回 null。
         *  头部是唯一真源——改名文件按其真实平台导入,避免以错误核心加载黑屏。 */
        fun headerPlatform(bytes: ByteArray): RomPlatform? {
            if (bytes.size >= INES_MAGIC.size && INES_MAGIC.indices.all { bytes[it] == INES_MAGIC[it] }) return FC
            if (bytes.size >= 0x150) {
                val logoMatched = GB_LOGO.indices.all { bytes[0x104 + it] == GB_LOGO[it] }
                val entryMatched = bytes[0x100] == 0x00.toByte() && bytes[0x101] == 0xC3.toByte() &&
                    bytes[0x102] == 0x50.toByte() && bytes[0x103] == 0x01.toByte()
                if (logoMatched && entryMatched) {
                    // $143:$80 向下兼容 GBC / $C0 GBC 专属——统一按 GBC 呈现(标签差异仅装饰)
                    return if ((bytes[0x143].toInt() and 0xFF) in intArrayOf(0x80, 0xC0)) GBC else GB
                }
            }
            return null
        }
    }
}
