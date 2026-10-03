package com.huffcart.app.ui.game

import android.content.Context

/**
 * 音频延迟三档（audio-latency-tuning）：档位决定 AudioTrack 缓冲深度（[bufferMs]），
 * 阻塞写是帧节拍、缓冲常满——深度即「按键到出声」的端到端延迟。毫秒值为初值，
 * 实施期依真机 underrun 观察可微调（design 决策 2）。
 */
enum class AudioLatency(val label: String, val bufferMs: Int) {
    LOW("低延迟", 40),
    BALANCED("平衡", 70),
    STABLE("稳定", 120),
    ;

    companion object {
        /** 存储值解析：空/非法回落 [BALANCED]（VideoSettingsStore 同款容错，可 JVM 单测）。 */
        fun from(raw: String?): AudioLatency =
            raw?.let { runCatching { valueOf(it) }.getOrNull() } ?: BALANCED
    }
}

/**
 * 声音设置值对象（audio-settings-and-save-management）：游戏音量百分比、静音、快进静音，
 * audio-latency-tuning 增补音频延迟档位。纯逻辑与 SharedPreferences 胶水分离——
 * [gain] / [gainDuringFastForward] 可 JVM 单测。
 */
data class AudioSettings(
    val volumePercent: Int = DEFAULT_VOLUME_PERCENT,
    val muted: Boolean = false,
    val ffMuted: Boolean = false,
    val latencyTier: AudioLatency = AudioLatency.BALANCED,
) {

    /** 输出增益 0f–1f：静音直接 0，否则按百分比缩放（越界值钳制）。 */
    val gain: Float
        get() = if (muted) 0f else volumePercent.coerceIn(0, 100) / 100f

    /**
     * 某帧的输出增益：快进生效且「快进静音」开启时静音，其余按常规增益。
     * 静音只改增益、不停写——AudioTrack 阻塞写是帧节拍（design 决策 1）。
     */
    fun gainDuringFastForward(ffActive: Boolean): Float =
        if (ffActive && ffMuted) 0f else gain

    companion object {
        const val DEFAULT_VOLUME_PERCENT = 100
    }
}

/**
 * 声音设置持久化（audio-settings-and-save-management 决策 2）：SharedPreferences 三键，
 * 沿用 VideoSettingsStore 的轻量约定。游戏屏（GameSession）构造时读取一次；设置页仅
 * 主界面可达，改完重进游戏即生效。
 */
object AudioSettingsStore {

    private const val PREFS_NAME = "audio_settings"
    private const val KEY_VOLUME = "volume_percent"
    private const val KEY_MUTE = "muted"
    private const val KEY_FF_MUTE = "ff_muted"
    private const val KEY_LATENCY = "latency_tier"

    fun load(context: Context): AudioSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AudioSettings(
            volumePercent = prefs.getInt(KEY_VOLUME, AudioSettings.DEFAULT_VOLUME_PERCENT),
            muted = prefs.getBoolean(KEY_MUTE, false),
            ffMuted = prefs.getBoolean(KEY_FF_MUTE, false),
            latencyTier = AudioLatency.from(prefs.getString(KEY_LATENCY, null)),
        )
    }

    fun save(context: Context, settings: AudioSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VOLUME, settings.volumePercent)
            .putBoolean(KEY_MUTE, settings.muted)
            .putBoolean(KEY_FF_MUTE, settings.ffMuted)
            .putString(KEY_LATENCY, settings.latencyTier.name)
            .apply()
    }
}
