package com.huffcart.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.huffcart.core.bridge.LoadResult
import com.huffcart.core.bridge.RetroButton
import com.huffcart.core.libretro.LibretroCore
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * 游戏运行屏（game-playback 能力的宿主）：
 * - 画面：SurfaceView 软件渲染，整数倍缩放 + letterbox（锐利像素，spec「画面呈现」）
 * - 音频：AudioTrack blocking write 作为帧节拍（design 决策 4）
 * - 输入：Compose Canvas 浮层 + 多点触控映射 1P 位掩码
 * - CRT 滤镜接入点：绘制处可叠加 RuntimeShader（API 33+ 特性检测，本期不实现）
 */
@Composable
fun GameScreen(romName: String, onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    var fatal by remember { mutableStateOf<String?>(null) }
    val session = remember(romName) {
        runCatching { GameSession(context, romName) }
            .onFailure { fatal = it.message ?: it.toString() }
            .getOrNull()
    }

    DisposableEffect(romName) {
        session?.let { s ->
            runCatching { s.start() }.onFailure { fatal = it.message ?: it.toString() }
        }
        onDispose { session?.stop() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (session == null || fatal != null) {
            Column(
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text(
                    text = fatal ?: "会话创建失败",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                TextButton(onClick = onExit) { Text(text = "返回", color = Color(0xFFE60012)) }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(session.surfaceCallback)
                    }
                },
            )
            GamepadOverlay { button, pressed -> session.core.setButton(0, button, pressed) }
        }
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

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceRef.set(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceRef.set(holder)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            surfaceRef.compareAndSet(holder, null)
        }
    }

    private val surfaceRef = AtomicReference<SurfaceHolder?>(null)

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
        audioTrack?.play()
        while (running) {
            val frame = try {
                core.runFrame()
            } catch (t: Throwable) {
                break
            }
            // 音频节拍：blocking write 把循环钉在核心采样率上
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val n = frame.audioSamples.coerceIn(0, frame.audio.size)
                    if (n > 0) track.write(frame.audio, 0, n, AudioTrack.WRITE_BLOCKING)
                }
            }
            // 渲染：整数倍缩放 letterbox
            val holder = surfaceRef.get() ?: continue
            val surface: Surface = holder.surface
            if (surface == null || !surface.isValid) continue
            val w = frame.videoInfo.width
            val h = frame.videoInfo.height
            val bmp = bitmap
                ?.takeIf { it.width == w && it.height == h }
                ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap = it }
            bmp.setPixels(frame.video, 0, w, 0, 0, w, h)
            val canvas = holder.lockCanvas() ?: continue
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

    fun stop() {
        if (!running && loopThread == null) return
        running = false
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

/* ---------------- 虚拟手柄 ---------------- */

private fun dp(sizePx: Int, density: Float): Float = sizePx * density

private enum class PadZone { A, B, SELECT, START, UP, DOWN, LEFT, RIGHT }

@Composable
private fun GamepadOverlay(onButton: (RetroButton, Boolean) -> Unit) {
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var pressed by remember { mutableStateOf(emptySet<RetroButton>()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent()
                        val density = density
                        val active = event.changes
                            .filter { it.pressed }
                            .flatMap { hitTest(it.position, canvasSize, density) }
                            .toSet()
                        if (active != pressed) {
                            (pressed - active).forEach { onButton(it, false) }
                            (active - pressed).forEach { onButton(it, true) }
                            pressed = active
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawPad(pressed, size)
        }
    }
}

private fun hitTest(pos: Offset, canvas: Size, density: Float): Set<RetroButton> {
    if (canvas == Size.Zero) return emptySet()
    val out = mutableSetOf<RetroButton>()
    val padR = dp(96, density)
    val dpadCenter = Offset(canvas.width * 0.16f, canvas.height * 0.74f)
    val d = pos - dpadCenter
    if (hypot(d.x, d.y) <= padR) {
        if (d == Offset.Zero) return setOf(RetroButton.LEFT)
        out += if (kotlin.math.abs(d.x) >= kotlin.math.abs(d.y)) {
            if (d.x > 0) RetroButton.RIGHT else RetroButton.LEFT
        } else {
            if (d.y > 0) RetroButton.DOWN else RetroButton.UP
        }
    }
    val aR = dp(40, density)
    val bR = dp(40, density)
    if (hypot(pos.x - canvas.width * 0.90f, pos.y - canvas.height * 0.72f) <= aR) out += RetroButton.A
    if (hypot(pos.x - canvas.width * 0.78f, pos.y - canvas.height * 0.78f) <= bR) out += RetroButton.B
    // Start / Select：底部中间两个胶囊
    val pillHalfW = dp(52, density)
    val pillHalfH = dp(20, density)
    val centerY = canvas.height * 0.95f
    fun inPill(cx: Float): Boolean =
        kotlin.math.abs(pos.x - cx) <= pillHalfW && kotlin.math.abs(pos.y - centerY) <= pillHalfH
    if (inPill(canvas.width * 0.40f)) out += RetroButton.SELECT
    if (inPill(canvas.width * 0.60f)) out += RetroButton.START
    return out
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPad(
    pressed: Set<RetroButton>,
    canvas: Size,
) {
    val base = Color(0xFFE60012).copy(alpha = 0.30f)
    val activeColor = Color(0xFFE60012).copy(alpha = 0.75f)
    val stroke = Color(0xFFF6F1E7).copy(alpha = 0.45f)

    fun colorFor(button: RetroButton) = if (button in pressed) activeColor else base

    // 十字键
    val cx = canvas.width * 0.16f
    val cy = canvas.height * 0.74f
    val armW = dp(38, density)
    val armL = dp(64, density)
    fun drawDir(button: RetroButton, dx: Float, dy: Float) {
        drawRoundRect(
            color = colorFor(button),
            topLeft = Offset(cx + dx * armL / 2 - if (dy == 0f) armL / 2 else armW / 2,
                cy + dy * armL / 2 - if (dx == 0f) armL / 2 else armW / 2),
            size = if (dy == 0f) Size(armL, armW) else Size(armW, armL),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(dp(10, density)),
        )
    }
    drawDir(RetroButton.LEFT, -1f, 0f)
    drawDir(RetroButton.RIGHT, 1f, 0f)
    drawDir(RetroButton.UP, 0f, -1f)
    drawDir(RetroButton.DOWN, 0f, 1f)

    // A / B
    fun drawRound(button: RetroButton, cx: Float, cy: Float, r: Float, label: String) {
        drawCircle(color = colorFor(button), radius = r, center = Offset(cx, cy))
        drawCircle(color = stroke, radius = r, center = Offset(cx, cy), style = Stroke(width = dp(2, density)))
        drawContext.canvas.nativeCanvas.drawText(
            label, cx, cy + dp(6, density),
            android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = dp(16, density)
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            },
        )
    }
    drawRound(RetroButton.A, canvas.width * 0.90f, canvas.height * 0.72f, dp(40, density), "A")
    drawRound(RetroButton.B, canvas.width * 0.78f, canvas.height * 0.78f, dp(40, density), "B")

    // Start / Select
    fun drawPill(button: RetroButton, cx: Float) {
        val halfW = dp(52, density)
        val halfH = dp(20, density)
        drawRoundRect(
            color = colorFor(button),
            topLeft = Offset(cx - halfW, canvas.height * 0.95f - halfH),
            size = Size(halfW * 2, halfH * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(halfH),
        )
    }
    drawPill(RetroButton.SELECT, canvas.width * 0.40f)
    drawPill(RetroButton.START, canvas.width * 0.60f)
}
