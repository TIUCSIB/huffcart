package com.huffcart.app.ui.game

import com.huffcart.core.bridge.RetroButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 摇杆轴→十字键状态机(physical-gamepad 任务 1.2):死区、触发、释放、迟滞、帽轴。 */
class AnalogStickStateTest {

    @Test
    fun deadzoneProducesNoInput() {
        val stick = AnalogStickState()
        assertTrue(stick.process(0.30f, 0f).isEmpty())
        assertTrue(stick.process(0f, -0.35f).isEmpty())
        assertTrue(stick.process(0f, 0f, 0.2f, 0f).isEmpty())
    }

    @Test
    fun engagesBeyondThreshold() {
        val stick = AnalogStickState()
        assertTrue(RetroButton.RIGHT in stick.process(0.60f, 0f))
        val up = AnalogStickState()
        assertTrue(RetroButton.UP in up.process(0f, -0.80f), "axisY 负值为上")
        val down = AnalogStickState()
        assertTrue(RetroButton.DOWN in down.process(0f, 0.80f))
    }

    @Test
    fun releasesBelowReleaseThreshold() {
        val stick = AnalogStickState()
        stick.process(0.70f, 0f)
        assertTrue(RetroButton.RIGHT in stick.process(0.50f, 0f), "迟滞带内保持")
        assertTrue(RetroButton.RIGHT in stick.process(0.42f, 0f), "迟滞带内保持")
        assertTrue(stick.process(0.30f, 0f).isEmpty(), "低于释放阈值复位")
    }

    @Test
    fun hysteresisBandDoesNotFlap() {
        val stick = AnalogStickState()
        stick.process(0.60f, 0f)
        // 在 0.40–0.55 迟滞带内来回抖动,方向应保持
        repeat(5) {
            assertTrue(RetroButton.RIGHT in stick.process(0.45f, 0f))
            assertTrue(RetroButton.RIGHT in stick.process(0.53f, 0f))
        }
    }

    @Test
    fun diagonalReturnsBothDirections() {
        val stick = AnalogStickState()
        val dirs = stick.process(0.80f, 0.80f)
        assertEquals(setOf(RetroButton.RIGHT, RetroButton.DOWN), dirs)
    }

    @Test
    fun hatAxisDrivesDirectionsIndependently() {
        val stick = AnalogStickState()
        assertTrue(RetroButton.RIGHT in stick.process(0f, 0f, 1f, 0f), "方向帽数字值直通")
        val up = AnalogStickState()
        assertTrue(RetroButton.UP in up.process(0f, 0f, 0f, -1f))
    }

    @Test
    fun stickAndHatStatesDoNotInterfere() {
        val stick = AnalogStickState()
        // 摇杆保持推右,方向帽打左:两个状态源互不污染,反向并集共存
        val dirs = stick.process(0.60f, 0f, -1f, 0f)
        assertTrue(RetroButton.RIGHT in dirs)
        assertTrue(RetroButton.LEFT in dirs)
    }

    @Test
    fun resetClearsAllDirections() {
        val stick = AnalogStickState()
        stick.process(0.80f, -0.80f, 1f, 1f)
        stick.reset()
        assertTrue(stick.process(0f, 0f).isEmpty())
    }
}
