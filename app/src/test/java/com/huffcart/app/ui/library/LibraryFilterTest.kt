package com.huffcart.app.ui.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 库过滤叠加逻辑（retro-ui-redesign 3.3）：关键字 × 分类，未分类仅归「全部」。 */
class LibraryFilterTest {

    private fun rom(name: String) = File("/roms/$name")

    private val roms = listOf(
        rom("超级马里奥兄弟.nes"),
        rom("Contra (J).nes"),
        rom("俄罗斯方块.nes"),
        rom("我小时候编的游戏.nes"),
    )

    @Test
    fun emptyQueryAndAllGenreReturnsEverything() {
        assertEquals(roms, LibraryState.filterRoms(roms, "", null))
    }

    @Test
    fun queryFiltersCaseInsensitive() {
        val result = LibraryState.filterRoms(roms, "  CON ", null)
        assertEquals(listOf(rom("Contra (J).nes")), result)
    }

    @Test
    fun genreFilterUsesCatalog() {
        val shooters = LibraryState.filterRoms(roms, "", GenreCatalog.Genre.SHOOTER)
        assertEquals(listOf(rom("Contra (J).nes")), shooters)
        val puzzles = LibraryState.filterRoms(roms, "", GenreCatalog.Genre.PUZZLE)
        assertEquals(listOf(rom("俄罗斯方块.nes")), puzzles)
    }

    @Test
    fun unclassifiedGamesOnlyInAll() {
        val action = LibraryState.filterRoms(roms, "", GenreCatalog.Genre.ACTION)
        assertTrue(rom("我小时候编的游戏.nes") !in action)
        assertTrue(rom("超级马里奥兄弟.nes") in action)
    }

    @Test
    fun queryAndGenreIntersect() {
        val result = LibraryState.filterRoms(roms, "马里奥", GenreCatalog.Genre.PUZZLE)
        assertTrue(result.isEmpty())
        val hit = LibraryState.filterRoms(roms, "俄罗斯", GenreCatalog.Genre.PUZZLE)
        assertEquals(listOf(rom("俄罗斯方块.nes")), hit)
    }
}
