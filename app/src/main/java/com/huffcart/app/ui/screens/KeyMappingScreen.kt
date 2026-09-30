package com.huffcart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.view.KeyEvent
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.game.KeyMappingStore
import com.huffcart.core.bridge.RetroButton
import com.huffcart.app.ui.theme.HcChipBg
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcPanelButton
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcRed

private val mappingRows = listOf(
    RetroButton.UP to "上",
    RetroButton.DOWN to "下",
    RetroButton.LEFT to "左",
    RetroButton.RIGHT to "右",
    RetroButton.A to "A 键",
    RetroButton.B to "B 键",
)

/** 按键设置页（retro-ui-redesign）：布局预览 + 映射行点按重绑 + 恢复默认。 */
@Composable
fun KeyMappingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var mapping by remember { mutableStateOf(KeyMappingStore.load(context)) }
    var listening by remember { mutableStateOf<RetroButton?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val target = listening
                if (
                    target != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.nativeKeyEvent.repeatCount == 0 &&
                    event.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_BACK
                ) {
                    mapping = mapping + (target to event.nativeKeyEvent.keyCode)
                    KeyMappingStore.save(context, mapping)
                    listening = null
                    true
                } else {
                    false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "按键设置", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                PadPreview()
                Spacer(modifier = Modifier.height(16.dp))
                mappingRows.forEachIndexed { index, (button, label) ->
                    MappingRow(
                        label = label,
                        value = if (listening == button) {
                            "请按下新键…"
                        } else {
                            KeyMappingStore.keyLabel(mapping[button] ?: 0)
                        },
                        listening = listening == button,
                        onClick = {
                            listening = button
                            focusRequester.requestFocus()
                        },
                    )
                    if (index != mappingRows.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        mapping = KeyMappingStore.defaults()
                        KeyMappingStore.save(context, mapping)
                        listening = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = "恢复默认")
                }
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CartridgeMark()
                    Text(
                        text = "  INSERT CARTRIDGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = HcRed,
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** 控制布局预览面板（静态示意，纯 Canvas）。 */
@Composable
private fun PadPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HcPanelDark),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val crossCx = size.width * 0.22f
            val crossCy = size.height * 0.5f
            val armW = size.width * 0.05f
            val armL = size.width * 0.16f
            drawRoundRect(
                color = HcPanelButton,
                topLeft = androidx.compose.ui.geometry.Offset(crossCx - armL / 2, crossCy - armW / 2),
                size = androidx.compose.ui.geometry.Size(armL, armW),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
            drawRoundRect(
                color = HcPanelButton,
                topLeft = androidx.compose.ui.geometry.Offset(crossCx - armW / 2, crossCy - armL / 2),
                size = androidx.compose.ui.geometry.Size(armW, armL),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
            drawCircle(color = HcRed, radius = size.height * 0.11f, center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.40f))
            drawCircle(color = HcRed, radius = size.height * 0.11f, center = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.58f))
        }
    }
}

@Composable
private fun MappingRow(label: String, value: String, listening: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (listening) HcRed.copy(alpha = 0.15f) else HcChipBg,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = if (listening) HcRed else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** 卡带小图标（Canvas 矢量，INSERT CARTRIDGE 装饰）。 */
@Composable
private fun CartridgeMark() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .padding(end = 4.dp)
            .height(16.dp)
            .width(22.dp),
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = HcRed,
            topLeft = Offset.Zero,
            size = Size(w, h * 0.82f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        drawRect(
            color = HcCream,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.28f),
        )
        drawRect(color = HcRed, topLeft = Offset(w * 0.14f, h * 0.86f), size = Size(w * 0.20f, h * 0.14f))
        drawRect(color = HcRed, topLeft = Offset(w * 0.66f, h * 0.86f), size = Size(w * 0.20f, h * 0.14f))
    }
}
