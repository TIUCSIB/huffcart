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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.huffcart.app.R
import com.huffcart.app.netplay.ButtonMask
import com.huffcart.app.netplay.NetplayCodec
import com.huffcart.app.netplay.NetplayGameEvent
import com.huffcart.app.netplay.PlayerInfo
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.ui.HideSystemNavigationBars
import com.huffcart.app.netplay.NetplayMessage
import com.huffcart.app.netplay.Seat
import com.huffcart.app.netplay.crc32OfVideo
import com.huffcart.app.ui.game.AudioSettingsStore
import com.huffcart.app.ui.game.ControlScheme
import com.huffcart.app.ui.game.ControlSchemeStore
import com.huffcart.app.ui.game.DisplayAspect
import com.huffcart.app.ui.game.KeyMappingStore
import com.huffcart.app.ui.game.PadFeedback
import com.huffcart.app.ui.game.PadControlsOverlay
import com.huffcart.app.ui.game.SaveSlotPanel
import com.huffcart.app.ui.game.SaveSlotPanelMode
import com.huffcart.app.ui.game.SaveSlotStore
import com.huffcart.app.ui.game.SlotMeta
import com.huffcart.app.ui.game.SlotThumbnails
import com.huffcart.app.ui.game.VideoSettingsStore
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
 * - 竖屏：红色导航栏（吹卡带 logo + ⚙️ 快捷菜单）+ 画面区 + 深色控制面板 + 装饰横幅；
 *   横屏（game-landscape-fullscreen）：全屏沉浸画面（无顶栏、隐藏状态栏）+ 浮层手柄 +
 *   右上角半透明菜单浮钮
 * - 音频：AudioTrack blocking write 作为帧节拍（design 决策 4）
 * - 输入：PadControls 触控 + 物理键盘映射（KeyMappingStore），两条通路独立写 setButton
 * - 存/读/快进入口在 ⚙️ 菜单（ui-polish）；存/读档经槽位面板选槽（audio-settings-and-save-management），
 *   反馈气泡仍顶部居中；音量/静音/快进静音按声音设置作用到 AudioTrack（只改增益不停写）
 * - 联机（netplay-lan）：经 NetplayManager 消费对局装配；联机中隐藏存/读/快进，
 *   顶部联机横幅；加入端以主机输入流为节拍（design 决策 1）
 * - CRT 滤镜接入点：绘制处可叠加 RuntimeShader（API 33+ 特性检测，本期不实现）
 */
@Composable
fun GameScreen(romName: String, onExit: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)
    val isPortrait =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    // 沉浸式：隐藏手势导航条（上滑临时呼出）；横屏全屏（game-landscape-fullscreen）再隐藏状态栏
    HideSystemNavigationBars(hideStatusBar = !isPortrait)

    var fatal by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var ffRate by remember { mutableStateOf(1) }
    var forceExit by remember { mutableStateOf(false) }
    var netplayOver by remember { mutableStateOf(false) }
    // 槽位面板（audio-settings-and-save-management）：菜单「存档/读档」点开后弹出，选槽即存/读
    var slotPanel by remember { mutableStateOf<SaveSlotPanelMode?>(null) }
    val netplay = remember(romName) { NetplayManager.consumePendingGame() }
    val session = remember(romName) {
        runCatching { GameSession(context, romName, netplay) }
            .onFailure { fatal = it.message ?: it.toString() }
            .getOrNull()
    }

    DisposableEffect(romName) {
        // 按压反馈开关（pad-feedback-and-display-settings）：进游戏屏读一次，
        // 设置页仅主界面可达，改完重进即新值（manifest configChanges 旋转不重建）
        PadFeedback.init(context)
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
            var menuOpen by remember { mutableStateOf(false) }
            val cycleFf = {
                ffRate = if (ffRate >= 3) 1 else ffRate + 1
                session.ffFactor = ffRate
            }
            val netplayActive = netplay != null && !netplayOver
            slotPanel?.let { mode ->
                val savesDir = remember { File(context.filesDir, SaveSlotStore.DIR_NAME) }
                val slotMetas = remember(mode) { SaveSlotStore.slots(savesDir, romName) }
                val slotThumbs = remember(slotMetas) {
                    slotMetas.map { meta -> meta?.let { SlotThumbnails.decode(context, romName, it.slot) } }
                }
                SaveSlotPanel(
                    mode = mode,
                    metas = slotMetas,
                    thumbnails = slotThumbs,
                    onSelect = { slot ->
                        // 读档模式下空槽在面板内已不可点，此处兜底
                        if (mode == SaveSlotPanelMode.SAVE || slotMetas.getOrNull(slot) != null) {
                            slotPanel = null
                            if (mode == SaveSlotPanelMode.SAVE) {
                                session.requestSaveState(slot)
                            } else {
                                session.requestLoadState(slot)
                            }
                        }
                    },
                    onDismiss = { slotPanel = null },
                )
            }
            if (isPortrait) {
                Column(modifier = Modifier.fillMaxSize()) {
                    GameTopBar(
                        ffRate = ffRate,
                        netplayActive = netplayActive,
                        menuExpanded = menuOpen,
                        onMenu = { menuOpen = it },
                        onSave = { slotPanel = SaveSlotPanelMode.SAVE },
                        onLoad = { slotPanel = SaveSlotPanelMode.LOAD },
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
                        if (netplayActive) {
                            netplay?.let { SeatBadges(it.members, it.role, Modifier.align(Alignment.TopStart)) }
                        }
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
                // 横屏全屏沉浸（game-landscape-fullscreen）：无顶栏、画面占满整屏，
                // 菜单收进右上角半透明浮钮，与竖屏顶栏菜单共用同一组回调
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(session.surfaceCallback)
                            }
                        },
                    )
                    PadControlsOverlay(onButton = handleButton, scheme = controlScheme)
                    GameFloatingMenu(
                        ffRate = ffRate,
                        netplayActive = netplayActive,
                        menuExpanded = menuOpen,
                        onMenu = { menuOpen = it },
                        onSave = { slotPanel = SaveSlotPanelMode.SAVE },
                        onLoad = { slotPanel = SaveSlotPanelMode.LOAD },
                        onCycleFf = cycleFf,
                        onExit = onExit,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    if (netplayActive) {
                        netplay?.let { SeatBadges(it.members, it.role, Modifier.align(Alignment.TopStart)) }
                    }
                    FeedbackPill(feedback, Modifier.align(Alignment.TopCenter))
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
                GameMenuItems(
                    netplayActive = netplayActive,
                    ffRate = ffRate,
                    onDismiss = { onMenu(false) },
                    onSave = onSave,
                    onLoad = onLoad,
                    onCycleFf = onCycleFf,
                    onExit = onExit,
                )
            }
        }
    }
}

/** 游戏菜单项（竖屏顶栏与横屏浮钮共用）：联机中（netplayActive）存/读/快进与同步机制
 *  冲突，菜单仅保留退出（spec「联机期间限制」）。 */
@Composable
private fun GameMenuItems(
    netplayActive: Boolean,
    ffRate: Int,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onCycleFf: () -> Unit,
    onExit: () -> Unit,
) {
    if (!netplayActive) {
        DropdownMenuItem(
            text = { Text("存档") },
            onClick = {
                onDismiss()
                onSave()
            },
        )
        DropdownMenuItem(
            text = { Text("读档") },
            onClick = {
                onDismiss()
                onLoad()
            },
        )
        DropdownMenuItem(
            text = { Text("快进（当前 ${ffRate}x）") },
            onClick = {
                onDismiss()
                onCycleFf()
            },
        )
    }
    DropdownMenuItem(
        text = { Text("退出游戏", color = HcRed) },
        onClick = {
            onDismiss()
            onExit()
        },
    )
}

/** 横屏全屏下的游戏菜单浮钮（game-landscape-fullscreen）：右上角半透明圆形齿轮，
 *  padding 避让 displayCutout（状态栏已隐藏，刘海挖孔仍可能压角）；菜单项与竖屏一致。 */
@Composable
private fun GameFloatingMenu(
    ffRate: Int,
    netplayActive: Boolean,
    menuExpanded: Boolean,
    onMenu: (Boolean) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onCycleFf: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(top = 4.dp, end = 4.dp),
    ) {
        IconButton(
            onClick = { onMenu(true) },
            modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "游戏菜单",
                tint = Color.White.copy(alpha = 0.85f),
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { onMenu(false) },
            shape = RoundedCornerShape(10.dp),
            containerColor = Color.White,
            border = BorderStroke(1.dp, HcOutlineLight),
        ) {
            GameMenuItems(
                netplayActive = netplayActive,
                ffRate = ffRate,
                onDismiss = { onMenu(false) },
                onSave = onSave,
                onLoad = onLoad,
                onCycleFf = onCycleFf,
                onExit = onExit,
            )
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

/** 联机席位徽章（netplay-lobby-v2）：左上角一排 P1–P4 小圆徽，本端席位红色高亮。 */
@Composable
private fun SeatBadges(members: List<PlayerInfo>, mySeat: Seat, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 40.dp, start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        members.sortedBy { it.seat.ordinal }.forEach { m ->
            val mine = m.seat == mySeat
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (mine) HcRed.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.seat.label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** 一次运行会话：装配核心 → 加载 ROM → 游戏线程（音频节拍 + 渲染）→ 停止回收。
 *  netplay 非空时进入联机态（netplay-lan）：房主保持音频节拍并广播输入，
 *  加入端以主机输入流为节拍、音频改非阻塞写（design 决策 1/5）。 */
private class GameSession(
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
    }

    @Volatile
    private var localP1Mask = 0

    /** 加入端本机席位（P2/P3/P4）掩码；UI 线程写 / 游戏线程读。 */
    @Volatile
    private var localSeatMask = 0

    private val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
    val savesDir = File(context.filesDir, SaveSlotStore.DIR_NAME).apply { mkdirs() }
    private val systemDir = File(context.filesDir, "system").apply { mkdirs() }
    private val romFile = romsDir.resolve(romName)
    private val coreLib = File(context.applicationInfo.nativeLibraryDir, "libfceumm_libretro.so")
    private val srmFile = savesDir.resolve(romName.removeSuffix(".nes") + ".srm")

    /** 声音设置（audio-settings-and-save-management）：会话构造时读一次，进游戏生效。 */
    private val audio = AudioSettingsStore.load(context)

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

    /** 本机物理输入入口（触控 + 键盘）：单机写 P1；联机按席位映射（netplay-lobby-v2）。 */
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
        while (running) {
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
                    notifyEvent(if (ok) "已读档（槽位 ${cmd.slot + 1}）" else "暂无存档")
                }
                null -> Unit
            }

            // 音频节拍：blocking write 把循环钉在核心采样率上；
            // 快进时被跳过帧的音频不写入（仅末帧一份），节拍仍为每墙钟帧一次；
            // 加入端改非阻塞写——节拍来自主机输入流，写满只能丢（短促杂音可接受）
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    // 音量/快进静音（audio-settings-and-save-management 决策 1）：只改增益，
                    // 绝不 pause/停写——AudioTrack 阻塞写是帧节拍
                    val desiredGain = audio.gainDuringFastForward(factor > 1)
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
