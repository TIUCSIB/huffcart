package com.huffcart.core.libretro

import com.huffcart.core.bridge.AudioInfo
import com.huffcart.core.bridge.Frame
import com.huffcart.core.bridge.LoadResult
import com.huffcart.core.bridge.RetroButton
import com.huffcart.core.bridge.RetroCore
import com.huffcart.core.bridge.VideoInfo

/**
 * RetroCore 的 libretro 实装：经 JNI shim（libcorenative.so）驱动
 * dlopen 的核心库（libfceumm_libretro.so）。
 *
 * 线程纪律：[attach]、[loadRom]、[runFrame]、[unload]、[deinit] 及其触发的
 * 全部核心回调必须串行在同一个游戏线程上（见 shim.c 文件头）；UI 层负责安排。
 */
class LibretroCore : RetroCore {

    override val name: String = "FCEUmm"

    companion object {
        init {
            System.loadLibrary("corenative")
        }
    }

    // 位掩码布局与 libretro 的 RETRO_DEVICE_ID_JOYPAD_* 对齐：bit = 1 << id
    private val inputMasks = IntArray(4)

    // 逐帧复用缓冲；容量覆盖 FC 的 256×240 并留放大分辨率兜底
    private val videoBuffer = IntArray(512 * 512)
    private val audioBuffer = ShortArray(32 * 1024)
    private val frameInfo = IntArray(2)
    private val timingOut = DoubleArray(2)

    private var attached = false
    private var loaded = false
    private var fps = 60.0
    private var sampleRate = 48000.0

    // internal 会被 Kotlin 改名混淆，JNI up-call 需要稳定名字，用 @JvmName 钉住
    @JvmName("getInputMaskFromNative")
    internal fun getInputMaskFromNative(player: Int): Int =
        inputMasks.getOrNull(player) ?: 0

    /** 装配：加载核心库 → 注册回调 → retro_init → 绑定逐帧缓冲。 */
    fun attach(coreLibPath: String, systemDir: String, saveDir: String) {
        check(!attached) { "重复 attach" }
        if (!nativeLoadCore(coreLibPath)) {
            error("核心库加载失败: $coreLibPath")
        }
        nativeInit(systemDir, saveDir)
        nativeSetBuffers(videoBuffer, audioBuffer)
        attached = true
    }

    override fun loadRom(romPath: String): LoadResult {
        check(attached) { "attach 尚未调用" }
        check(!loaded) { "上一局尚未 unload" }
        if (!nativeLoadGame(romPath)) return LoadResult.INVALID_ROM
        nativeGetTiming(timingOut)
        fps = timingOut[0]
        sampleRate = timingOut[1]
        loaded = true
        return LoadResult.OK
    }

    override fun runFrame(): Frame {
        check(loaded) { "runFrame before loadRom" }
        val audioShorts = nativeRunFrame(frameInfo)
        return Frame(
            video = videoBuffer,
            videoInfo = VideoInfo(frameInfo[0], frameInfo[1]),
            audio = audioBuffer,
            audioInfo = AudioInfo(sampleRate.toInt()),
            audioSamples = audioShorts,
        )
    }

    fun fps(): Double = fps

    fun sampleRateInt(): Int = sampleRate.toInt()

    /** 电池存档原始数据（RETRO_MEMORY_SAVE_RAM），无 SRAM 时返回 null。 */
    fun getSram(): ByteArray? = nativeGetMemory(0)

    fun setSram(data: ByteArray): Boolean = nativeSetMemory(0, data)

    /**
     * 即时存档：完整模拟状态快照（核心私有格式）；失败或核心不支持返回 null。
     * 须在游戏线程调用（与 runFrame 同线程）。
     */
    fun saveState(): ByteArray? = nativeSaveState()

    /** 恢复状态快照；核心拒收（大小/版本不符）返回 false。须在游戏线程调用。 */
    fun loadState(data: ByteArray): Boolean = nativeLoadState(data)

    /**
     * 金手指批应用（cheat-codes）：reset 清空码集后逐条 set(enabled)，
     * 核心此后每帧自行应用。须在游戏线程调用；核心不支持时返回 false。
     */
    fun applyCheats(codes: List<String>): Boolean = nativeApplyCheats(codes.toTypedArray())

    override fun setButton(player: Int, button: RetroButton, pressed: Boolean) {
        val bit = when (button) {
            RetroButton.B -> 1 shl 0
            RetroButton.SELECT -> 1 shl 2
            RetroButton.START -> 1 shl 3
            RetroButton.UP -> 1 shl 4
            RetroButton.DOWN -> 1 shl 5
            RetroButton.LEFT -> 1 shl 6
            RetroButton.RIGHT -> 1 shl 7
            RetroButton.A -> 1 shl 8
        }
        val p = if (player in inputMasks.indices) player else 0
        inputMasks[p] =
            if (pressed) inputMasks[p] or bit else inputMasks[p] and bit.inv()
    }

    override fun reset() {
        if (loaded) nativeReset()
    }

    override fun unload() {
        if (loaded) {
            nativeUnloadGame()
            loaded = false
        }
    }

    /** 完整释放：卸载游戏 → retro_deinit → dlclose。之后不可再用。 */
    fun deinit() {
        unload()
        if (attached) {
            nativeDeinit()
            attached = false
        }
    }

    private external fun nativeLoadCore(coreLibPath: String): Boolean
    private external fun nativeInit(systemDir: String, saveDir: String)
    private external fun nativeSetBuffers(video: IntArray, audio: ShortArray)
    private external fun nativeLoadGame(romPath: String): Boolean
    private external fun nativeRunFrame(infoOut: IntArray): Int
    private external fun nativeReset()
    private external fun nativeUnloadGame()
    private external fun nativeDeinit()
    private external fun nativeGetTiming(out: DoubleArray)
    private external fun nativeGetMemory(region: Int): ByteArray?
    private external fun nativeSetMemory(region: Int, data: ByteArray): Boolean
    private external fun nativeSaveState(): ByteArray?
    private external fun nativeLoadState(data: ByteArray): Boolean
    private external fun nativeApplyCheats(codes: Array<String>): Boolean
}
