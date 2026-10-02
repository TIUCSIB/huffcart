package com.huffcart.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huffcart.core.bridge.RetroButton
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcPadRed
import com.huffcart.app.ui.theme.HcPadRedDeep
import com.huffcart.app.ui.theme.PixelFontFamily

// 素材手柄实测色（用户参考图采样）
private val DpadArm = Color(0xFF3C3C3C)
private val DpadRecess = Color(0xFF161616)
private val RedTop = Color(0xFFF85753)
private val RedBottom = Color(0xFFC41E20)
private val RedLabel = Color(0xFF7A0305)
private val PillBg = Color(0xFF3E3E3E)
private val PillBorderDark = Color(0xFF191919)
private val ArrowWhite = Color(0xFFF2F2F2)

/**
 * 组件化虚拟手柄（retro-ui-redesign 决策 4；ui-asset-integration：视觉 1:1 对齐素材手柄）。
 * 视觉由 Canvas 绘制（十字键 + 三角箭头 + 渐变红球），命中区为独立透明层，
 * 按下红色高亮（spec「虚拟手柄输入」），交互逻辑与横竖屏共用组件不变。
 */

// 十字键专用死区：远小于摇杆（0.25/0.20 会让十字中心一大片点按无反应）
private const val DPAD_DEAD_ZONE = 0.12f
private const val DPAD_EXIT_ZONE = 0.10f

// 十字键扇区：宽正方向 / 窄斜向——按臂身稍偏仍判正方向，只有明确按在 45° 角附近才判斜向
private const val DPAD_CARDINAL_HALF_DEGREES = 32f
private const val DPAD_DIAGONAL_HALF_DEGREES = 13f

// 十字键按压高亮长度（占十字包络 env 的比例，从臂端向臂心方向覆盖）
private const val DPAD_HIGHLIGHT_FRAC = 0.35f

/** 十字键手势（竖屏面板与横屏浮层共用）：触点相对中心角度判向（JoystickMath 8 向：
 *  死区+迟滞），滑动换向，抬手复位；斜向 = 同帧两键组合（如右上 = UP+RIGHT）。
 *  down 落点即判定——快速点按没有后续 move 事件也立即生效。 */
private fun Modifier.dpadGesture(
    onPressStart: () -> Unit,
    onDirsChange: (Set<RetroButton>) -> Unit,
    onButton: (RetroButton, Boolean) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onPressStart()
        var current: Set<RetroButton> = emptySet()
        fun apply(pos: Offset) {
            val r = minOf(size.width, size.height) / 2f
            val nd = JoystickMath.directions(
                (pos.x - size.width / 2f) / r,
                (pos.y - size.height / 2f) / r,
                current,
                deadZone = DPAD_DEAD_ZONE,
                exitZone = DPAD_EXIT_ZONE,
                cardinalHalfDegrees = DPAD_CARDINAL_HALF_DEGREES,
                diagonalHalfDegrees = DPAD_DIAGONAL_HALF_DEGREES,
            )
            if (nd != current) {
                (current - nd).forEach { onButton(it, false) }
                (nd - current).forEach { onButton(it, true) }
                current = nd
                onDirsChange(nd)
            }
        }
        try {
            apply(down.position)
            while (true) {
                val event = awaitPointerEvent()
                val pt = event.changes.firstOrNull { it.pressed } ?: break
                apply(pt.position)
            }
        } finally {
            current.forEach { onButton(it, false) }
            onDirsChange(emptySet())
        }
    }
}

/**
 * 十字键（横屏浮层，代码绘制）：凹槽圆 + 圆润十字 + 臂端白色三角箭头。
 * 触点角度判向（8 向含斜向）；按下方向半臂红色高亮、十字整体下压（压进凹槽的层次感）。
 */
@Composable
fun DpadControl(
    size: Dp,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    val haptic = rememberPadFeedback()
    var pressedDirs by remember { mutableStateOf<Set<RetroButton>>(emptySet()) }
    val alphaMul = if (translucent) 0.55f else 1f
    // 十字朝按压方向偏移（跷跷板倾斜）：按「右」十字右移
    val nudgeDp = size * 0.018f
    val nudgeX by animateDpAsState(
        targetValue = when {
            RetroButton.RIGHT in pressedDirs -> nudgeDp
            RetroButton.LEFT in pressedDirs -> -nudgeDp
            else -> 0.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dpadNudgeX",
    )
    val nudgeY by animateDpAsState(
        targetValue = when {
            RetroButton.DOWN in pressedDirs -> nudgeDp
            RetroButton.UP in pressedDirs -> -nudgeDp
            else -> 0.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dpadNudgeY",
    )
    // 十字下压弹簧分数：按压时整体沉入凹槽
    val sinkFrac by animateFloatAsState(
        targetValue = if (pressedDirs.isNotEmpty()) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dpadSinkFrac",
    )
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            // 凹槽圆
            drawCircle(color = DpadRecess.copy(alpha = alphaMul), radius = s * 0.425f, center = Offset(cx, cy))

            val env = s * 0.63f // 十字包络
            val thick = env * 0.375f // 臂厚
            val radius = thick * 0.28f
            val armColor = DpadArm.copy(alpha = alphaMul)
            val pressColor = HcPadRed.copy(alpha = if (translucent) 0.8f else 1f)

            fun armRect(dir: RetroButton): Pair<Offset, Size> = when (dir) {
                RetroButton.LEFT, RetroButton.RIGHT ->
                    Pair(Offset(cx - env / 2, cy - thick / 2), Size(env, thick))
                else ->
                    Pair(Offset(cx - thick / 2, cy - env / 2), Size(thick, env))
            }

            // 立体底影：静态沉底 + 朝按压反方向偏移——按「右」十字右移、影子从左侧露出（跷跷板翘起的离缝）
            val shadowColor = Color.Black.copy(alpha = 0.45f * alphaMul)
            translate(left = -nudgeX.toPx() * 0.6f, top = -nudgeY.toPx() * 0.6f + s * 0.025f) {
                drawRoundRect(color = shadowColor, topLeft = armRect(RetroButton.LEFT).first, size = armRect(RetroButton.LEFT).second, cornerRadius = CornerRadius(radius))
                drawRoundRect(color = shadowColor, topLeft = armRect(RetroButton.UP).first, size = armRect(RetroButton.UP).second, cornerRadius = CornerRadius(radius))
            }

            translate(left = nudgeX.toPx(), top = nudgeY.toPx() + sinkFrac * s * 0.03f) {
                // 十字底座（横竖臂交叠）
                drawRoundRect(color = armColor, topLeft = armRect(RetroButton.LEFT).first, size = armRect(RetroButton.LEFT).second, cornerRadius = CornerRadius(radius))
                drawRoundRect(color = armColor, topLeft = armRect(RetroButton.UP).first, size = armRect(RetroButton.UP).second, cornerRadius = CornerRadius(radius))

                // 按下方向红色高亮：臂端向内覆盖 DPAD_HIGHLIGHT_FRAC（与臂身同宽、圆角一致），斜向两条同亮
                pressedDirs.forEach { dir ->
                    val len = env * DPAD_HIGHLIGHT_FRAC
                    val (tl, sz) = when (dir) {
                        RetroButton.LEFT -> Pair(Offset(cx - env / 2, cy - thick / 2), Size(len, thick))
                        RetroButton.RIGHT -> Pair(Offset(cx + env / 2 - len, cy - thick / 2), Size(len, thick))
                        RetroButton.UP -> Pair(Offset(cx - thick / 2, cy - env / 2), Size(thick, len))
                        else -> Pair(Offset(cx - thick / 2, cy + env / 2 - len), Size(thick, len))
                    }
                    drawRoundRect(color = pressColor, topLeft = tl, size = sz, cornerRadius = CornerRadius(radius))
                }

                // 臂端白色三角箭头（距臂端约臂厚 45%）
                val triSize = thick * 0.42f
                val triInset = thick * 0.72f
                fun tri(dir: RetroButton): Path = Path().apply {
                    when (dir) {
                        RetroButton.UP -> {
                            moveTo(cx, cy - env / 2 + triInset - triSize)
                            lineTo(cx - triSize / 2, cy - env / 2 + triInset)
                            lineTo(cx + triSize / 2, cy - env / 2 + triInset)
                        }
                        RetroButton.DOWN -> {
                            moveTo(cx, cy + env / 2 - triInset + triSize)
                            lineTo(cx - triSize / 2, cy + env / 2 - triInset)
                            lineTo(cx + triSize / 2, cy + env / 2 - triInset)
                        }
                        RetroButton.LEFT -> {
                            moveTo(cx - env / 2 + triInset - triSize, cy)
                            lineTo(cx - env / 2 + triInset, cy - triSize / 2)
                            lineTo(cx - env / 2 + triInset, cy + triSize / 2)
                        }
                        else -> {
                            moveTo(cx + env / 2 - triInset + triSize, cy)
                            lineTo(cx + env / 2 - triInset, cy - triSize / 2)
                            lineTo(cx + env / 2 - triInset, cy + triSize / 2)
                        }
                    }
                    close()
                }
                listOf(RetroButton.UP, RetroButton.DOWN, RetroButton.LEFT, RetroButton.RIGHT).forEach { dir ->
                    drawPath(path = tri(dir), color = ArrowWhite.copy(alpha = alphaMul))
                }
            }
        }
        // 命中区 = 凹槽圆（0.85×边，与可见凹槽一致）：方框透明角落不拦截相邻控件
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(size * 0.85f)
                .clip(CircleShape)
                .dpadGesture(
                    onPressStart = haptic,
                    onDirsChange = { pressedDirs = it },
                    onButton = onButton,
                ),
        )
    }
}

/**
 * A / B 红色圆钮（素材观感）：横向排列（B 左、A 右略高），渐变红球 + 底部投影 + 深红字母；
 * 按下下沉 3dp 并提亮（spec 按下红色高亮）。
 */
@Composable
fun AbButtons(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    buttonSize: Dp = 68.dp,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(buttonSize * 0.24f),
        verticalAlignment = Alignment.Bottom,
    ) {
        RoundPadButton(
            button = RetroButton.B,
            modifier = Modifier.size(buttonSize),
            translucent = translucent,
            depth = buttonSize * 0.09f,
            onButton = onButton,
        )
        RoundPadButton(
            button = RetroButton.A,
            modifier = Modifier
                .size(buttonSize)
                .offset(y = -buttonSize * 0.32f),
            translucent = translucent,
            depth = buttonSize * 0.09f,
            onButton = onButton,
        )
    }
}

@Composable
private fun RoundPadButton(
    button: RetroButton,
    modifier: Modifier = Modifier,
    translucent: Boolean,
    depth: Dp = 6.dp,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = rememberPadFeedback()
    val alphaMul = if (translucent) 0.55f else 1f
    val label = if (button == RetroButton.A) "A" else "B"
    // 弹簧下沉至与投影齐平（按进面板）+ 提亮 alpha 动画
    val offsetY by animateDpAsState(
        targetValue = if (pressed) depth else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "padBtnY",
    )
    val bright by animateFloatAsState(
        targetValue = if (pressed) 0.22f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "padBtnBright",
    )
    Box(modifier = modifier) {
        if (!translucent) {
            // 底部投影：立体深度；按压时球体下沉 depth 与投影齐平
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = depth)
                    .clip(CircleShape)
                    .background(HcPadRedDeep.copy(alpha = alphaMul)),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = offsetY)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(RedTop.copy(alpha = alphaMul), HcPadRed.copy(alpha = alphaMul), RedBottom.copy(alpha = alphaMul)),
                    ),
                )
                .pointerInput(button) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        haptic()
                        pressed = true
                        onButton(button, true)
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) break
                        }
                        pressed = false
                        onButton(button, false)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // 球面光影：上缘受光、下缘入影的立体感
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.30f * alphaMul),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f * alphaMul),
                            ),
                        ),
                    ),
            )
            // 按下提亮（alpha 随弹簧过渡）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = bright)),
            )
            Text(
                text = label,
                color = RedLabel.copy(alpha = if (pressed) 0.9f else 1f),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Select / Start 胶囊（素材观感）：并排居中小胶囊，深灰底 + 深描边 + 白色粗体字，按下红。 */
@Composable
fun MenuPills(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Pill(button = RetroButton.SELECT, label = "SELECT", translucent = translucent, onButton = onButton)
        Pill(button = RetroButton.START, label = "START", translucent = translucent, onButton = onButton)
    }
}

@Composable
private fun Pill(
    button: RetroButton,
    label: String,
    translucent: Boolean,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = rememberPadFeedback()
    val alphaMul = if (translucent) 0.55f else 1f
    // 弹簧下沉至与投影齐平（按进面板）
    val offsetY by animateDpAsState(
        targetValue = if (pressed) 3.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillY",
    )
    Box(
        modifier = Modifier
            .size(width = 70.dp, height = 27.dp)
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    haptic()
                    pressed = true
                    onButton(button, true)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    pressed = false
                    onButton(button, false)
                }
            },
    ) {
        // 底部投影：立体深度；按压时胶囊下沉与投影齐平
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color.Black.copy(alpha = 0.45f * alphaMul)),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = offsetY)
                .clip(RoundedCornerShape(15.dp))
                .background(if (pressed) HcPadRed.copy(alpha = if (translucent) 0.8f else 1f) else PillBg.copy(alpha = alphaMul)),
            contentAlignment = Alignment.Center,
        ) {
            // 顶缘受光的立体感
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.16f * alphaMul), Color.Transparent),
                        ),
                    ),
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = PixelFontFamily,
                    fontWeight = FontWeight.Bold,
                ),
                fontSize = 9.sp,
            )
        }
    }
}

// ---- 虚拟摇杆（virtual-joystick）----
// 几何比例（相对方框短边）：竖屏底座须盖住十字挖孔（对角 ≈1.02×边）→ 底座直径 ≈1.02×短边；
// 横屏半透明浮层同参数，轻微越出方框无碍。方向判定全部走 JoystickMath（死区+迟滞）。

/** 底座半径 / 方框短边。 */
private const val JOY_BASE_FRAC = 0.51f
/** 摇杆帽半径 / 方框短边。 */
private const val JOY_CAP_FRAC = 0.30f
/** 满行程（帽最大位移）/ 方框短边。 */
private const val JOY_TRAVEL_FRAC = 0.24f

/**
 * 虚拟摇杆：代码绘制的推杆——深灰底座圆盘 + 径向渐变摇杆帽，推向方向时底座缘红色弧光。
 * 命中区为整个方框；以按下点为原点相对跟手（浮点摇杆，拇指落点即中心），帽位移跟手即时、
 * 松手弹簧回中（与现有按键手感同参）；方向集合变化按差集「先抬旧后按新」发回调，
 * 首次按下触觉反馈一次、换向不震（与现有一致）。
 */
@Composable
fun JoystickControl(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    val haptic = rememberPadFeedback()
    var dragging by remember { mutableStateOf(false) }
    var dirs by remember { mutableStateOf<Set<RetroButton>>(emptySet()) }
    // 帽位移（px，相对中心）：按住直接赋值跟手；松手由 LaunchedEffect 从松手点弹簧回零
    // （awaitEachGesture 为受限挂起作用域，不能直接调 Animatable.snapTo/animateTo）
    var capPx by remember { mutableStateOf(Offset.Zero) }
    var releaseFrom by remember { mutableStateOf(Offset.Zero) }
    val settle = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(dragging) {
        if (!dragging) {
            settle.snapTo(releaseFrom)
            settle.animateTo(
                Offset.Zero,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    val alphaMul = if (translucent) 0.55f else 1f

    Box(
        modifier = modifier
            .alpha(alphaMul)
            .clip(CircleShape) // 命中裁成圆形：方框透明角落不拦截相邻控件
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    haptic()
                    dragging = true
                    capPx = Offset.Zero
                    val downId = down.id
                    val travel = minOf(size.width, size.height) * JOY_TRAVEL_FRAC
                    var current: Set<RetroButton> = emptySet()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pt = event.changes.firstOrNull { it.id == downId }
                            if (pt == null || !pt.pressed) break
                            // 相对按下点的位移；方向判定用原始量，帽位置截断到满行程
                            val raw = pt.position - down.position
                            val dist = raw.getDistance()
                            capPx = if (dist > travel) raw * (travel / dist) else raw
                            val n = raw / travel
                            val nd = JoystickMath.directions(n.x, n.y, current)
                            if (nd != current) {
                                (current - nd).forEach { onButton(it, false) }
                                (nd - current).forEach { onButton(it, true) }
                                current = nd
                                dirs = nd
                            }
                        }
                    } finally {
                        releaseFrom = capPx
                        dragging = false
                        dirs = emptySet()
                        current.forEach { onButton(it, false) }
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val center = Offset(cx, cy)
            val baseR = s * JOY_BASE_FRAC
            val capR = s * JOY_CAP_FRAC
            val pos = if (dragging) capPx else settle.value
            val mag = (pos.getDistance() / (s * JOY_TRAVEL_FRAC)).coerceIn(0f, 1f)

            // 底座：不透明深灰圆盘（竖屏遮蔽十字挖孔）+ 暗描边
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF2E2E2E), DpadRecess, Color(0xFF101010)),
                    center = center,
                    radius = baseR,
                ),
                radius = baseR,
                center = center,
            )
            drawCircle(
                color = PillBorderDark,
                radius = baseR,
                center = center,
                style = Stroke(width = s * 0.012f),
            )

            // 方向高亮：底座缘沿推杆角的红色弧光（弧宽随推杆量增亮）
            if (dirs.isNotEmpty() && mag > 0.02f) {
                val ang = Math.toDegrees(kotlin.math.atan2(pos.y, pos.x).toDouble()).toFloat()
                drawArc(
                    color = HcPadRed.copy(alpha = 0.25f + 0.55f * mag),
                    startAngle = ang - 40f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(cx - baseR, cy - baseR),
                    size = Size(baseR * 2f, baseR * 2f),
                    style = Stroke(width = s * 0.035f, cap = StrokeCap.Round),
                )
            }

            // 帽：径向渐变球（高光偏左上）+ 描边 + 高光点；推向方向时轻微红晕
            val capC = center + pos
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF565656), Color(0xFF2B2B2B), Color(0xFF1C1C1C)),
                    center = capC - Offset(capR * 0.3f, capR * 0.3f),
                    radius = capR * 1.5f,
                ),
                radius = capR,
                center = capC,
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = capR,
                center = capC,
                style = Stroke(width = s * 0.012f),
            )
            if (dirs.isNotEmpty()) {
                drawCircle(color = HcPadRed.copy(alpha = 0.14f), radius = capR, center = capC)
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = capR * 0.16f,
                center = capC - Offset(capR * 0.38f, capR * 0.38f),
            )
        }
    }
}

// ---- 竖屏控制面板（全代码绘制）----

/** 竖屏控制面板（全代码绘制）：深色圆角面板底 + 十字键 + 红色 A/B + SELECT/START 胶囊，
 *  与横屏浮层共用同一套控件与设计语言（三轮：弃皮肤底图——切片烘焙环按压错位，且切片
 *  与代码控件混排观感割裂；skin_*.png 不再被面板引用）。方向控制按 scheme 分两形态。 */
@Composable
fun GamepadPanel(
    onButton: (RetroButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scheme: ControlScheme = ControlScheme.DPAD,
) {
    // 面板加高（386/215）：压缩内容区与控制区的视觉距离，同时容纳更大的按键
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(386f / 215f)) {
        val h = maxHeight
        // 面板底：深色圆角（游戏屏黑底上以圆角与色阶分区），内一圈细描边增加轮廓
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRoundRect(
                color = HcPanelDark,
                cornerRadius = CornerRadius(size.height * 0.14f),
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
                cornerRadius = CornerRadius(size.height * 0.14f),
            )
        }
        // 十字键靠左上：给底部居中的胶囊让出命中空间（圆命中半径避让，见 tasks 8.1）
        if (scheme == ControlScheme.DPAD) {
            DpadControl(
                size = h * 0.84f,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = h * 0.04f, top = h * 0.02f),
                onButton = onButton,
            )
        } else {
            // 摇杆底座圆（1.02×边）比十字键凹槽大，缩小一档避免压到 SELECT
            JoystickControl(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = h * 0.04f, top = h * 0.02f)
                    .size(h * 0.75f),
                onButton = onButton,
            )
        }
        AbButtons(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(y = -h * 0.06f)
                .padding(end = h * 0.07f),
            buttonSize = h * 0.34f,
            onButton = onButton,
        )
        MenuPills(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = h * 0.05f),
            onButton = onButton,
        )
    }
}

/** 横屏全屏浮层装配：方向控制（十字键/摇杆按 scheme）居左、A/B 居右、Select/Start 底部居中。
 *  两侧集群整体下移（真机反馈：垂直居中太偏上）并内收（真机反馈：贴边太靠外）——
 *  下移 70dp 落到拇指热区（约屏高 2/3 处），左右各内收 60dp 向画面靠拢。 */
@Composable
fun PadControlsOverlay(
    onButton: (RetroButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scheme: ControlScheme = ControlScheme.DPAD,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (scheme == ControlScheme.DPAD) {
            DpadControl(
                size = 150.dp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 60.dp)
                    .offset(y = 70.dp),
                translucent = true,
                onButton = onButton,
            )
        } else {
            JoystickControl(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 60.dp)
                    .size(150.dp)
                    .offset(y = 70.dp),
                translucent = true,
                onButton = onButton,
            )
        }
        AbButtons(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 60.dp)
                .offset(y = 70.dp),
            translucent = true,
            onButton = onButton,
        )
        MenuPills(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
            translucent = true,
            onButton = onButton,
        )
    }
}
