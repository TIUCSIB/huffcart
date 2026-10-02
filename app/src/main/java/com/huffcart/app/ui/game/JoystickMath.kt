package com.huffcart.app.ui.game

import com.huffcart.core.bridge.RetroButton
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 摇杆方向判定（virtual-joystick 决策 2）：归一化偏移 → 方向键集合（0–2 个）。
 * 纯函数无 Android 依赖；死区/迟滞参数集中于此，实机手感联调只动这里。
 *
 * 输入约定：nx/ny 为屏幕坐标偏移（x 右正、y 下正）除以满行程（帽最大位移），
 * 距离 1.0 = 推到底；current 为上一次判定的方向集合（空集 = 未激活）。
 */
object JoystickMath {

    /** 死区半径：未激活时低于它不产生方向输入。 */
    const val DEAD_ZONE = 0.25f

    /** 已激活后的退出阈值（低于死区，形成距离迟滞，防死区边缘抖动）。 */
    const val EXIT_ZONE = 0.20f

    /** 8 向扇区半宽（度）：以各方向中心 ±22.5° 为界。 */
    const val SECTOR_HALF_DEGREES = 22.5f

    /** 角度迟滞（度）：保持当前方向时扇区外扩该值才换向，防 45° 边界抖动。 */
    const val SECTOR_HYSTERESIS_DEGREES = 5f

    // 方向中心角（度）：上为 0、顺时针为正（atan2(x, -y)）
    private val DIR_DEGREES = listOf(
        0f to setOf(RetroButton.UP),
        45f to setOf(RetroButton.UP, RetroButton.RIGHT),
        90f to setOf(RetroButton.RIGHT),
        135f to setOf(RetroButton.DOWN, RetroButton.RIGHT),
        180f to setOf(RetroButton.DOWN),
        -135f to setOf(RetroButton.DOWN, RetroButton.LEFT),
        -90f to setOf(RetroButton.LEFT),
        -45f to setOf(RetroButton.UP, RetroButton.LEFT),
    )

    /**
     * 判定当前推杆方向集合。距离迟滞：未激活用 DEAD_ZONE、已激活用 EXIT_ZONE。
     *
     * 扇区宽度：正方向与斜向可分别指定（默认均匀 22.5°）。十字键传「宽正方向 / 窄斜向」
     * （如 32°/13°）——按臂身稍偏仍判正方向，只有明确按在 45° 角附近才判斜向，
     * 贴合真实十字键的机械门限；摇杆保持均匀扇区（模拟量手感）。
     * 角度迟滞：当前方向在其半宽 + SECTOR_HYSTERESIS_DEGREES 内时不换向。
     */
    fun directions(
        nx: Float,
        ny: Float,
        current: Set<RetroButton>,
        deadZone: Float = DEAD_ZONE,
        exitZone: Float = EXIT_ZONE,
        cardinalHalfDegrees: Float = SECTOR_HALF_DEGREES,
        diagonalHalfDegrees: Float = SECTOR_HALF_DEGREES,
    ): Set<RetroButton> {
        val dist = hypot(nx, ny)
        val threshold = if (current.isEmpty()) deadZone else exitZone
        if (dist < threshold) return emptySet()

        val deg = Math.toDegrees(atan2(nx.toDouble(), (-ny).toDouble())).toFloat()

        // 角度迟滞：仍在当前方向的加宽扇区内则保持（即使别的方向角距更近）
        if (current.isNotEmpty()) {
            val c = centerOf(current)
            if (c != null) {
                val half = (if (current.size == 2) diagonalHalfDegrees else cardinalHalfDegrees) +
                    SECTOR_HYSTERESIS_DEGREES
                if (kotlin.math.abs(wrap180(deg - c)) <= half) return current
            }
        }

        // 扇区判定：在各自半宽内取角距最近者
        var bestDirs: Set<RetroButton>? = null
        var bestDist = Float.MAX_VALUE
        for ((c, dirSet) in DIR_DEGREES) {
            val half = if (dirSet.size == 2) diagonalHalfDegrees else cardinalHalfDegrees
            val d = kotlin.math.abs(wrap180(deg - c))
            if (d <= half && d < bestDist) {
                bestDist = d
                bestDirs = dirSet
            }
        }
        // 兜底（扇区连续覆盖，理论不可达）：取角距最近的正方向
        return bestDirs ?: DIR_DEGREES.minByOrNull { kotlin.math.abs(wrap180(deg - it.first)) }!!.second
    }

    /** 方向集合对应的中心角；空集或非法组合返回 null。 */
    private fun centerOf(dirs: Set<RetroButton>): Float? = DIR_DEGREES
        .firstOrNull { it.second == dirs }
        ?.first

    /** 归一化到 (-180, 180]。 */
    private fun wrap180(a: Float): Float {
        var x = a % 360f
        if (x > 180f) x -= 360f
        if (x <= -180f) x += 360f
        return x
    }
}
