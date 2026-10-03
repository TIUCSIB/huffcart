package com.huffcart.app.ui.game

import android.view.InputDevice
import android.view.KeyEvent
import com.huffcart.core.bridge.RetroButton

/** 物理手柄输入(physical-gamepad):固定映射表、键码解析、轴事件判定与方向集增量。 */
object GamepadInput {

    /** 固定映射(决策 1:标签对齐,开箱即玩):仅标准面键与起停键。
     *  十字键不在此表——DPAD_* 落入用户键盘映射表,保住「重绑后原默认键不再驱动」契约;
     *  X/Y/肩键/扳机默认不映射(spec「未映射按键无副作用」)。 */
    private val FIXED = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to RetroButton.A,
        KeyEvent.KEYCODE_BUTTON_B to RetroButton.B,
        KeyEvent.KEYCODE_BUTTON_START to RetroButton.START,
        KeyEvent.KEYCODE_BUTTON_SELECT to RetroButton.SELECT,
    )

    /** 键码解析(决策 1):固定表先行,用户键盘映射表兜底——单一查找顺序,不做来源判断。 */
    fun resolve(keyCode: Int, keyboardMapping: Map<RetroButton, Int>): RetroButton? {
        FIXED[keyCode]?.let { return it }
        return keyboardMapping.entries.firstOrNull { it.value == keyCode }?.key
    }

    /** 摇杆类事件判定(决策 2):摇杆类设备或方向帽来源;鼠标/触控板/轨迹球不含这些类。 */
    fun isAxisEvent(source: Int): Boolean =
        source and InputDevice.SOURCE_CLASS_JOYSTICK != 0 || source and InputDevice.SOURCE_DPAD != 0

    /** 方向集增量:先复位移除的,再按住新增的(与 netplay ButtonMask.diff 同思路)。 */
    fun applyDirections(
        previous: Set<RetroButton>,
        current: Set<RetroButton>,
        onButton: (RetroButton, Boolean) -> Unit,
    ) {
        (previous - current).forEach { onButton(it, false) }
        (current - previous).forEach { onButton(it, true) }
    }
}

/** 轴事件枢纽(physical-gamepad 决策 2):游戏屏组合期注册、退出注销;
 *  MainActivity.dispatchGenericMotionEvent 摇杆类事件经此转发,无接收器时透传。 */
object GamepadAxisHub {

    @Volatile
    var listener: ((android.view.MotionEvent) -> Boolean)? = null
}
