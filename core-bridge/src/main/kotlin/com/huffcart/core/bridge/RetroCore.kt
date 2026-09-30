package com.huffcart.core.bridge

/**
 * 模拟核心的宿主契约（骨架占位）。
 *
 * 生命周期约定：
 * 1. [loadRom] 成功后核心进入就绪态；
 * 2. 宿主以恒定节拍调用 [runFrame]（FC 为 60Hz），每帧取回一帧视频缓冲与本帧新增的音频采样；
 * 3. 输入事件随时通过 [setButton] 注入，核心在下一帧生效；
 * 4. [reset] 软复位；[unload] 释放核心持有的全部资源。
 *
 * 真实实现将由 libretro-playback change 以 JNI + FCEUmm 提供；本接口签名即两端接缝，
 * 调整须与该 change 的设计同步，避免两端契约漂移。
 */
interface RetroCore {
    /** 核心显示名，如 "FCEUmm"。 */
    val name: String

    fun loadRom(rom: ByteArray): LoadResult

    fun runFrame(): Frame

    fun setButton(player: Int, button: RetroButton, pressed: Boolean)

    fun reset()

    fun unload()
}

enum class LoadResult { OK, INVALID_ROM, UNSUPPORTED_MAPPER, ERROR }

data class VideoInfo(val width: Int, val height: Int)

data class AudioInfo(val sampleRate: Int)

/** 一帧的产出：视频为 ARGB 像素缓冲（尺寸见 [VideoInfo]），音频为本帧新增采样。 */
class Frame(
    val video: IntArray,
    val videoInfo: VideoInfo,
    val audio: ShortArray,
    val audioInfo: AudioInfo,
)

/** FC 标准手柄按键（1P/2P 由 [RetroCore.setButton] 的 player 参数区分）。 */
enum class RetroButton { A, B, SELECT, START, UP, DOWN, LEFT, RIGHT }
