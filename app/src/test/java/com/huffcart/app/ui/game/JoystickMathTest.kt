package com.huffcart.app.ui.game

import com.huffcart.core.bridge.RetroButton
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoystickMathTest {

    /** 按角度（上=0、顺时针）与距离构造归一化输入。 */
    private fun push(deg: Float, dist: Float): Pair<Float, Float> {
        val rad = Math.toRadians(deg.toDouble())
        return Pair((sin(rad) * dist).toFloat(), (-cos(rad) * dist).toFloat())
    }

    private fun dirs(deg: Float, dist: Float, current: Set<RetroButton> = emptySet()) =
        JoystickMath.directions(push(deg, dist).first, push(deg, dist).second, current)

    @Test
    fun deadZoneProducesNoInput() {
        assertEquals(emptySet(), dirs(0f, JoystickMath.DEAD_ZONE - 0.01f))
        assertEquals(emptySet(), dirs(90f, 0.1f))
        assertEquals(emptySet(), JoystickMath.directions(0f, 0f, emptySet()))
    }

    @Test
    fun cardinalDirections() {
        assertEquals(setOf(RetroButton.UP), dirs(0f, 0.6f))
        assertEquals(setOf(RetroButton.RIGHT), dirs(90f, 0.6f))
        assertEquals(setOf(RetroButton.DOWN), dirs(180f, 0.6f))
        assertEquals(setOf(RetroButton.LEFT), dirs(-90f, 0.6f))
    }

    @Test
    fun diagonalCombinesTwoButtons() {
        assertEquals(setOf(RetroButton.UP, RetroButton.RIGHT), dirs(45f, 0.6f))
        assertEquals(setOf(RetroButton.DOWN, RetroButton.LEFT), dirs(-135f, 0.6f))
    }

    @Test
    fun neverMoreThanTwoButtons() {
        for (deg in -175..175 step 5) {
            assertTrue(dirs(deg.toFloat(), 0.9f).size <= 2, "deg=$deg")
        }
    }

    @Test
    fun sectorBoundaryAtActivation() {
        // 22° 属「上」扇区（±22.5°），23° 越界进右上扇区
        assertEquals(setOf(RetroButton.UP), dirs(22f, 0.8f))
        assertEquals(setOf(RetroButton.UP, RetroButton.RIGHT), dirs(23f, 0.8f))
        // 「右」扇区 [67.5°, 112.5°]：67° 属右上、68° 属右
        assertEquals(setOf(RetroButton.UP, RetroButton.RIGHT), dirs(67f, 0.8f))
        assertEquals(setOf(RetroButton.RIGHT), dirs(68f, 0.8f))
    }

    @Test
    fun distanceHysteresisAroundDeadZone() {
        val right = setOf(RetroButton.RIGHT)
        // 已激活：低于激活死区（0.25）但不低于退出阈值（0.20）仍保持
        assertEquals(right, dirs(90f, 0.22f, right))
        // 低于退出阈值 → 复位
        assertEquals(emptySet(), dirs(90f, 0.19f, right))
        // 未激活：0.22 低于激活死区 → 无输入
        assertEquals(emptySet(), dirs(90f, 0.22f))
    }

    @Test
    fun angleHysteresisPreventsJitterNearBoundary() {
        val right = setOf(RetroButton.RIGHT)
        // 90° + 25° = 115°：仍在「右」外扩扇区（±27.5°）内 → 保持
        assertEquals(right, dirs(115f, 0.8f, right))
        // 120°：越出外扩扇区 → 换向右下
        assertEquals(setOf(RetroButton.DOWN, RetroButton.RIGHT), dirs(120f, 0.8f, right))
        // 未激活时同角度直接判右下（无保持可言）
        assertEquals(setOf(RetroButton.DOWN, RetroButton.RIGHT), dirs(120f, 0.8f))
    }

    @Test
    fun directionSwitchWithoutRelease() {
        assertEquals(setOf(RetroButton.RIGHT), dirs(90f, 0.6f, setOf(RetroButton.LEFT)))
        assertEquals(setOf(RetroButton.UP), dirs(0f, 0.6f, setOf(RetroButton.DOWN)))
    }

    @Test
    fun diagonalKeptWhileInsideWidenedSector() {
        val upRight = setOf(RetroButton.UP, RetroButton.RIGHT)
        // 45° + 27° = 72°：在右上外扩扇区内 → 保持斜向
        assertEquals(upRight, dirs(72f, 0.8f, upRight))
        // 73°：越出 → 换「右」
        assertEquals(setOf(RetroButton.RIGHT), dirs(73f, 0.8f, upRight))
    }
}

// ---- 十字键模式：宽正方向扇区（32°）/ 窄斜向扇区（13°）----

class JoystickDpadModeTest {

    private fun push(deg: Float, dist: Float): Pair<Float, Float> {
        val rad = Math.toRadians(deg.toDouble())
        return Pair((sin(rad) * dist).toFloat(), (-cos(rad) * dist).toFloat())
    }

    private fun dpadDirs(deg: Float, dist: Float, current: Set<RetroButton> = emptySet()) =
        JoystickMath.directions(
            push(deg, dist).first, push(deg, dist).second, current,
            deadZone = 0.12f, exitZone = 0.10f,
            cardinalHalfDegrees = 32f, diagonalHalfDegrees = 13f,
        )

    @Test
    fun offAxisPressOnArmStaysCardinal() {
        // 按左臂略偏下（-121°，偏轴 31°）仍判「左」——均匀扇区会误判左下
        assertEquals(setOf(RetroButton.LEFT), dpadDirs(-121f, 0.8f))
        // 明确按在左下角（-140°）才是斜向
        assertEquals(setOf(RetroButton.DOWN, RetroButton.LEFT), dpadDirs(-140f, 0.8f))
    }

    @Test
    fun diagonalRequiresDeliberateAim() {
        assertEquals(setOf(RetroButton.UP, RetroButton.RIGHT), dpadDirs(45f, 0.8f))
        assertEquals(setOf(RetroButton.UP, RetroButton.RIGHT), dpadDirs(56f, 0.8f))
        // 越出斜向扇区（45°±13° 之外）回落正方向
        assertEquals(setOf(RetroButton.RIGHT), dpadDirs(59f, 0.8f))
        assertEquals(setOf(RetroButton.UP), dpadDirs(31f, 0.8f))
    }

    @Test
    fun dpadHysteresisKeepsCurrentCardinal() {
        // 已激活「左」时手指滑到 -124°（偏轴 34°）仍保持左，不抖进斜向
        assertEquals(setOf(RetroButton.LEFT), dpadDirs(-124f, 0.8f, setOf(RetroButton.LEFT)))
    }
}
