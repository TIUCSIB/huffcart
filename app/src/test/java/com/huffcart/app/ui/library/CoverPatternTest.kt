package com.huffcart.app.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertEquals as assertEquals2

class CoverPatternTest {

    @Test
    fun sameNameProducesIdenticalGrid() {
        assertEquals(
            CoverPattern.gridFor("超级马里奥兄弟").toList(),
            CoverPattern.gridFor("超级马里奥兄弟").toList(),
        )
    }

    @Test
    fun differentNamesProduceDifferentGrids() {
        assertNotEquals(CoverPattern.gridFor("超级马里奥兄弟").toList(), CoverPattern.gridFor("魂斗罗").toList())
    }

    @Test
    fun gridIsHorizontallyMirrored() {
        val grid = CoverPattern.gridFor("anything")
        for (row in 0 until CoverPattern.ROWS) {
            for (col in 0 until CoverPattern.HALF_COLS) {
                assertEquals(
                    grid[row * CoverPattern.COLS + col],
                    grid[row * CoverPattern.COLS + (CoverPattern.COLS - 1 - col)],
                    "row $row col $col 非镜像对称",
                )
            }
        }
    }

    @Test
    fun gridColorsComeFromPalette() {
        val grid = CoverPattern.gridFor("anything")
        grid.forEach { color ->
            kotlin.test.assertTrue(CoverPattern.PALETTE.contains(color), "越界颜色 $color")
        }
    }
}
