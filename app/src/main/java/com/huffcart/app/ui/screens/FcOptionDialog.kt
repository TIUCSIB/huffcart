package com.huffcart.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.huffcart.app.ui.theme.HcOnLight
import com.huffcart.app.ui.theme.HcOnLightVariant
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcSurfaceLight
import com.huffcart.app.ui.theme.PixelFontFamily

/**
 * 复古单选弹窗（virtual-joystick 3.3 二轮样式；pad-feedback-and-display-settings 任务 2.2
 * 自按键设置页抽取共用）：简洁白卡（圆角、无描边阴影）+ 像素字标题 + FC 菜单式红色光标；
 * 点选即生效并关闭（FC 菜单没有确认键），点卡片外关闭。虚拟手柄样式与画面比例两处复用。
 */
@Composable
fun <T> FcOptionDialog(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onSelect: (T) -> Unit,
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
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = HcOnLight,
            )
            Spacer(modifier = Modifier.height(8.dp))
            options.forEachIndexed { index, (option, label) ->
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
                if (index != options.lastIndex) {
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
