package com.huffcart.app.ui.game

import android.content.Context
import android.view.KeyEvent
import com.huffcart.core.bridge.RetroButton

/**
 * 物理键盘映射（retro-ui-redesign 决策 6）：SharedPreferences 存
 * 虚拟键 → keyCode；解析失败回默认，避免为几个 Int 引入 DataStore。
 */
object KeyMappingStore {

    private const val PREFS_NAME = "key_mapping"

    private val DEFAULTS = linkedMapOf(
        RetroButton.UP to KeyEvent.KEYCODE_DPAD_UP,
        RetroButton.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        RetroButton.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        RetroButton.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        RetroButton.A to KeyEvent.KEYCODE_Z,
        RetroButton.B to KeyEvent.KEYCODE_X,
    )

    fun defaults(): Map<RetroButton, Int> = DEFAULTS.toMap()

    fun load(context: Context): Map<RetroButton, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DEFAULTS.mapValues { (button, default) ->
            prefs.getString(button.name, null)?.toIntOrNull() ?: default
        }
    }

    fun save(context: Context, mapping: Map<RetroButton, Int>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                mapping.forEach { (button, keyCode) -> putString(button.name, keyCode.toString()) }
            }
            .apply()
    }

    /** 展示用键名；方向键用箭头符号，其余取 KeyEvent 名去前缀。 */
    fun keyLabel(keyCode: Int): String = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "↑"
        KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
        KeyEvent.KEYCODE_DPAD_LEFT -> "←"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
        else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
    }
}
