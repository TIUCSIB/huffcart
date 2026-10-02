package com.huffcart.app.ui.game

import android.content.Context

/**
 * 画面比例（pad-feedback-and-display-settings）：原生 8:7 为默认（整数倍缩放 + letterbox，
 * 与既往行为一致），经典 4:3 为老电视观感（允许非整数缩放），铺满无视比例填满画面区。
 * 三档绘制均走最近邻（禁平滑插值）。
 */
enum class DisplayAspect(val label: String) {
    NATIVE("原生 8:7"),
    RATIO_4_3("经典 4:3"),
    STRETCH("铺满"),
}

/**
 * 画面比例持久化（pad-feedback-and-display-settings 决策 4）：SharedPreferences 存枚举名；
 * 沿用 ControlSchemeStore 的轻量约定——不为一个枚举引入 DataStore。游戏屏（GameSession）
 * 构造时读取一次；设置页仅主界面可达，改完重进游戏即生效。
 */
object VideoSettingsStore {

    private const val PREFS_NAME = "video_settings"
    private const val KEY_ASPECT = "display_aspect"

    fun load(context: Context): DisplayAspect {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ASPECT, null)
        return raw?.let { runCatching { DisplayAspect.valueOf(it) }.getOrNull() }
            ?: DisplayAspect.NATIVE
    }

    fun save(context: Context, aspect: DisplayAspect) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ASPECT, aspect.name).apply()
    }
}
