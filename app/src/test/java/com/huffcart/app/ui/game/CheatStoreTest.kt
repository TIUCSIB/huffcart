package com.huffcart.app.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 金手指校验与行格式解析（cheat-codes 任务 2.1）。 */
class CheatStoreTest {

    @Test
    fun validGameGenieCodes() {
        assertTrue(CheatStore.isValidGameGenie("SXIOPO")) // 6 位
        assertTrue(CheatStore.isValidGameGenie("SXIOPOXX")) // 8 位
        assertTrue(CheatStore.isValidGameGenie("sxiopo")) // 大小写不敏感
        assertTrue(CheatStore.isValidGameGenie("SXIO-PO")) // 连字符可选
    }

    @Test
    fun invalidGameGenieCodes() {
        assertFalse(CheatStore.isValidGameGenie("SXIO")) // 过短
        assertFalse(CheatStore.isValidGameGenie("SXIOPOXXX")) // 9 位
        assertFalse(CheatStore.isValidGameGenie("123456")) // 非字母表
        assertFalse(CheatStore.isValidGameGenie("SXIOBQ")) // B/Q 不在 GG 字母表
        assertFalse(CheatStore.isValidGameGenie(""))
    }

    @Test
    fun normalizeStripsDashAndSpaces() {
        assertEquals("SXIOPO", CheatStore.normalize(" sxi-opO "))
    }

    @Test
    fun lineRoundtrip() {
        val entries = listOf(CheatEntry("SXIOPO", true), CheatEntry("ZEZLYE", false))
        val parsed = CheatStore.parseLines(CheatStore.serializeLines(entries).lines())
        assertEquals(entries, parsed)
    }

    @Test
    fun parseDropsCorruptLines() {
        val parsed = CheatStore.parseLines(
            listOf("SXIOPO\t1", "nosep", "", "\t1", "GARBAGE!\t0", "ZEZLYE\t0"),
        )
        // 损坏行与非 GG 字母表的码一并丢弃（自愈）
        assertEquals(listOf(CheatEntry("SXIOPO", true), CheatEntry("ZEZLYE", false)), parsed)
    }

    @Test
    fun parseEmptyFile() {
        assertEquals(emptyList(), CheatStore.parseLines(emptyList()))
    }
}
