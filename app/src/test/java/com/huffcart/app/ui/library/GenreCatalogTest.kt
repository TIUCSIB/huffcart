package com.huffcart.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GenreCatalogTest {

    @Test
    fun lookupHitsChineseName() {
        val entry = GenreCatalog.lookup("超级马里奥兄弟.nes")
        assertNotNull(entry)
        assertEquals(GenreCatalog.Genre.ACTION, entry.genre)
    }

    @Test
    fun lookupIgnoresCaseSpacesAndExtension() {
        val entry = GenreCatalog.lookup(" Super Mario Bros.NES")
        assertNotNull(entry)
        assertEquals(GenreCatalog.Genre.ACTION, entry.genre)
    }

    @Test
    fun lookupReturnsNullForUnknownGame() {
        assertNull(GenreCatalog.lookup("我小时候编的游戏.nes"))
    }

    @Test
    fun summaryPresentForCuratedGames() {
        assertNotNull(GenreCatalog.lookup("魂斗罗.nes")?.summary)
        assertNull(GenreCatalog.lookup("双截龙.nes")?.summary)
    }

    @Test
    fun racingCategoryHits() {
        val entry = GenreCatalog.lookup("F1 Race (J).nes")
        assertNotNull(entry)
        assertEquals(GenreCatalog.Genre.RACING, entry.genre)
    }

    @Test
    fun genresMatchSpecSet() {
        assertEquals(
            listOf("动作", "射击", "冒险", "益智", "格斗", "赛车"),
            GenreCatalog.Genre.entries.map { it.label },
        )
    }
}
