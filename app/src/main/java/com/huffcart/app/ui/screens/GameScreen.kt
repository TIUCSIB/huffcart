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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.huffcart.app.R
import com.huffcart.app.netplay.ButtonMask
import com.huffcart.app.netplay.NetplayCodec
import com.huffcart.app.netplay.NetplayGameEvent
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.ui.HideSystemNavigationBars
import com.huffcart.app.netplay.NetplayMessage
import com.huffcart.app.netplay.Seat
import com.huffcart.app.netplay.crc32OfVideo
import com.huffcart.app.ui.game.ControlScheme
import com.huffcart.app.ui.game.ControlSchemeStore
import com.huffcart.app.ui.game.KeyMappingStore
import com.huffcart.app.ui.game.PadControlsOverlay
import com.huffcart.app.ui.library.CoverStore
import com.huffcart.app.ui.theme.HcBannerTag
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcGameBlack
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcOutlineLight
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

/**
 * 游戏运行屏（game-playback 能力的宿主，retro-ui-redesign 改版）：
 * - 顶部红色导航栏（吹卡带 logo + ⚙️ 快捷菜单）；竖屏：画面区 + 深色面板；横屏：画面 + 浮层
 * - 音频：AudioTrack blocking write 作为帧节拍（design 决策 4）
 * - 输入：PadControls 触控 + 物理键盘映射（KeyMappingStore），两条通路独立写 setButton
 * - 存/读/快进入口在 ⚙️ 菜单（ui-polish），反馈气泡仍顶部居中
 * - 联机（netplay-lan）：经 NetplayManager 消费对局装配；联机中隐藏存/读/快进，
 *   顶部联机横幅；加入端以主机输入流为节拍（design 决策 1）
 * - CRT 滤镜接入点：绘制处可叠加 RuntimeShader（API 33+ 特性检测，本期不实现）
 */
@Composable
fun GameScreen(romName: String, onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)
    // 沉浸式：隐藏手势导航条（上滑临时呼出），底部面板不被系统手势区挤压
    HideSystemNavigationBars()

    var fatal by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var ffRate by remember { mutableStateOf(1) }
    var forceExit by remember { mutableStateOf(false) }
    var netplayOver by remember { mutableStateOf(false) }
    val netplay = remember(romName) { NetplayManager.consumePendingGame() }
    val session = remember(romName) {
        runCatching { GameSession(context, romName, netplay) }
            .onFailure { fatal = it.message ?: it.toString() }
            .getOrNull()
    }

    DisposableEffect(romName) {
        session?.let { s ->
            s.onEvent = { msg -> feedback = msg }
            s.onFatal = { msg -> fatal = msg }
            runCatching { s.start() }.onFailure { fatal = it.message ?: it.toString() }
        }
        NetplayManager.gameListener = { event ->
            when (event) {
                is NetplayGameEvent.Message -> feedback = event.text
                is NetplayGameEvent.Ended -> {
                    if (!event.exitGame) netplayOver = true // 房主降级单机：撤联机横幅
                    if (event.exitGame) forceExit = true
                }
            }
        }
        onDispose {
            NetplayManager.gameListener = null
            NetplayManager.onGameScreenLeft()
            session?.stop()
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2500)
            feedback = null
        }
    }

    LaunchedEffect(forceExit) {
        if (forceExit) {
            delay(1200)
            onExit()
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
    // 控制形态（virtual-joystick）：设置页仅在主界面可达，进入游戏屏读取一次即可
    val controlScheme = remember { ControlSchemeStore.load(context) }
    val handleButton: (RetroButton, Boolean) -> Unit = { button, pressed ->
        session?.onLocalButton(button, pressed)
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
            var menuOpen by remember { mutableStateOf(false) }
            val cycleFf = {
                ffRate = if (ffRate >= 3) 1 else ffRate + 1
                session.ffFactor = ffRate
            }
            val netplayActive = netplay != null && !netplayOver
            val netplayBanner = if (netplayActive) {
                netplay?.let {
                    "联机中 · 对方 ${it.opponentNickname}（${if (it.role == Seat.P1) "P2" else "P1"}）"
                }
            } else {
                null
            }
            if (isPortrait) {
                Column(modifier = Modifier.fillMaxSize()) {
                    GameTopBar(
                        ffRate = ffRate,
                        netplayActive = netplayActive,
                        menuExpanded = menuOpen,
                        onMenu = { menuOpen = it },
                        onSave = { session.requestSaveState() },
                        onLoad = { session.requestLoadState() },
                        onCycleFf = cycleFf,
                        onExit = onExit,
                    )
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    holder.addCallback(session.surfaceCallback)
                                }
                            },
                        )
                        NetplayBanner(netplayBanner, Modifier.align(Alignment.TopStart))
                        FeedbackPill(feedback, Modifier.align(Alignment.TopCenter))
                    }
                    ControlPanel(
                        modifier = Modifier.fillMaxWidth(),
                        onButton = handleButton,
                        scheme = controlScheme,
                    )
                    FamilyComputerBanner()
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    GameTopBar(
                        ffRate = ffRate,
                        netplayActive = netplayActive,
                        menuExpanded = menuOpen,
                        onMenu = { menuOpen = it },
                        onSave = { session.requestSaveState() },
                        onLoad = { session.requestLoadState() },
                        onCycleFf = cycleFf,
                        onExit = onExit,
                    )
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    holder.addCallback(session.surfaceCallback)
                                }
                            },
                        )
                        PadControlsOverlay(onButton = handleButton, scheme = controlScheme)
                        NetplayBanner(netplayBanner, Modifier.align(Alignment.TopStart))
                        FeedbackPill(feedback, Modifier.align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

/** 游戏屏顶部红色导航栏（设计稿）：左吹卡带素材 logo，右 ⚙️ 快捷菜单（存/读/快进/退出）。
 *  联机中（netplayActive）存/读/快进与同步机制冲突，菜单仅保留退出（spec「联机期间限制」）。 */
@Composable
private fun GameTopBar(
    ffRate: Int,
    netplayActive: Boolean,
    menuExpanded: Boolean,
    onMenu: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onCycleFf: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HcRed)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.asset_logo_lockup),
            contentDescription = "吹卡带",
            modifier = Modifier
                .height(36.dp)
                .aspectRatio(475f / 237f),
            filterQuality = FilterQuality.None,
        )
        Box {
            IconButton(onClick = { onMenu(true) }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "游戏菜单",
                    tint = Color.White,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenu(false) },
                shape = RoundedCornerShape(10.dp),
                containerColor = Color.White,
                border = BorderStroke(1.dp, HcOutlineLight),
            ) {
                if (!netplayActive) {
                    DropdownMenuItem(
                        text = { Text("存档") },
                        onClick = {
                            onMenu(false)
                            onSave()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("读档") },
                        onClick = {
                            onMenu(false)
                            onLoad()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("快进（当前 ${ffRate}x）") },
                        onClick = {
                            onMenu(false)
                            onCycleFf()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("退出游戏", color = HcRed) },
                    onClick = {
                        onMenu(false)
                        onExit()
                    },
                )
            }
        }
    }
}

/** 竖屏下区：虚拟手柄面板（全代码绘制，黑底上圆角分区；方向形态按 scheme）。 */
@Composable
private fun ControlPanel(
    modifier: Modifier,
    onButton: (RetroButton, Boolean) -> Unit,
    scheme: ControlScheme,
) {
    com.huffcart.app.ui.game.GamepadPanel(
        onButton = onButton,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        scheme = scheme,
    )
}

/** 面板底部装饰横幅：云纹条 3 段平铺铺满宽度（单张 Crop 会把云纹裁掉）。 */
@Composable
private fun FamilyComputerBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(3) {
                Image(
                    bitmap = ImageBitmap.imageResource(R.drawable.asset_banner_red),
                    contentDescription = null,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None,
                )
            }
        }
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

/** 联机横幅（netplay-lan）：左上角显示联机状态与对方昵称/席位。 */
@Composable
private fun NetplayBanner(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Box(
        modifier = modifier
            .padding(top = 32.dp, start = 12.dp)
            .background(HcRed.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 一次运行会话：装配核心 → 加载 ROM → 游戏线程（音频节拍 + 渲染）→ 停止回收。
 *  netplay 非空时进入联机态（netplay-lan）：房主保持音频节拍并广播输入，
 *  加入端以主机输入流为节拍、音频改非阻塞写（design 决策 1/5）。 */
private class GameSession(
    context: Context,
    romName: String,
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
    }

    @Volatile
    private var localP1Mask = 0

    @Volatile
    private var localP2Mask = 0

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

    /** 本机物理输入入口（触控 + 键盘）：单机写 P1；联机按席位映射（design 决策 6）。 */
    fun onLocalButton(button: RetroButton, pressed: Boolean) {
        val np = netplay
        if (np == null) {
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
            Seat.P2 -> {
                // 加入端：本机是 P2，但核心输入完全来自主机回传——本地只上送
                localP2Mask = if (pressed) localP2Mask or bit else localP2Mask and bit.inv()
                NetplayManager.sendJoinerMask(localP2Mask)
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
        when (netplay?.role) {
            // 加入端：跳过本地 SRAM（避免与房主分叉）；快照对齐在游戏线程做
            Seat.P2 -> Unit
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
                Seat.P2 -> {
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
        var lastAppliedP1 = 0
        var lastAppliedP2 = 0
        while (running) {
            // ---- 联机帧首：装配本帧核心输入（design 决策 1）----
            var input: NetplayMessage.Input? = null
            if (netplayLive && np != null) {
                when (np.role) {
                    Seat.P2 -> {
                        // 加入端节拍：阻塞等主机第 N 帧输入；超时即断（读超时 3s）
                        input = NetplayManager.awaitJoinerInput(3_000)
                        if (input == null) {
                            println("[StopSeq] joiner loop: input timeout, exiting")
                            netplayLive = false
                            NetplayManager.postGameEvent(NetplayGameEvent.Message("连接超时，联机已断开"))
                            NetplayManager.postGameEvent(NetplayGameEvent.Ended(exitGame = true))
                            running = false
                            break
                        }
                        ButtonMask.diff(lastAppliedP1, input.p1Mask,
                            { core.setButton(0, it, true) }, { core.setButton(0, it, false) })
                        ButtonMask.diff(lastAppliedP2, input.p2Mask,
                            { core.setButton(1, it, true) }, { core.setButton(1, it, false) })
                        lastAppliedP1 = input.p1Mask
                        lastAppliedP2 = input.p2Mask
                    }
                    Seat.P1 -> {
                        if (!NetplayManager.isHostLinked()) {
                            // 对方断开：清 P2 注入，无缝回到单机（spec「断线处理」）
                            netplayLive = false
                            ButtonMask.diff(lastAppliedP2, 0,
                                { }, { core.setButton(1, it, false) })
                            lastAppliedP2 = 0
                            NetplayManager.postGameEvent(NetplayGameEvent.Message("对方已断开，回到单机"))
                        } else {
                            val remote = NetplayManager.latestRemoteP2Mask()
                            ButtonMask.diff(lastAppliedP2, remote,
                                { core.setButton(1, it, true) }, { core.setButton(1, it, false) })
                            lastAppliedP2 = remote
                        }
                    }
                }
            }

            // 快进：每墙钟帧连跑 factor 个模拟帧，只渲染/写末帧的音画；
            // 联机中钳制为 1x（双方节拍必须一致，spec「联机期间限制」）
            val factor = if (netplayLive) 1 else ffFactor.coerceIn(1, 3)
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
            frameNo += factor

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
            // 快进时被跳过帧的音频不写入（仅末帧一份），节拍仍为每墙钟帧一次；
            // 加入端改非阻塞写——节拍来自主机输入流，写满只能丢（短促杂音可接受）
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val n = current.audioSamples.coerceIn(0, current.audio.size)
                    val mode = if (netplayLive && np?.role == Seat.P2) {
                        AudioTrack.WRITE_NON_BLOCKING
                    } else {
                        AudioTrack.WRITE_BLOCKING
                    }
                    if (n > 0) track.write(current.audio, 0, n, mode)
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
                        NetplayManager.sendHostInput(frameNo, localP1Mask, lastAppliedP2, crc)
                    }
                    Seat.P2 -> {
                        if (NetplayCodec.isChecksumFrame(frameNo)) {
                            np.joinSession?.verifyFrame(crc32OfVideo(current.video, w, h))
                        }
                        // 保活：掩码不变也定期上送，房主靠它判定断线（design 决策 5）
                        if (frameNo % JOINER_KEEPALIVE_FRAMES == 0) {
                            NetplayManager.sendJoinerMask(localP2Mask)
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

    fun stop() {
        println("[StopSeq] stop entry: running=$running loopAlive=${loopThread?.isAlive}")
        if (!running && loopThread == null) return
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
