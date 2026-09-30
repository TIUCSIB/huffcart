package com.huffcart.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huffcart.core.bridge.RetroButton
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcPanelButton
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcPanelPressed
import com.huffcart.app.ui.theme.HcPanelPressedBright
import com.huffcart.app.ui.theme.HcRed

/**
 * 组件化虚拟手柄（retro-ui-redesign 决策 4）：每个按键独立 pointerInput 命中，
 * 按下红色高亮；竖屏放深色控制面板、横屏作半透明浮层，一套组件两处装配。
 */

/** 按住型按键：down 置位、全部指针抬起复位。 */
@Composable
private fun HoldButton(
    button: RetroButton,
    shape: Shape,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    var pressed by remember { mutableStateOf(false) }
    val base = if (filled) {
        HcRed
    } else {
        HcPanelButton.copy(alpha = if (translucent) 0.55f else 1f)
    }
    val background = if (pressed) {
        (if (filled) HcPanelPressedBright else HcPanelPressed).copy(alpha = if (translucent && !filled) 0.8f else 1f)
    } else {
        base
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(
                if (filled) {
                    Modifier
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = HcCream.copy(alpha = 0.25f),
                        shape = shape,
                    )
                },
            )
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onButton(button, true)
                    // awaitAllPointersUp 为 internal API，手动等待全部指针抬起
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) break
                    }
                    pressed = false
                    onButton(button, false)
                }
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** 十字键：四臂独立命中，中心为装饰圆点。 */
@Composable
fun DpadControl(
    size: Dp,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    val armThickness = size * 0.36f
    val armLength = size * 0.60f
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        DirectionArm(
            button = RetroButton.UP,
            icon = Icons.Filled.KeyboardArrowUp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = armThickness, height = armLength),
            translucent = translucent,
            onButton = onButton,
        )
        DirectionArm(
            button = RetroButton.DOWN,
            icon = Icons.Filled.KeyboardArrowDown,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = armThickness, height = armLength),
            translucent = translucent,
            onButton = onButton,
        )
        DirectionArm(
            button = RetroButton.LEFT,
            icon = Icons.Filled.KeyboardArrowLeft,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = armLength, height = armThickness),
            translucent = translucent,
            onButton = onButton,
        )
        DirectionArm(
            button = RetroButton.RIGHT,
            icon = Icons.Filled.KeyboardArrowRight,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = armLength, height = armThickness),
            translucent = translucent,
            onButton = onButton,
        )
        Box(
            modifier = Modifier
                .size(size * 0.24f)
                .background(
                    HcPanelDark.copy(alpha = if (translucent) 0.9f else 1f),
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun DirectionArm(
    button: RetroButton,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    translucent: Boolean,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    HoldButton(
        button = button,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
        translucent = translucent,
        onButton = onButton,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(26.dp),
        )
    }
}

/** A / B 红色圆形按钮（B 左下、A 右上，与设计稿一致）。 */
@Composable
fun AbButtons(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    Box(modifier = modifier.size(width = 150.dp, height = 150.dp)) {
        HoldButton(
            button = RetroButton.B,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(70.dp),
            filled = true,
            translucent = translucent,
            onButton = onButton,
        ) {
            Text(text = "B", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        }
        HoldButton(
            button = RetroButton.A,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(70.dp),
            filled = true,
            translucent = translucent,
            onButton = onButton,
        ) {
            Text(text = "A", color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        }
    }
}

/** Select / Start 胶囊。 */
@Composable
fun MenuPills(
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    onButton: (RetroButton, Boolean) -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        HoldButton(
            button = RetroButton.SELECT,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(width = 88.dp, height = 32.dp),
            translucent = translucent,
            onButton = onButton,
        ) {
            Text(
                text = "SELECT",
                color = Color.White.copy(alpha = 0.85f),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        HoldButton(
            button = RetroButton.START,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(width = 88.dp, height = 32.dp),
            translucent = translucent,
            onButton = onButton,
        ) {
            Text(
                text = "START",
                color = Color.White.copy(alpha = 0.85f),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
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
