package com.huffcart.app.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** 确定性像素风占位封面：按游戏名生成，同一游戏任何时刻图案一致（spec「封面确定性」）。 */
@Composable
fun PlaceholderCover(gameName: String, modifier: Modifier = Modifier) {
    val grid = remember(gameName) { CoverPattern.gridFor(gameName) }
    Canvas(modifier = modifier) {
        val cols = CoverPattern.COLS
        val rows = CoverPattern.ROWS
        val cellW = size.width / cols
        val cellH = size.height / rows
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                drawRect(
                    color = Color(grid[row * cols + col]),
                    topLeft = Offset(col * cellW, row * cellH),
                    size = Size(cellW + 0.5f, cellH + 0.5f),
                )
            }
        }
    }
}
