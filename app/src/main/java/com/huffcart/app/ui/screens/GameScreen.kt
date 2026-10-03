package com.huffcart.app.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.huffcart.app.netplay.NetplayGameEvent
import com.huffcart.app.netplay.PlayerInfo
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.ui.HideSystemNavigationBars
import com.huffcart.app.netplay.NetplayMessage
import com.huffcart.app.netplay.Seat
import com.huffcart.app.ui.game.CheatEntry
import com.huffcart.app.ui.game.CheatPanel
import com.huffcart.app.ui.game.CheatStore
import com.huffcart.app.ui.game.GameSession
import com.huffcart.app.ui.game.ControlScheme
import com.huffcart.app.ui.game.ControlSchemeStore
import com.huffcart.app.ui.game.KeyMappingStore
import com.huffcart.app.ui.game.PadFeedback
import com.huffcart.app.ui.game.PadControlsOverlay
import com.huffcart.app.ui.game.SaveSlotPanel
import com.huffcart.app.ui.game.SaveSlotPanelMode
import com.huffcart.app.ui.game.SaveSlotStore
import com.huffcart.app.ui.game.SlotMeta
import com.huffcart.app.ui.game.SlotThumbnails
import com.huffcart.app.ui.theme.HcBannerTag
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcGameBlack
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcRedDark
import com.huffcart.app.ui.theme.PixelFontFamily
import com.huffcart.core.bridge.RetroButton
import java.io.File
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
    // 金手指面板（cheat-codes）：菜单「金手指」打开；条目状态本地持有，变更即落盘并批应用
    var cheatPanelOpen by remember { mutableStateOf(false) }
    var cheatEntries by remember(romName) {
        mutableStateOf(CheatStore.load(context, SaveSlotStore.baseName(romName)))
    }
    // 连发（turbo-and-auto-resume）：会话内开关，默认关
    var turboOn by remember { mutableStateOf(false) }
    val netplay = remember(romName) { NetplayManager.consumePendingGame() }
    val session = remember(romName) {
        runCatching { GameSession(context, romName, netplay) }
            .onFailure { fatal = it.message ?: it.toString() }
            .getOrNull()
    }
    // 断点续玩：单机进入且存在挂起档时询问（须在 session 声明后初始化）
    var resumeAsk by remember(romName) {
        mutableStateOf(session != null && netplay == null && session.suspendAvailable)
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
            // 弹出窗口跨旋转会因焦点等待超时触发 ANR（game-menu-rotation-anr）：布局形态
            // 切换即收起菜单与面板。组合期判断（非 LaunchedEffect——后者首次运行会误清初值）
            var lastPortrait by remember { mutableStateOf(isPortrait) }
            if (isPortrait != lastPortrait) {
                lastPortrait = isPortrait
                menuOpen = false
                slotPanel = null
                cheatPanelOpen = false
                resumeAsk = false
            }
            val cycleFf = {
                ffRate = if (ffRate >= 3) 1 else ffRate + 1
                session.ffFactor = ffRate
            }
            val toggleTurbo = {
                turboOn = !turboOn
                session.setTurbo(turboOn)
                feedback = if (turboOn) "连发开：按住 A/B 自动连打" else "连发关"
            }
            // 倒带（rewind-and-session-split）：按住置位/松开复位，游戏循环据此回退
            val setRewindHeld: (Boolean) -> Unit = { holding ->
                Log.d("RewindUI", "rewindHeld=$holding")
                session.rewindHeld = holding
                feedback = if (holding) "倒带中…" else null
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
            if (cheatPanelOpen) {
                CheatPanel(
                    entries = cheatEntries,
                    onToggle = { index ->
                        cheatEntries = cheatEntries.mapIndexed { i, e ->
                            if (i == index) e.copy(enabled = !e.enabled) else e
                        }
                        CheatStore.save(context, SaveSlotStore.baseName(romName), cheatEntries)
                        session.requestApplyCheats(cheatEntries.filter { it.enabled }.map { it.code })
                    },
                    onDelete = { index ->
                        cheatEntries = cheatEntries.filterIndexed { i, _ -> i != index }
                        CheatStore.save(context, SaveSlotStore.baseName(romName), cheatEntries)
                        session.requestApplyCheats(cheatEntries.filter { it.enabled }.map { it.code })
                    },
                    onAdd = { code ->
                        cheatEntries = cheatEntries + CheatEntry(code, enabled = true)
                        CheatStore.save(context, SaveSlotStore.baseName(romName), cheatEntries)
                        session.requestApplyCheats(cheatEntries.filter { it.enabled }.map { it.code })
                    },
                    onDismiss = { cheatPanelOpen = false },
                )
            }
            if (resumeAsk) {
                FcOptionDialog(
                    title = "继续上次进度？",
                    options = listOf("resume" to "继续上次", "restart" to "重新开始"),
                    current = "",
                    onSelect = { which ->
                        resumeAsk = false
                        if (which == "resume") {
                            session.requestResumeSuspend()
                        } else {
                            session.discardSuspend()
                        }
                    },
                    // 关闭不选择：保留挂起档，下次进入再问
                    onDismiss = { resumeAsk = false },
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
                        onRewindHold = setRewindHeld,
                        turboOn = turboOn,
                        onToggleTurbo = toggleTurbo,
                        onOpenCheats = { cheatPanelOpen = true },
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
                        onRewindHold = setRewindHeld,
                        turboOn = turboOn,
                        onToggleTurbo = toggleTurbo,
                        onOpenCheats = { cheatPanelOpen = true },
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
    onRewindHold: (Boolean) -> Unit,
    turboOn: Boolean,
    onToggleTurbo: () -> Unit,
    onOpenCheats: () -> Unit,
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
                    onRewindHold = onRewindHold,
                    turboOn = turboOn,
                    onToggleTurbo = onToggleTurbo,
                    onOpenCheats = onOpenCheats,
                    onExit = onExit,
                )
            }
        }
    }
}

/** 游戏菜单项（竖屏顶栏与横屏浮钮共用）：联机中（netplayActive）存/读/快进与同步机制
 *  冲突，菜单仅保留退出（spec「联机期间限制」）；倒带同受此限制（rewind spec「联机中不可用」）。 */
@Composable
private fun GameMenuItems(
    netplayActive: Boolean,
    ffRate: Int,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onCycleFf: () -> Unit,
    onRewindHold: (Boolean) -> Unit,
    turboOn: Boolean,
    onToggleTurbo: () -> Unit,
    onOpenCheats: () -> Unit,
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
        // 按住倒带（rewind-and-session-split 决策 5）：按住生效、松开结束；菜单不自动收起。
        // 不用 DropdownMenuItem——其内部 clickable 的手势消费使外挂 pointerInput 的
        // onPress 不可靠（真机实测不触发）；普通 Box 自管按压序列
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onRewindHold(true)
                        waitForUpOrCancellation()
                        onRewindHold(false)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text("按住倒带", color = HcRed)
        }
        DropdownMenuItem(
            text = { Text("连发 A/B（${if (turboOn) "开" else "关"}）") },
            onClick = {
                onDismiss()
                onToggleTurbo()
            },
        )
        DropdownMenuItem(
            text = { Text("金手指") },
            onClick = {
                onDismiss()
                onOpenCheats()
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
    onRewindHold: (Boolean) -> Unit,
    turboOn: Boolean,
    onToggleTurbo: () -> Unit,
    onOpenCheats: () -> Unit,
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
                onRewindHold = onRewindHold,
                turboOn = turboOn,
                onToggleTurbo = onToggleTurbo,
                onOpenCheats = onOpenCheats,
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

