package com.huffcart.core.bridge

/**
 * 模拟核心的宿主契约。
 *
 * 生命周期约定：
 * 1. 宿主完成装配（真实实现为核心库加载与回调注册，装配方式随实现而定）；
 * 2. [loadRom] 成功后核心进入就绪态；
 * 3. 宿主以恒定节拍调用 [runFrame]（FC 为 ~60Hz），每帧取回一帧视频缓冲与
 *    本帧新增的音频采样（有效长度见 [Frame.audioSamples]）；
 * 4. 输入事件随时通过 [setButton] 注入，核心在下一帧生效；
 * 5. [reset] 软复位；[unload] 卸载当前游戏（电池存档由实现负责持久化）。
 *
 * 当前真实实现为 core-native 模块的 LibretroCore（JNI + FCEUmm，
 * need_fullpath 路径加载模式——详见该 change 的 design）。
 * 接口签名是两端接缝，调整须与对应 change 的设计同步，避免契约漂移。
 */
interface RetroCore {
    /** 核心显示名，如 "FCEUmm"。 */
    val name: String

    fun loadRom(romPath: String): LoadResult

    fun runFrame(): Frame

    fun setButton(player: Int, button: RetroButton, pressed: Boolean)

    fun reset()

    fun unload()
}

enum class LoadResult { OK, INVALID_ROM, UNSUPPORTED_MAPPER, ERROR }

data class VideoInfo(val width: Int, val height: Int)

data class AudioInfo(val sampleRate: Int)

/**
 * 一帧的产出：视频为 ARGB 像素缓冲（尺寸见 [VideoInfo]），
 * 音频为本帧新增采样，有效长度 [audioSamples]（单位：short，双声道交错）。
 */
class Frame(
    val video: IntArray,
    val videoInfo: VideoInfo,
    val audio: ShortArray,
    val audioInfo: AudioInfo,
    val audioSamples: Int = 0,
)

/** FC 标准手柄按键（1P/2P 由 [RetroCore.setButton] 的 player 参数区分）。 */
enum class RetroButton { A, B, SELECT, START, UP, DOWN, LEFT, RIGHT }
