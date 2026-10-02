package com.huffcart.app.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 按压反馈开关持久化（pad-feedback-and-display-settings）：SharedPreferences 存两个布尔；
 * 沿用 KeyMappingStore / ControlSchemeStore 的轻量约定——不为两个开关引入 DataStore。
 * 触觉反馈与按键音效缺省均开启（真机手感定位）。
 */
object PadFeedbackStore {

    private const val PREFS_NAME = "pad_feedback"
    private const val KEY_HAPTIC = "haptic_enabled"
    private const val KEY_SOUND = "key_sound_enabled"

    fun loadHaptic(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_HAPTIC, true)

    fun loadSound(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SOUND, true)

    fun saveHaptic(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    fun saveSound(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SOUND, enabled).apply()
    }
}

/**
 * 按压反馈运行时：触觉开关标志 + 程序合成按键音（pad-feedback-and-display-settings 决策 2）。
 * 游戏屏启动时 init 读一次设置（设置页仅主界面可达，改完重进游戏即新值）；音频为
 * AudioTrack MODE_STATIC 静态短脉冲——约 6ms 方波快速衰减，构造时写一次缓冲，按压时回卷重播，
 * 无需素材文件（后续换真素材只替换 playClick 内部实现）；走媒体流与游戏 AudioTrack 并行混音，
 * 音量跟随媒体音量。音轨为进程级单例不释放（静态缓冲仅几百字节）。
 */
object PadFeedback {

    @Volatile
    var hapticEnabled: Boolean = true
        private set

    @Volatile
    var keySoundEnabled: Boolean = true
        private set

    private var clickTrack: AudioTrack? = null

    private const val SAMPLE_RATE = 48_000
    private const val CLICK_FREQ_HZ = 1_800f
    private const val CLICK_DURATION_MS = 6f
    private const val CLICK_DECAY_MS = 1.6f
    private const val CLICK_PEAK = 0.45f

    /** 游戏屏启动时调用：加载开关并准备按键音音轨（已建则复用）。 */
    fun init(context: Context) {
        val appContext = context.applicationContext
        hapticEnabled = PadFeedbackStore.loadHaptic(appContext)
        keySoundEnabled = PadFeedbackStore.loadSound(appContext)
        if (clickTrack == null) {
            runCatching { clickTrack = buildClickTrack() }
        }
    }

    /** 按压时播放一次按键音（开关关闭时静默）。 */
    fun playClick() {
        if (!keySoundEnabled) return
        val track = clickTrack ?: return
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
            track.setPlaybackHeadPosition(0)
            track.play()
        }
    }

    private fun buildClickTrack(): AudioTrack {
        val samples = synthClick()
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            bytes[i * 2] = (s.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (s.toInt() shr 8).toByte()
        }
        track.write(bytes, 0, bytes.size)
        return track
    }

    /** 方波短脉冲 + 指数衰减包络：像按键的「咔哒」而非蜂鸣。 */
    private fun synthClick(): ShortArray {
        val n = (SAMPLE_RATE * CLICK_DURATION_MS / 1000f).toInt()
        val samples = ShortArray(n)
        val period = SAMPLE_RATE / CLICK_FREQ_HZ
        for (i in 0 until n) {
            val tSec = i / SAMPLE_RATE.toFloat()
            val square = if ((i % period) < period / 2f) 1f else -1f
            val env = Math.exp((-tSec * 1000f / CLICK_DECAY_MS).toDouble()).toFloat()
            samples[i] = (square * env * CLICK_PEAK * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }
}

/**
 * 虚拟手柄统一反馈入口（pad-feedback-and-display-settings 决策 3）：按压时按开关触发
 * 触觉与按键音效；换向不重复触发的语义由调用点保证（仅在触点落下时调用）。
 */
@Composable
fun rememberPadFeedback(): () -> Unit {
    val view = LocalView.current
    return remember {
        {
            if (PadFeedback.hapticEnabled) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            PadFeedback.playClick()
        }
    }
}
