package com.huffcart.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huffcart.core.bridge.RetroButton
import com.huffcart.app.ui.theme.HcPadRed
import com.huffcart.app.ui.theme.HcPadRedDeep

// 素材手柄实测色（用户参考图采样）
private val DpadArm = Color(0xFF3C3C3C)
private val DpadRecess = Color(0xFF252525)
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

/** 按住型透明命中层：down 置位、全部指针抬起复位，视觉由调用方绘制。 */
@Composable
private fun HitArea(
    button: RetroButton,
    shape: Shape,
    modifier: Modifier = Modifier,
    translucent: Boolean,
    onButton: (RetroButton, Boolean) -> Unit,
    onVisualPress: (Boolean) -> Unit = {},
) {
    Box(
        modifier = modifier
            .clip(shape)
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onVisualPress(true)
                    onButton(button, true)
                    // awaitAllPointersUp 为 internal API，手动等待全部指针抬起
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    onVisualPress(false)
                    onButton(button, false)
                }
            },
    )
}

/**
 * 十字键（素材观感）：凹槽圆 + 圆润十字 + 臂端白色三角箭头。
 * 按下方向臂整臂红色高亮；四向命中区几何与十字臂一致（中带交叠处斜向双键生效）。
 */
@Composable
fun DpadControl(
    size: Dp,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf<RetroButton?>(null) }
    val alphaMul = if (translucent) 0.55f else 1f
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

            // 十字底座（横竖臂交叠）
            drawRoundRect(color = armColor, topLeft = armRect(RetroButton.LEFT).first, size = armRect(RetroButton.LEFT).second, cornerRadius = CornerRadius(radius))
            drawRoundRect(color = armColor, topLeft = armRect(RetroButton.UP).first, size = armRect(RetroButton.UP).second, cornerRadius = CornerRadius(radius))

            // 按下方向臂红色高亮
            pressed?.let { dir ->
                val (tl, sz) = armRect(dir)
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
        // 透明命中区（视觉全在 Canvas）
        val env = size * 0.63f
        val thick = env * 0.375f
        HitArea(
            button = RetroButton.UP,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.TopCenter).size(width = thick, height = env),
            translucent = translucent,
            onButton = onButton,
            onVisualPress = { p -> pressed = if (p) RetroButton.UP else null },
        )
        HitArea(
            button = RetroButton.DOWN,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.BottomCenter).size(width = thick, height = env),
            translucent = translucent,
            onButton = onButton,
            onVisualPress = { p -> pressed = if (p) RetroButton.DOWN else null },
        )
        HitArea(
            button = RetroButton.LEFT,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.CenterStart).size(width = env, height = thick),
            translucent = translucent,
            onButton = onButton,
            onVisualPress = { p -> pressed = if (p) RetroButton.LEFT else null },
        )
        HitArea(
            button = RetroButton.RIGHT,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.align(Alignment.CenterEnd).size(width = env, height = thick),
            translucent = translucent,
            onButton = onButton,
            onVisualPress = { p -> pressed = if (p) RetroButton.RIGHT else null },
        )
    }
}

/**
 * A / B 红色圆钮（素材观感）：垂直渐变红球 + 底部投影 + 深红字母；
 * 按下下沉 3dp 并提亮（spec 按下红色高亮）。B 左下、A 右上，与设计稿一致。
 */
@Composable
fun AbButtons(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    Box(modifier = modifier.size(width = 150.dp, height = 150.dp)) {
        RoundPadButton(
            button = RetroButton.B,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(74.dp),
            translucent = translucent,
            onButton = onButton,
        )
        RoundPadButton(
            button = RetroButton.A,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(74.dp),
            translucent = translucent,
            onButton = onButton,
        )
    }
}

@Composable
private fun RoundPadButton(
    button: RetroButton,
    modifier: Modifier = Modifier,
    translucent: Boolean,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val alphaMul = if (translucent) 0.55f else 1f
    val label = if (button == RetroButton.A) "A" else "B"
    Box(modifier = modifier) {
        if (!translucent) {
            // 底部投影：按钮下沉时被按下缘遮住，形成"按进去"的手感
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = 5.dp)
                    .clip(CircleShape)
                    .background(HcPadRedDeep),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = if (pressed && !translucent) 3.dp else 0.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(RedTop.copy(alpha = alphaMul), HcPadRed.copy(alpha = alphaMul), RedBottom.copy(alpha = alphaMul)),
                    ),
                )
                .pointerInput(button) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
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
            if (pressed) {
                // 按下提亮
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.22f)),
                )
            }
            Text(
                text = label,
                color = RedLabel.copy(alpha = if (pressed) 0.9f else 1f),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Select / Start 胶囊（素材观感）：深灰底 + 深描边 + 白色粗体字，按下红。 */
@Composable
fun MenuPills(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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
    val alphaMul = if (translucent) 0.55f else 1f
    Box(
        modifier = Modifier
            .size(width = 88.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, PillBorderDark.copy(alpha = alphaMul), RoundedCornerShape(16.dp))
            .background(if (pressed) HcPadRed.copy(alpha = if (translucent) 0.8f else 1f) else PillBg.copy(alpha = alphaMul))
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
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
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** 横屏全屏浮层装配：十字键居左、A/B 居右、Select/Start 底部居中。 */
@Composable
fun PadControlsOverlay(
    onButton: (RetroButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        DpadControl(
            size = 150.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp),
            translucent = true,
            onButton = onButton,
        )
        AbButtons(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp),
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
