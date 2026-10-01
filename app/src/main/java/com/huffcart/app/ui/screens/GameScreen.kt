package com.huffcart.app.ui.screens

import android.content.Context
import android.content.res.Configuration
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
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.huffcart.app.ui.game.KeyMappingStore
import com.huffcart.app.ui.game.PadControlsOverlay
import com.huffcart.app.ui.theme.HcBannerTag
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcGameBlack
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcRedDark
import com.huffcart.app.ui.theme.PixelFontFamily
import com.huffcart.core.bridge.Frame
import com.huffcart.core.bridge.LoadResult
import com.huffcart.core.bridge.RetroButton
import com.huffcart.core.libretro.LibretroCore
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import com.huffcart.app.R

/**
 * 游戏运行屏（game-playback 能力的宿主，retro-ui-redesign 改版）：
 * - 竖屏：上区画面（整数倍缩放 letterbox）+ 下区深色圆角控制面板；横屏：全屏画面 + 半透明浮层
 * - 音频：AudioTrack blocking write 作为帧节拍（design 决策 4）
 * - 输入：PadControls 触控 + 物理键盘映射（KeyMappingStore），两条通路独立写 setButton
 * - 存/读/快进 chips 固定右上（save-states-fastforward，位置不动）
 * - CRT 滤镜接入点：绘制处可叠加 RuntimeShader（API 33+ 特性检测，本期不实现）
 */
@Composable
fun GameScreen(romName: String, onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    var fatal by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var ffRate by remember { mutableStateOf(1) }
    val session = remember(romName) {
        runCatching { GameSession(context, romName) }
            .onFailure { fatal = it.message ?: it.toString() }
            .getOrNull()
    }

    DisposableEffect(romName) {
        session?.let { s ->
            s.onEvent = { msg -> feedback = msg }
            runCatching { s.start() }.onFailure { fatal = it.message ?: it.toString() }
        }
        onDispose { session?.stop() }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2500)
            feedback = null
        }
    }

    // 物理键盘映射（retro-ui-redesign）：keyCode → 虚拟键
    val focusRequester = remember { FocusRequester() }
    val view = androidx.compose.ui.platform.LocalView.current
    // 游戏屏无文本输入，焦点常在组合期尚未就绪导致 requestFocus 空转——
    // 窗口每次重获焦点（含首次）都补请求一次
    DisposableEffect(Unit) {
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) focusRequester.requestFocus()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
    }
    val keyCodeToButton = remember {
        KeyMappingStore.load(context).entries.associate { (button, keyCode) -> keyCode to button }
    }
    val handleButton: (RetroButton, Boolean) -> Unit = { button, pressed ->
        session?.core?.setButton(0, button, pressed)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HcGameBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val button = keyCodeToButton[event.nativeKeyEvent.keyCode]
                if (button == null) {
                    false
                } else {
                    Log.d("GameKeys", "key ${event.nativeKeyEvent.keyCode} → $button")
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount == 0) handleButton(button, true)
                            true
                        }
                        KeyEventType.KeyUp -> {
                            handleButton(button, false)
                            true
                        }
                        else -> false
                    }
                }
            },
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        if (session == null || fatal != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = fatal ?: "会话创建失败",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                TextButton(onClick = onExit) { Text(text = "返回", color = HcRed) }
            }
        } else {
            val isPortrait =
                LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
            if (isPortrait) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    holder.addCallback(session.surfaceCallback)
                                }
                            },
                        )
                        FeedbackPill(feedback, Modifier.align(Alignment.TopCenter))
                        SessionChips(
                            modifier = Modifier.align(Alignment.TopEnd),
                            ffRate = ffRate,
                            onSave = { session.requestSaveState() },
                            onLoad = { session.requestLoadState() },
                            onCycleFf = {
                                ffRate = if (ffRate >= 3) 1 else ffRate + 1
                                session.ffFactor = ffRate
                            },
                        )
                    }
                    ControlPanel(modifier = Modifier.fillMaxWidth(), onButton = handleButton)
                    FamilyComputerBanner()
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(session.surfaceCallback)
                            }
                        },
                    )
                    PadControlsOverlay(onButton = handleButton)
                    FeedbackPill(feedback, Modifier.align(Alignment.TopCenter))
                    SessionChips(
                        modifier = Modifier.align(Alignment.TopEnd),
                        ffRate = ffRate,
                        onSave = { session.requestSaveState() },
                        onLoad = { session.requestLoadState() },
                        onCycleFf = {
                            ffRate = if (ffRate >= 3) 1 else ffRate + 1
                            session.ffFactor = ffRate
                        },
                    )
                }
            }
        }
    }
}

/** 竖屏下区：深色圆角控制面板，组件化手柄装配（不透明）。 */
@Composable
private fun ControlPanel(modifier: Modifier, onButton: (RetroButton, Boolean) -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                HcPanelDark,
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ),
    ) {
        com.huffcart.app.ui.game.DpadControl(
            size = 170.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp),
            onButton = onButton,
        )
        com.huffcart.app.ui.game.AbButtons(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp),
            onButton = onButton,
        )
        com.huffcart.app.ui.game.MenuPills(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            onButton = onButton,
        )
    }
}

/** 面板底部装饰横幅（设计稿品牌元素，纯展示）。 */
@Composable
private fun FamilyComputerBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
    ) {
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.asset_banner_red),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .background(HcBannerTag, RoundedCornerShape(4.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = "FAMILY COMPUTER",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PixelFontFamily,
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun FeedbackPill(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Box(
        modifier = modifier
            .padding(top = 32.dp)
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SessionChips(
    modifier: Modifier = Modifier,
    ffRate: Int,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onCycleFf: () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(top = 28.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PadChip(label = "存", desc = "存档", onClick = onSave)
        PadChip(label = "读", desc = "读档", onClick = onLoad)
        PadChip(label = "${ffRate}x", desc = "快进", onClick = onCycleFf)
    }
}

@Composable
private fun PadChip(label: String, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = desc }
            .background(HcRed.copy(alpha = 0.45f), CircleShape)
            .border(1.dp, HcCream.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** 一次运行会话：装配核心 → 加载 ROM → 游戏线程（音频节拍 + 渲染）→ 停止回收。 */
private class GameSession(context: Context, romName: String) {

    val core = LibretroCore()

    @Volatile
    private var running = false
    private var loopThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    private val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
    private val savesDir = File(context.filesDir, "romsaves").apply { mkdirs() }
    private val systemDir = File(context.filesDir, "system").apply { mkdirs() }
    private val romFile = romsDir.resolve(romName)
    private val coreLib = File(context.applicationInfo.nativeLibraryDir, "libfceumm_libretro.so")
    private val srmFile = savesDir.resolve(romName.removeSuffix(".nes") + ".srm")
    private val stateFile = savesDir.resolve(romName.removeSuffix(".nes") + ".state0")

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

    fun requestSaveState() { pendingCommand.set(SaveStateCmd) }

    fun requestLoadState() { pendingCommand.set(LoadStateCmd) }

    private fun notifyEvent(message: String) {
        mainHandler.post { onEvent?.invoke(message) }
    }

    private sealed interface SessionCommand
    private data object SaveStateCmd : SessionCommand
    private data object LoadStateCmd : SessionCommand
    private val pendingCommand = AtomicReference<SessionCommand?>(null)

    fun start() {
        check(!running) { "会话已在运行" }
        core.attach(coreLib.absolutePath, systemDir.absolutePath, savesDir.absolutePath)
        when (core.loadRom(romFile.absolutePath)) {
            LoadResult.OK -> Unit
            LoadResult.INVALID_ROM -> throw IllegalStateException("不是有效的 FC ROM")
            else -> throw IllegalStateException("游戏加载失败")
        }
        srmFile.takeIf { it.exists() }?.let { core.setSram(it.readBytes()) }

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

        running = true
        loopThread = thread(name = "game-loop") { loop() }
    }

    private fun loop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        var bitmap: Bitmap? = null
        val paint = android.graphics.Paint().apply { isFilterBitmap = false }
        var framesSinceMark = 0
        var lastMark = SystemClock.elapsedRealtime()
        audioTrack?.play()
        while (running) {
            // 快进：每墙钟帧连跑 factor 个模拟帧，只渲染/写末帧的音画
            val factor = ffFactor.coerceIn(1, 3)
            var frame: Frame? = null
            var failed = false
            for (i in 0 until factor) {
                frame = try {
                    core.runFrame()
                } catch (t: Throwable) {
                    failed = true
                    break
                }
            }
            if (failed) break
            val current = frame ?: break

            // 会话命令在游戏线程帧末执行（序列化须与 retro_run 同线程）
            when (val cmd = pendingCommand.getAndSet(null)) {
                SaveStateCmd -> {
                    val data = core.saveState()
                    if (data != null) {
                        stateFile.writeBytes(data)
                        notifyEvent("已存档")
                    } else {
                        notifyEvent("存档失败")
                    }
                }
                LoadStateCmd -> {
                    val ok = stateFile.exists() &&
                        runCatching { core.loadState(stateFile.readBytes()) }.getOrDefault(false)
                    notifyEvent(if (ok) "已读档" else "暂无存档")
                }
                null -> Unit
            }

            // 音频节拍：blocking write 把循环钉在核心采样率上；
            // 快进时被跳过帧的音频不写入（仅末帧一份），节拍仍为每墙钟帧一次
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val n = current.audioSamples.coerceIn(0, current.audio.size)
                    if (n > 0) track.write(current.audio, 0, n, AudioTrack.WRITE_BLOCKING)
                }
            }
            // 渲染：整数倍缩放 letterbox（canvas 为所在区域的实际尺寸，分区/全屏自适应）
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
            // lock/unlock 全程持锁，surfaceDestroyed 等本块结束才放行框架销毁
            synchronized(surfaceMutex) {
                runCatching {
                    val canvas = holder.lockCanvas() ?: return@synchronized
                    try {
                        canvas.drawColor(android.graphics.Color.BLACK)
                        val scale = minOf(canvas.width / w, canvas.height / h).coerceAtLeast(1)
                        val dw = w * scale
                        val dh = h * scale
                        val left = (canvas.width - dw) / 2f
                        val top = (canvas.height - dh) / 2f
                        canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
                    } finally {
                        holder.unlockCanvasAndPost(canvas)
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

    fun stop() {
        if (!running && loopThread == null) return
        running = false
        pendingCommand.set(null)
        loopThread?.join(1500)
        loopThread = null
        audioTrack?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        audioTrack = null
        // SRAM 快照落盘（设计决策 7：退出时导出，重进注入）
        core.getSram()?.let { bytes -> if (bytes.isNotEmpty()) srmFile.writeBytes(bytes) }
        core.deinit()
    }
}
