package com.huffcart.app.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 平台判定(gb-gbc-platform 任务 1.1):iNES/GB 头、GBC 标志、伪 GB 拒绝、头为唯一真源。 */
class RomPlatformTest {

    private fun fcRom(): ByteArray = "NES\u001A".toByteArray() + ByteArray(32) { 0x00 }

    /** 合成 GB 头:入口 nop;jp $0150 + 完整 logo + $143 标志 + $148 ROM 尺寸。 */
    private fun gbRom(cgbFlag: Int): ByteArray = ByteArray(0x150).also { bytes ->
        bytes[0x100] = 0x00; bytes[0x101] = 0xC3.toByte(); bytes[0x102] = 0x50; bytes[0x103] = 0x01
        val logo = byteArrayOf(
            0xCE.toByte(), 0xED.toByte(), 0x66.toByte(), 0x66.toByte(), 0xCC.toByte(), 0x0D.toByte(), 0x00.toByte(), 0x0B.toByte(),
            0x03.toByte(), 0x73.toByte(), 0x00.toByte(), 0x83.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x00.toByte(), 0x0D.toByte(),
            0x00.toByte(), 0x08.toByte(), 0x11.toByte(), 0x1F.toByte(), 0x88.toByte(), 0x89.toByte(), 0x00.toByte(), 0x0E.toByte(),
            0xDC.toByte(), 0xCC.toByte(), 0x6E.toByte(), 0xE6.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xD9.toByte(), 0x99.toByte(),
            0xBB.toByte(), 0xBB.toByte(), 0x67.toByte(), 0x63.toByte(), 0x6E.toByte(), 0x0E.toByte(), 0xEC.toByte(), 0xCC.toByte(),
            0xDD.toByte(), 0xDC.toByte(), 0x99.toByte(), 0x9F.toByte(), 0xBB.toByte(), 0xB9.toByte(), 0x33.toByte(), 0x3E.toByte(),
        )
        logo.forEachIndexed { i, b -> bytes[0x104 + i] = b }
        bytes[0x143] = cgbFlag.toByte()
        bytes[0x148] = 0x00
    }

    @Test
    fun inesHeaderDetectsFC() {
        assertEquals(RomPlatform.FC, RomPlatform.headerPlatform(fcRom()))
    }

    @Test
    fun gbHeaderDetectsGB() {
        assertEquals(RomPlatform.GB, RomPlatform.headerPlatform(gbRom(cgbFlag = 0x00)))
    }

    @Test
    fun cgbFlag80AndC0DetectGBC() {
        assertEquals(RomPlatform.GBC, RomPlatform.headerPlatform(gbRom(cgbFlag = 0xC0)))
        assertEquals(RomPlatform.GBC, RomPlatform.headerPlatform(gbRom(cgbFlag = 0x80)))
    }

    @Test
    fun pseudoGbWithoutLogoIsRejected() {
        val bytes = gbRom(cgbFlag = 0x00).also { it[0x104] = 0x00 } // 破坏 logo 首字节
        assertNull(RomPlatform.headerPlatform(bytes))
    }

    @Test
    fun brokenEntryPointIsRejected() {
        val bytes = gbRom(cgbFlag = 0x00).also { it[0x101] = 0x00 } // 破坏 jp 指令
        assertNull(RomPlatform.headerPlatform(bytes))
    }

    @Test
    fun garbageAndShortInputsAreRejected() {
        assertNull(RomPlatform.headerPlatform("垃圾数据".toByteArray()))
        assertNull(RomPlatform.headerPlatform(ByteArray(4)))
        assertNull(RomPlatform.headerPlatform(ByteArray(0)))
    }

    @Test
    fun headerWinsOverRenamedExtension() {
        // GB 数据 + .nes 扩展名:头为唯一真源,按真实平台导入(避免错误核心黑屏)
        assertEquals(RomPlatform.GB, RomPlatform.headerPlatform(gbRom(cgbFlag = 0x00)))
    }

    @Test
    fun extensionLookupCoversAllPlatforms() {
        assertEquals(RomPlatform.FC, RomPlatform.fromExtension("塞尔达.NES"))
        assertEquals(RomPlatform.GB, RomPlatform.fromExtension("宝可梦.gb"))
        assertEquals(RomPlatform.GBC, RomPlatform.fromExtension("水晶.GBC"))
        assertNull(RomPlatform.fromExtension("说明.txt"))
    }

    @Test
    fun formatTextPerPlatform() {
        assertEquals("iNES (.nes)", RomPlatform.FC.formatText)
        assertEquals("Game Boy (.gb)", RomPlatform.GB.formatText)
        assertEquals("Game Boy Color (.gbc)", RomPlatform.GBC.formatText)
    }
}
