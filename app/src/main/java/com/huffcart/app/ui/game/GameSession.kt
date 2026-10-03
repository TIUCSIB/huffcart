package com.huffcart.app.ui.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import com.huffcart.app.netplay.ButtonMask
import com.huffcart.app.netplay.NetplayCodec
import com.huffcart.app.netplay.NetplayGameEvent
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.netplay.Seat
import com.huffcart.app.netplay.crc32OfVideo
import com.huffcart.app.ui.library.CoverStore
import com.huffcart.core.bridge.Frame
import com.huffcart.core.bridge.LoadResult
import com.huffcart.core.bridge.RetroButton
import com.huffcart.core.libretro.LibretroCore
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** 一次运行会话：装配核心 → 加载 ROM → 游戏线程（音频节拍 + 渲染）→ 停止回收。
 *  netplay 非空时进入联机态（netplay-lan）：房主保持音频节拍并广播输入，
 *  加入端以主机输入流为节拍、音频改非阻塞写（design 决策 1/5）。
 *  原 GameScreen.kt 私有类整体迁出（rewind-and-session-split 任务 1.1，纯移动零行为变化）。 */
internal class GameSession(
    context: Context,
    private val romName: String,
    private val netplay: com.huffcart.app.netplay.NetplayGameSetup? = null,
) {

    /** 截图封面落盘走 appContext（异步消费可能晚于会话结束，避免持有 Activity）。 */
    private val appContext = context.applicationContext

    /** 库/详情展示名（不含扩展名），同时是封面规范化键的输入。 */
    private val displayName = romName.substringBeforeLast('.')

    val core = LibretroCore()

    @Volatile
    private var running = false
    private var loopThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    // ---- 联机输入状态（UI 线程写 / 游戏线程读） ----

    private companion object {
        /** 加入端保活间隔（帧）：约 0.5s，房主读超时以此判定对端死亡。 */
        const val JOINER_KEEPALIVE_FRAMES = 30

        /** 截图封面时机（帧）：~60fps 下 480 帧 ≈ 8 秒，避开纯黑开机画面。 */
        const val CAPTURE_FRAME = 480

        /** 倒带采样间隔（模拟帧）：每 3 帧一采样 ≈ 20 采样/s（rewind-and-session-split 决策 1）。 */
        const val REWIND_SAMPLE_FRAMES = 3
    }

    @Volatile
    private var localP1Mask = 0

    /** 加入端本机席位（P2/P3/P4）掩码；UI 线程写 / 游戏线程读。 */
    @Volatile
    private var localSeatMask = 0

    /** 待应用的金手指码（cheat-codes）：UI 线程写 / 游戏线程读。 */
    @Volatile
    private var cheatCodesToApply: List<String> = emptyList()

    // ---- 连发（turbo-and-auto-resume）：开关会话内生效；A/B 用户按住镜像始终维护，
    //      连发开启时 A/B 由游戏循环按帧相位驱动（不在事件路径做相位——半周期置位无法撤回） ----

    /** 连发开关：UI 菜单写 / 游戏线程读；不持久化（小霸王 TURBO 语义），默认关。 */
    @Volatile
    var turboEnabled: Boolean = false

    @Volatile
    private var userA = false

    @Volatile
    private var userB = false

    fun setTurbo(enabled: Boolean) { turboEnabled = enabled }

    // ---- 倒带（rewind-and-session-split）：环形缓冲 + 按住开关（决策 2/3） ----

    /** 倒带缓冲：游戏线程独占读写（采样/回退均在游戏线程，序列化同 retro_run 线程约束）。 */
    private val rewindBuffer = RewindBuffer()

    /** 按住倒带：UI 菜单按住置位/松开复位（决策 5）；游戏线程每墙钟帧读。联机中入口隐藏，
     *  循环内再以 netplayLive 兜底（spec「联机中不可用」）。 */
    @Volatile
    var rewindHeld: Boolean = false

    // ---- 断点续玩：退出序列在游戏线程保存挂起档；重进由 UI 询问恢复或重新开始 ----

    /** 挂起档是否存在（构造时判定，供 UI 决定是否呈现续玩询问）。 */
    val suspendAvailable: Boolean
        get() = suspendFile.isFile

    @Volatile
    private var suspendRequested = false

    private var suspendSaved = false // 游戏线程私有

    fun requestResumeSuspend() { pendingCommand.set(ResumeSuspendCmd) }

    /** 选择「重新开始」：清除挂起档，本次会话照常从开机状态运行。 */
    fun discardSuspend() { suspendFile.delete() }

    private val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
    val savesDir = File(context.filesDir, SaveSlotStore.DIR_NAME).apply { mkdirs() }
    private val systemDir = File(context.filesDir, "system").apply { mkdirs() }
    private val romFile = romsDir.resolve(romName)
    private val coreLib = File(context.applicationInfo.nativeLibraryDir, "libfceumm_libretro.so")
    private val srmFile = savesDir.resolve(romName.removeSuffix(".nes") + ".srm")

    /** 挂起档（turbo-and-auto-resume「断点续玩」）：独立于槽位（.stateN）与 SRAM 的退出快照。 */
    private val suspendFile = savesDir.resolve(SaveSlotStore.baseName(romName) + ".resume")

    /** 声音设置（audio-settings-and-save-management）：会话构造时读一次，进游戏生效。 */
    private val audio = AudioSettingsStore.load(context)

    /** 初始金手指（cheat-codes）：构造时读已启用的码，游戏线程 loop 起始注入（联机排除）。 */
    private val initialCheats: List<String> =
        CheatStore.load(context, SaveSlotStore.baseName(romName))
            .filter { it.enabled }
            .map { it.code }

    /** 画面比例（pad-feedback-and-display-settings）：会话构造时读一次；设置页仅主界面可达，
     *  改完重进游戏即新值。 */
    private val aspect = VideoSettingsStore.load(context)

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceRef.set(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceRef.set(holder)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // 与渲染循环互斥：确保回调返回时游戏线程已画完当前帧并放弃 canvas，
            // 否则框架销毁 surface 与 lockCanvas 竞争会阻塞主线程（横竖屏切换时 ANR）
            synchronized(surfaceMutex) {
                surfaceRef.compareAndSet(holder, null)
            }
        }
    }

    private val surfaceRef = AtomicReference<SurfaceHolder?>(null)

    /** 渲染帧与 surface 销毁的互斥锁（见 surfaceDestroyed 注释）。 */
    private val surfaceMutex = Object()

    // ---- 即时存档 / 快进（save-states / fast-forward 能力） ----

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 游戏线程执行结果经主线程回调给 UI 展示反馈。 */
    var onEvent: ((String) -> Unit)? = null

    /** 快进倍率（1/2/3）；UI 线程写，游戏线程每墙钟帧读取并钳制。 */
    @Volatile
    var ffFactor: Int = 1

    fun requestSaveState(slot: Int) { pendingCommand.set(SaveStateCmd(slot)) }

    fun requestLoadState(slot: Int) { pendingCommand.set(LoadStateCmd(slot)) }

    /** 金手指批应用（cheat-codes）：UI 传全部已启用码，游戏线程 reset+逐条 set。 */
    fun requestApplyCheats(codes: List<String>) {
        cheatCodesToApply = codes
        pendingCommand.set(ApplyCheatsCmd)
    }

    /** 本机物理输入入口（触控 + 键盘）：单机写 P1；联机按席位映射（netplay-lobby-v2）。 */
    fun onLocalButton(button: RetroButton, pressed: Boolean) {
        val np = netplay
        if (np == null) {
            // A/B 镜像始终维护（连发开启/关闭切换时能还原真实按住状态）
            if (button == RetroButton.A) userA = pressed
            if (button == RetroButton.B) userB = pressed
            // 连发开启时 A/B 交给游戏循环按帧相位驱动
            if (turboEnabled && (button == RetroButton.A || button == RetroButton.B)) return
            core.setButton(0, button, pressed)
            return
        }
        val bit = ButtonMask.bit(button)
        when (np.role) {
            Seat.P1 -> {
                // 房主：本机即 P1（权威端，零延迟注入），掩码用于每帧广播
                core.setButton(0, button, pressed)
                localP1Mask = if (pressed) localP1Mask or bit else localP1Mask and bit.inv()
            }
            else -> {
                // 加入端（P2/P3/P4）：核心输入完全来自主机回传——本地只上送本席位
                localSeatMask = if (pressed) localSeatMask or bit else localSeatMask and bit.inv()
                NetplayManager.sendJoinerMask(localSeatMask)
            }
        }
    }

    private fun notifyEvent(message: String) {
        mainHandler.post { onEvent?.invoke(message) }
    }

    /** 游戏线程上报不可恢复错误（如联机开局失败）：UI 转入错误态。 */
    private fun notifyFatal(message: String) {
        mainHandler.post { onFatal?.invoke(message) }
    }

    /** 由游戏线程（loop 内）设置；GameScreen 组合期挂接。 */
    var onFatal: ((String) -> Unit)? = null

    private sealed interface SessionCommand
    private data class SaveStateCmd(val slot: Int) : SessionCommand
    private data class LoadStateCmd(val slot: Int) : SessionCommand
    private data object ApplyCheatsCmd : SessionCommand
    private data object ResumeSuspendCmd : SessionCommand
    private val pendingCommand = AtomicReference<SessionCommand?>(null)

    fun start() {
        check(!running) { "会话已在运行" }
        core.attach(coreLib.absolutePath, systemDir.absolutePath, savesDir.absolutePath)
        // 金手指注入须在 loadRom 之前：fceumm 家族的 GG 替换读取钩子在游戏加载时构建，
        // 加载后追加的码不生效（真机实测）。联机不注入（spec「联机不可用」）
        if (netplay == null && initialCheats.isNotEmpty()) {
            core.applyCheats(initialCheats)
        }
        when (core.loadRom(romFile.absolutePath)) {
            LoadResult.OK -> Unit
            LoadResult.INVALID_ROM -> throw IllegalStateException("不是有效的 FC ROM")
            else -> throw IllegalStateException("游戏加载失败")
        }
        when (netplay?.role) {
            // 加入端（P2/P3/P4）：跳过本地 SRAM（避免与房主分叉）；快照对齐在游戏线程做
            Seat.P2, Seat.P3, Seat.P4 -> Unit
            // 房主：注入本地 SRAM，快照对齐在游戏线程做（loop 开头，避免阻塞主线程）
            Seat.P1 -> srmFile.takeIf { it.exists() }?.let { core.setSram(it.readBytes()) }
            // 单机：照旧注入电池存档
            null -> srmFile.takeIf { it.exists() }?.let { core.setSram(it.readBytes()) }
        }

        // fceumm 在变量解析前 av_info 报 0Hz，首帧后回落 48000 默认——直接对齐
        val rate = core.sampleRateInt().takeIf { it in 8000..96_000 } ?: 48_000
        val minBuf = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    // 无 CONTENT_TYPE_GAME（该常量不存在于 SDK），游戏音频按 MUSIC 走
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, rate / 10 * 4)) // ≈100ms
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        // 音量/静音（audio-settings-and-save-management 决策 1）：只改增益不停写——
        // AudioTrack 阻塞写是帧节拍；快进静音由游戏循环按帧联动
        audioTrack?.setVolume(audio.gain)

        running = true
        loopThread = thread(name = "game-loop") { loop() }
    }

    /** 画面区内居中缩放矩形（pad-feedback-and-display-settings）：NATIVE 整数倍 letterbox
     *  （原行为，像素完美）；RATIO_4_3 按 4:3 盒适配（允许非整数缩放，老电视观感）；
     *  STRETCH 铺满画面区（不保持比例）。三档均走最近邻 paint（isFilterBitmap = false），
     *  非整数档位像素宽度轻微不均为可接受取舍（主流模拟器同做法）。 */
    private fun scaledRect(canvasW: Int, canvasH: Int, frameW: Int, frameH: Int): RectF {
        return when (aspect) {
            DisplayAspect.NATIVE -> {
                val scale = minOf(canvasW / frameW, canvasH / frameH).coerceAtLeast(1)
                val dw = frameW * scale.toFloat()
                val dh = frameH * scale.toFloat()
                RectF((canvasW - dw) / 2f, (canvasH - dh) / 2f, (canvasW + dw) / 2f, (canvasH + dh) / 2f)
            }
            DisplayAspect.RATIO_4_3 -> {
                val target = 4f / 3f
                val dw: Float
                val dh: Float
                if (canvasW.toFloat() / canvasH > target) {
                    dh = canvasH.toFloat()
                    dw = dh * target
                } else {
                    dw = canvasW.toFloat()
                    dh = dw / target
                }
                RectF((canvasW - dw) / 2f, (canvasH - dh) / 2f, (canvasW + dw) / 2f, (canvasH + dh) / 2f)
            }
            DisplayAspect.STRETCH -> RectF(0f, 0f, canvasW.toFloat(), canvasH.toFloat())
        }
    }

    private fun loop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        var bitmap: Bitmap? = null
        val paint = android.graphics.Paint().apply { isFilterBitmap = false }
        var framesSinceMark = 0
        var lastMark = SystemClock.elapsedRealtime()
        // 当前已应用增益（NaN 驱动首帧应用一次；快进进入/退出时随 desiredGain 切换）
        var appliedGain = Float.NaN
        audioTrack?.play()

        // 截图封面（game-cover-art 任务 2.3）：无任何封面时，开局约 8 秒截取一帧存 PNG。
        // 标题画面也是可识别封面；只截一次，落盘后下次会话不再截。
        var captureCover = !CoverStore.hasAnyDisplayCover(appContext, displayName)

        val np = netplay
        var netplayLive = np != null
        // ---- 联机开局握手（游戏线程；序列化须与 retro_run 同线程）----
        if (np != null) {
            when (np.role) {
                Seat.P1 -> {
                    val snapshot = core.saveState()
                    if (snapshot == null) {
                        notifyFatal("无法创建联机开局快照")
                        return
                    }
                    NetplayManager.onHostSnapshotReady(snapshot)
                    if (!NetplayManager.awaitJoinerGameReady(15_000)) {
                        notifyFatal("对方未就绪，联机已取消")
                        return
                    }
                }
                else -> {
                    // 加入端（P2/P3/P4）：加载房主快照，从同一状态起跑
                    val state = np.joinerState
                    if (state == null || !core.loadState(state)) {
                        notifyFatal("联机开局同步失败")
                        return
                    }
                    NetplayManager.onJoinerGameLoaded()
                }
            }
        }
        var frameNo = 0
        // 各席位最后应用的掩码（索引 = Seat.ordinal；房主侧 P2–P4 / 加入端全席位）
        val lastAppliedMasks = IntArray(4)
        // 连发驱动状态（游戏线程私有）：记录上次驱动到的 A/B 相位，关闭时还原用户真实状态
        var drivenA = false
        var drivenB = false
        // 倒带状态（游戏线程私有）：本次按住期间的耗尽闩（松开复位）与采样计帧
        var rewindExhausted = false
        var framesSinceSample = 0
        // 断点续玩：stop() 置 suspendRequested（先于 running=false），loop 顶部在游戏线程
        // 保存挂起档后退出——放顶部保证 surface 缺失等提前 continue 路径也能完成保存
        while (running || suspendRequested) {
            if (suspendRequested && !suspendSaved) {
                suspendSaved = true
                val snapshot = core.saveState()
                if (snapshot != null) {
                    runCatching { suspendFile.writeBytes(snapshot) }
                }
            }
            if (!running) break
            // ---- 联机帧首：装配本帧核心输入（netplay-lobby-v2：四席位）----
            if (netplayLive && np != null) {
                when (np.role) {
                    Seat.P1 -> {
                        if (!NetplayManager.isHostLinked()) {
                            // 全部加入端断开：清 P2–P4 注入，无缝回到单机（spec「断线处理」）
                            netplayLive = false
                            for (seat in Seat.entries) {
                                if (seat == Seat.P1) continue
                                val prev = lastAppliedMasks[seat.ordinal]
                                lastAppliedMasks[seat.ordinal] = 0
                                ButtonMask.diff(prev, 0,
                                    { }, { core.setButton(seat.ordinal, it, false) })
                            }
                            NetplayManager.postGameEvent(NetplayGameEvent.Message("对方已断开，回到单机"))
                        } else {
                            val remote = NetplayManager.latestRemoteMasks()
                            for (seat in Seat.entries) {
                                if (seat == Seat.P1) continue
                                val target = remote[seat] ?: 0
                                val prev = lastAppliedMasks[seat.ordinal]
                                if (prev != target) {
                                    ButtonMask.diff(prev, target,
                                        { core.setButton(seat.ordinal, it, true) },
                                        { core.setButton(seat.ordinal, it, false) })
                                    lastAppliedMasks[seat.ordinal] = target
                                }
                            }
                        }
                    }
                    else -> {
                        // 加入端节拍：阻塞等主机第 N 帧输入；超时即断（读超时 3s）
                        val input = NetplayManager.awaitJoinerInput(3_000)
                        if (input == null) {
                            println("[StopSeq] joiner loop: input timeout, exiting")
                            netplayLive = false
                            NetplayManager.postGameEvent(NetplayGameEvent.Message("连接超时，联机已断开"))
                            NetplayManager.postGameEvent(NetplayGameEvent.Ended(exitGame = true))
                            running = false
                            break
                        }
                        val masks = intArrayOf(
                            input.p1Mask, input.p2Mask, input.p3Mask, input.p4Mask,
                        )
                        for (seat in Seat.entries) {
                            val target = masks[seat.ordinal]
                            val prev = lastAppliedMasks[seat.ordinal]
                            if (prev != target) {
                                ButtonMask.diff(prev, target,
                                    { core.setButton(seat.ordinal, it, true) },
                                    { core.setButton(seat.ordinal, it, false) })
                                lastAppliedMasks[seat.ordinal] = target
                            }
                        }
                    }
                }
            }

            // 连发驱动（turbo-and-auto-resume）：开启时 A/B 按帧相位合成（60fps 下每 2 帧翻转
            // = 15Hz），仅在用户按住期间产生输入；关闭时把 A/B 还原为用户真实按住状态
            if (!netplayLive) {
                if (turboEnabled) {
                    val phase = frameNo / 2 % 2 == 1
                    val a = userA && phase
                    val b = userB && phase
                    if (a != drivenA) { core.setButton(0, RetroButton.A, a); drivenA = a }
                    if (b != drivenB) { core.setButton(0, RetroButton.B, b); drivenB = b }
                } else {
                    if (userA != drivenA) { core.setButton(0, RetroButton.A, userA); drivenA = userA }
                    if (userB != drivenB) { core.setButton(0, RetroButton.B, userB); drivenB = userB }
                }
            }

            // 倒带（rewind-and-session-split 决策 1）：按住期间不正常推进——弹出采样恢复后
            // 跑 1 帧取音画，净回退约 2 模拟帧/墙钟帧；耗尽即闩，从当前点正常推进并提示
            // （spec「缓冲耗尽」）；联机中不生效（入口已隐藏，此处兜底）
            if (!rewindHeld) rewindExhausted = false
            val rewinding = rewindHeld && !netplayLive && !rewindExhausted
            var rewindTick = false
            if (rewinding) {
                val state = rewindBuffer.pop()
                if (state != null && runCatching { core.loadState(state) }.getOrDefault(false)) {
                    rewindTick = true
                } else {
                    rewindExhausted = true
                    notifyEvent("已到倒带起点")
                }
            }

            // 快进：每墙钟帧连跑 factor 个模拟帧，只渲染/写末帧的音画；
            // 联机中钳制为 1x（双方节拍必须一致，spec「联机期间限制」）；
            // 倒带帧强制 1 个模拟帧（恢复态的前进刷新，决策 1）——快进与倒带互斥
            val factor = if (netplayLive || rewindTick) 1 else ffFactor.coerceIn(1, 3)
            var frame: Frame? = null
            var failed = false
            for (i in 0 until factor) {
                frame = try {
                    core.runFrame()
                } catch (t: Throwable) {
                    failed = true
                    Log.e("GameLoop", "runFrame failed at frameNo=$frameNo", t)
                    break
                }
            }
            if (failed) break
            val current = frame ?: break
            frameNo += factor

            // 倒带采样：每 REWIND_SAMPLE_FRAMES 模拟帧采样入环（决策 1/2）；倒带帧不采样
            // （回放的时间线是瞬态），读档/续玩的时间线切换已在命令分支清缓冲
            framesSinceSample += factor
            if (!rewindTick && framesSinceSample >= REWIND_SAMPLE_FRAMES) {
                framesSinceSample = 0
                core.saveState()?.let { rewindBuffer.push(it) }
            }

            // 会话命令在游戏线程帧末执行（序列化须与 retro_run 同线程）
            when (val cmd = pendingCommand.getAndSet(null)) {
                is SaveStateCmd -> {
                    val data = core.saveState()
                    if (data != null) {
                        val existed = SaveSlotStore.stateFile(savesDir, romName, cmd.slot).exists()
                        SaveSlotStore.writeState(savesDir, romName, cmd.slot, data)
                        captureSlotThumbnail(current, cmd.slot)
                        notifyEvent(
                            if (existed) {
                                "已覆盖保存到槽位 ${cmd.slot + 1}"
                            } else {
                                "已存档到槽位 ${cmd.slot + 1}"
                            },
                        )
                    } else {
                        notifyEvent("存档失败")
                    }
                }
                is LoadStateCmd -> {
                    val file = SaveSlotStore.stateFile(savesDir, romName, cmd.slot)
                    val ok = file.exists() &&
                        runCatching { core.loadState(file.readBytes()) }.getOrDefault(false)
                    // 时间线切换：倒带缓冲以新时间点重建（spec「读档后缓冲重建」）
                    if (ok) rewindBuffer.clear()
                    notifyEvent(if (ok) "已读档（槽位 ${cmd.slot + 1}）" else "暂无存档")
                }
                ApplyCheatsCmd -> {
                    val ok = core.applyCheats(cheatCodesToApply)
                    notifyEvent(if (ok) "金手指已更新" else "核心不支持金手指")
                }
                ResumeSuspendCmd -> {
                    val ok = suspendFile.isFile &&
                        runCatching { core.loadState(suspendFile.readBytes()) }.getOrDefault(false)
                    if (ok) {
                        suspendFile.delete()
                        rewindBuffer.clear() // 时间线切换：倒带缓冲重建
                        notifyEvent("已恢复上次进度")
                    } else {
                        // 挂起档失效：清除，游戏保持从开机状态继续
                        suspendFile.delete()
                    }
                }
                null -> Unit
            }

            // 音频节拍：blocking write 把循环钉在核心采样率上；
            // 快进时被跳过帧的音频不写入（仅末帧一份），节拍仍为每墙钟帧一次；
            // 加入端改非阻塞写——节拍来自主机输入流，写满只能丢（短促杂音可接受）
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // 音量/快进/倒带静音（audio-settings-and-save-management 决策 1 +
                    // rewind spec「倒带期间静音」）：只改增益，绝不 pause/停写——
                    // AudioTrack 阻塞写是帧节拍；倒带强制静音，独立于快进静音开关
                    val desiredGain = if (rewindTick) 0f else audio.gainDuringFastForward(factor > 1)
                    if (desiredGain != appliedGain) {
                        track.setVolume(desiredGain)
                        appliedGain = desiredGain
                    }
                    val n = current.audioSamples.coerceIn(0, current.audio.size)
                    val mode = if (netplayLive && np?.role == Seat.P2) {
                        AudioTrack.WRITE_NON_BLOCKING
                    } else {
                        AudioTrack.WRITE_BLOCKING
                    }
                    if (n > 0) track.write(current.audio, 0, n, mode)
                }
            }
            // 渲染：按画面比例设置缩放（NATIVE 整数倍 letterbox / 4:3 / 铺满，最近邻）；
            // canvas 为所在区域的实际尺寸，分区/全屏自适应
            val holder = surfaceRef.get()
            if (holder == null) {
                Thread.sleep(8)
                continue
            }
            val surface: Surface = holder.surface
            if (surface == null || !surface.isValid) {
                Thread.sleep(8)
                continue
            }
            val w = current.videoInfo.width
            val h = current.videoInfo.height
            val bmp = bitmap
                ?.takeIf { it.width == w && it.height == h }
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }
            bmp.setPixels(current.video, 0, w, 0, 0, w, h)

            // 截图封面：原生分辨率帧复制一份交给 IO 线程压缩落盘，游戏线程只付 ~1ms 拷贝
            if (captureCover && frameNo >= CAPTURE_FRAME) {
                captureCover = false
                bmp.copy(Bitmap.Config.ARGB_8888, false)?.let { shot ->
                    CoverStore.saveScreenshotAsync(appContext, displayName, shot)
                }
            }

            // lock/unlock 全程持锁，surfaceDestroyed 等本块结束才放行框架销毁
            synchronized(surfaceMutex) {
                runCatching {
                    val canvas = holder.lockCanvas() ?: return@synchronized
                    try {
                        canvas.drawColor(android.graphics.Color.BLACK)
                        canvas.drawBitmap(bmp, null, scaledRect(canvas.width, canvas.height, w, h), paint)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
                    }
                }
            }

            // ---- 联机帧末：广播/校验（帧号与 CRC 见协议层）----
            if (netplayLive && np != null) {
                val w = current.videoInfo.width
                val h = current.videoInfo.height
                when (np.role) {
                    Seat.P1 -> {
                        val crc = if (NetplayCodec.isChecksumFrame(frameNo)) {
                            crc32OfVideo(current.video, w, h)
                        } else {
                            null
                        }
                        NetplayManager.sendHostInput(
                            frameNo, localP1Mask,
                            lastAppliedMasks[Seat.P2.ordinal],
                            lastAppliedMasks[Seat.P3.ordinal],
                            lastAppliedMasks[Seat.P4.ordinal],
                            crc,
                        )
                    }
                    else -> {
                        if (NetplayCodec.isChecksumFrame(frameNo)) {
                            np.joinSession?.verifyFrame(crc32OfVideo(current.video, w, h))
                        }
                        // 保活：掩码不变也定期上送，房主靠它判定断线（design 决策 5）
                        if (frameNo % JOINER_KEEPALIVE_FRAMES == 0) {
                            NetplayManager.sendJoinerMask(localSeatMask)
                        }
                    }
                }
            }

            framesSinceMark += factor
            val now = SystemClock.elapsedRealtime()
            if (now - lastMark >= 5000) {
                Log.d("GameLoop", "emulation fps=${framesSinceMark * 1000 / (now - lastMark)}")
                framesSinceMark = 0
                lastMark = now
            }
        }
    }

    /** 保存时刻抓帧写槽位缩略图：原生分辨率复制一份交 IO 线程压缩落盘，游戏线程只付拷贝。 */
    private fun captureSlotThumbnail(frame: Frame, slot: Int) {
        val w = frame.videoInfo.width
        val h = frame.videoInfo.height
        if (w <= 0 || h <= 0) return
        val shot = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        shot.setPixels(frame.video, 0, w, 0, 0, w, h)
        SlotThumbnails.writeAsync(appContext, romName, slot, shot)
    }

    fun stop() {
        println("[StopSeq] stop entry: running=$running loopAlive=${loopThread?.isAlive}")
        if (!running && loopThread == null) return
        // 断点续玩：单机退出即请求挂起（先于 running=false，游戏线程 loop 顶部完成保存）
        if (netplay == null) suspendRequested = true
        running = false
        pendingCommand.set(null)
        // 等满 5s：加入端循环可能阻塞在"等主机输入"（3s 超时）上，必须等
        // 游戏线程彻底退出后才能释放核心——deinit 与 retro_run 并发会 native
        // 崩溃（真机复现：房主退出 → 加入端闪退）
        loopThread?.join(5_000)
        println("[StopSeq] join done: loopAlive=${loopThread?.isAlive}")
        loopThread = null
        audioTrack?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        audioTrack = null
        // SRAM 快照落盘（设计决策 7：退出时导出，重进注入）
        core.getSram()?.let { bytes -> if (bytes.isNotEmpty()) srmFile.writeBytes(bytes) }
        println("[StopSeq] before deinit")
        core.deinit()
        println("[StopSeq] after deinit")
    }
}
