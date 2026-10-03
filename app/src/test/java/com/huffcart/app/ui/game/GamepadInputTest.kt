package com.huffcart.app.ui.game

import android.view.KeyEvent
import com.huffcart.core.bridge.RetroButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 手柄输入解析(physical-gamepad 任务 1.1):固定表先行、键盘映射兜底、未映射返回空。 */
class GamepadInputTest {

    private val defaults = KeyMappingStore.defaults()

    @Test
    fun fixedTableResolvesStandardGamepadButtons() {
        assertEquals(RetroButton.A, GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_A, defaults))
        assertEquals(RetroButton.B, GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_B, defaults))
        assertEquals(RetroButton.START, GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_START, defaults))
        assertEquals(RetroButton.SELECT, GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_SELECT, defaults))
    }

    @Test
    fun keyboardFallbackResolvesDefaults() {
        assertEquals(RetroButton.A, GamepadInput.resolve(KeyEvent.KEYCODE_Z, defaults))
        assertEquals(RetroButton.B, GamepadInput.resolve(KeyEvent.KEYCODE_X, defaults))
        assertEquals(RetroButton.UP, GamepadInput.resolve(KeyEvent.KEYCODE_DPAD_UP, defaults))
    }

    @Test
    fun keyboardRebindReplacesOldKey() {
        val mapping = defaults + (RetroButton.A to KeyEvent.KEYCODE_K)
        assertEquals(RetroButton.A, GamepadInput.resolve(KeyEvent.KEYCODE_K, mapping))
        assertNull(GamepadInput.resolve(KeyEvent.KEYCODE_Z, mapping), "原默认键不再驱动 A")
    }

    @Test
    fun unmappedGamepadButtonsProduceNothing() {
        assertNull(GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_X, defaults))
        assertNull(GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_Y, defaults))
        assertNull(GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_L1, defaults))
        assertNull(GamepadInput.resolve(KeyEvent.KEYCODE_BUTTON_MODE, defaults))
    }

    @Test
    fun axisEventFilterMatchesJoystickAndDpadOnly() {
        assertTrue(GamepadInput.isAxisEvent(android.view.InputDevice.SOURCE_JOYSTICK))
        assertTrue(GamepadInput.isAxisEvent(android.view.InputDevice.SOURCE_GAMEPAD))
        assertTrue(GamepadInput.isAxisEvent(android.view.InputDevice.SOURCE_DPAD))
        // 鼠标/触控板/轨迹球类不触发轴处理
        assertFalse(GamepadInput.isAxisEvent(android.view.InputDevice.SOURCE_MOUSE))
        assertFalse(GamepadInput.isAxisEvent(android.view.InputDevice.SOURCE_TRACKBALL))
        assertFalse(GamepadInput.isAxisEvent(android.view.InputDevice.SOURCE_TOUCHSCREEN))
    }

    @Test
    fun directionDiffReleasesRemovedAndPressesAdded() {
        val released = mutableListOf<RetroButton>()
        val pressed = mutableListOf<RetroButton>()
        GamepadInput.applyDirections(
            setOf(RetroButton.UP, RetroButton.LEFT),
            setOf(RetroButton.LEFT, RetroButton.DOWN),
        ) { button, pressedNow ->
            if (pressedNow) pressed.add(button) else released.add(button)
        }
        assertEquals(listOf(RetroButton.UP), released)
        assertEquals(listOf(RetroButton.DOWN), pressed)
    }
}
