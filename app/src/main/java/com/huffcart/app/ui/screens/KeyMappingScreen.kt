package com.huffcart.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.view.KeyEvent
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.game.ControlScheme
import com.huffcart.app.ui.game.ControlSchemeStore
import com.huffcart.app.ui.game.KeyMappingStore
import com.huffcart.core.bridge.RetroButton
import com.huffcart.app.ui.theme.HcChipBg
import com.huffcart.app.ui.theme.HcOnLight
import com.huffcart.app.ui.theme.HcOnLightVariant
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcSurfaceLight
import com.huffcart.app.ui.theme.PixelFontFamily
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.huffcart.app.R

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
    // 控制形态（virtual-joystick）：切换即持久化，游戏屏下次进入时生效
    var scheme by remember { mutableStateOf(ControlSchemeStore.load(context)) }
    var schemeDialog by remember { mutableStateOf(false) }

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
                Image(
                    bitmap = ImageBitmap.imageResource(R.drawable.asset_gamepad),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(386f / 186f),
                    filterQuality = FilterQuality.None,
                )
                Spacer(modifier = Modifier.height(16.dp))
                // 虚拟手柄样式（virtual-joystick）：十字键（默认）/ 摇杆
                MappingRow(
                    label = "虚拟手柄样式",
                    value = if (scheme == ControlScheme.JOYSTICK) "摇杆" else "十字键",
                    listening = false,
                    onClick = { schemeDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
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
    if (schemeDialog) {
        SchemePickerDialog(
            current = scheme,
            onSelect = { option ->
                scheme = option
                ControlSchemeStore.save(context, option)
                schemeDialog = false
            },
            onDismiss = { schemeDialog = false },
        )
    }
}

/** 复古单选弹窗（virtual-joystick 3.3 二轮：弃 Material AlertDialog，契合 FC 主题）：
 *  简洁白卡（圆角、无描边阴影）+ 像素字标题 + FC 菜单式红色光标；
 *  点选即生效并关闭（FC 菜单没有确认键），点卡片外关闭。 */
@Composable
private fun SchemePickerDialog(
    current: ControlScheme,
    onSelect: (ControlScheme) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HcSurfaceLight)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
                Text(
                    text = "虚拟手柄样式",
                    style = MaterialTheme.typography.titleMedium,
                    color = HcOnLight,
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    ControlScheme.DPAD to "十字键",
                    ControlScheme.JOYSTICK to "摇杆",
                ).forEach { (option, label) ->
                    val selected = option == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // FC 菜单光标：占位对齐，选中亮红色三角
                        Box(modifier = Modifier.size(width = 16.dp, height = 12.dp)) {
                            if (selected) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, size.height / 2f)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                    drawPath(path, color = HcRed)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            fontFamily = PixelFontFamily,
                            fontSize = 15.sp,
                            color = if (selected) HcRed else HcOnLight,
                        )
                    }
                    if (option != ControlScheme.JOYSTICK) {
                        HorizontalDivider(color = HcOutlineLight)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点选即生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = HcOnLightVariant,
                )
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

/** 卡带小图标（素材切片，INSERT CARTRIDGE 装饰）。 */
@Composable
private fun CartridgeMark() {
    Image(
        bitmap = ImageBitmap.imageResource(R.drawable.asset_cartridge),
        contentDescription = null,
        modifier = Modifier
            .padding(end = 4.dp)
            .height(18.dp)
            .aspectRatio(125f / 124f),
        filterQuality = FilterQuality.None,
    )
}
