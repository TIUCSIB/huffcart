package com.huffcart.app.ui.game

import com.huffcart.core.bridge.RetroButton

/** 摇杆轴→十字键状态机(physical-gamepad 决策 3):触发阈值 0.55、释放阈值 0.40,
 *  两值之间的迟滞带防止边界抖动;死区由释放阈值覆盖(死区内恒不触发)。
 *  左摇杆与方向帽各自独立维护迟滞状态,避免互相污染;JVM 纯逻辑可测。 */
internal class AnalogStickState {

    private val stickHeld = mutableSetOf<RetroButton>()
    private val hatHeld = mutableSetOf<RetroButton>()

    /** 处理一次轴采样,返回当前应按下的方向集(调用方与上次结果做差)。
     *  轴约定:axisX 正=右、axisY 正=下(负=上),方向帽同。 */
    fun process(axisX: Float, axisY: Float, hatX: Float = 0f, hatY: Float = 0f): Set<RetroButton> {
        applyAxis(stickHeld, axisX, RetroButton.LEFT, RetroButton.RIGHT)
        applyAxis(stickHeld, axisY, RetroButton.UP, RetroButton.DOWN)
        applyAxis(hatHeld, hatX, RetroButton.LEFT, RetroButton.RIGHT)
        applyAxis(hatHeld, hatY, RetroButton.UP, RetroButton.DOWN)
        return stickHeld + hatHeld
    }

    /** 全部方向复位(游戏屏退出时调用)。 */
    fun reset() {
        stickHeld.clear()
        hatHeld.clear()
    }

    private fun applyAxis(held: MutableSet<RetroButton>, value: Float, negative: RetroButton, positive: RetroButton) {
        if (value >= ENGAGE) held.add(positive) else if (value < RELEASE) held.remove(positive)
        if (value <= -ENGAGE) held.add(negative) else if (value > -RELEASE) held.remove(negative)
    }

    private companion object {
        const val ENGAGE = 0.55f
        const val RELEASE = 0.40f
    }
}
