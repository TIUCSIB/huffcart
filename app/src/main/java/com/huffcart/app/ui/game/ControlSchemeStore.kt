package com.huffcart.app.ui.game

import android.content.Context

/** 虚拟手柄方向控制形态（virtual-joystick）：十字键为默认，摇杆为可选。 */
enum class ControlScheme { DPAD, JOYSTICK }

/**
 * 控制形态持久化（virtual-joystick 决策 4）：SharedPreferences 存枚举名；
 * 沿用 KeyMappingStore 的轻量约定——不为一个枚举引入 DataStore。
 * 设置页只在主界面可达（游戏屏为独立全屏路由），游戏屏进入时读取一次即可。
 */
object ControlSchemeStore {

    private const val PREFS_NAME = "control_scheme"
    private const val KEY_STYLE = "virtual_pad_style"

    fun load(context: Context): ControlScheme {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, null)
        return raw?.let { runCatching { ControlScheme.valueOf(it) }.getOrNull() }
            ?: ControlScheme.DPAD
    }

    fun save(context: Context, scheme: ControlScheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, scheme.name)
            .apply()
    }
}
