package com.huffcart.app.ui.library

/**
 * 占位封面图案（纯逻辑，retro-ui-redesign 决策 5）：
 * 以游戏名哈希为随机种子，生成左半 8×8 随机块并水平镜像为 16×8，
 * 颜色取自固定复古调色板——同一游戏名永远得到同一图案。
 * 只依赖 kotlin.random 与 Int 颜色值，保证纯 JVM 可测。
 */
object CoverPattern {

    val PALETTE = intArrayOf(
        0xFFE60012.toInt(), // 红
        0xFFF7B500.toInt(), // 黄
        0xFF2E6DB4.toInt(), // 蓝
        0xFF2FA84F.toInt(), // 绿
        0xFF8E44AD.toInt(), // 紫
        0xFFF6F1E7.toInt(), // 米白
        0xFF33291F.toInt(), // 深棕
    )

    const val HALF_COLS = 8
    const val ROWS = 8
    const val COLS = HALF_COLS * 2

    /** 返回 COLS*ROWS 的行优先颜色数组（ARGB Int），水平镜像对称。 */
    fun gridFor(name: String): IntArray {
        val rng = kotlin.random.Random(name.hashCode())
        val bg = PALETTE[rng.nextInt(PALETTE.size)]
        val fg = PALETTE[rng.nextInt(PALETTE.size)]
        val accent = PALETTE[rng.nextInt(PALETTE.size)]
        val grid = IntArray(COLS * ROWS)
        for (row in 0 until ROWS) {
            for (col in 0 until HALF_COLS) {
                val color = when (rng.nextInt(100)) {
                    in 0..54 -> fg
                    in 55..69 -> accent
                    else -> bg
                }
                grid[row * COLS + col] = color
                grid[row * COLS + (COLS - 1 - col)] = color
            }
        }
        return grid
    }
}
