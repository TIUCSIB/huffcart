package com.huffcart.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverAliasesTest {

    @Test
    fun allTargetsAreNormalizedAsciiKeys() {
        // 目标键必须是「只含小写字母数字」的规范化名，才能直接命中 libretro 索引
        val offender = CoverAliases.table.entries
            .firstOrNull { !Regex("[a-z0-9]+").matches(it.value) }
        assertTrue(offender == null, "非法目标键：$offender")
    }

    @Test
    fun keysDoNotCollideWithValues() {
        // 键是中文规范化名、值是英文规范化名，两者不应出现同名（防自我映射）
        val offender = CoverAliases.table.entries
            .firstOrNull { it.key == it.value }
        assertTrue(offender == null, "自映射条目：$offender")
    }

    @Test
    fun spotCheckKnownMappings() {
        assertEquals("contra", CoverAliases.target("魂斗罗"))
        assertEquals("battlecity", CoverAliases.target("坦克大战"))
        assertEquals("supermariobros", CoverAliases.target("超级马里奥兄弟"))
    }

    @Test
    fun unknownKeyReturnsNull() {
        assertNull(CoverAliases.target("4人麻将"))
        assertNull(CoverAliases.target("没有这个游戏"))
    }
}
