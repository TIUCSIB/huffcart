package com.huffcart.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * 金手指管理面板（cheat-codes）：FC 白卡风格 Dialog，与槽位面板同族。逐条开关即时回调，
 * 添加/删除由宿主落盘并批应用到核心；呈现期间游戏继续运行（核心逐帧应用码集）。
 */
@Composable
fun CheatPanel(
    entries: List<CheatEntry>,
    onToggle: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HcSurfaceLight)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = "金手指",
                style = MaterialTheme.typography.titleMedium,
                color = HcOnLight,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Game Genie 码：6 或 8 位字母，连字符可省略",
                style = MaterialTheme.typography.labelSmall,
                color = HcOnLightVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.code,
                        fontFamily = PixelFontFamily,
                        fontSize = 13.sp,
                        color = if (entry.enabled) HcRed else HcOnLightVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = entry.enabled,
                        onCheckedChange = { onToggle(index) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = HcRed,
                            checkedThumbColor = HcSurfaceLight,
                        ),
                    )
                    IconButton(onClick = { onDelete(index) }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "删除${entry.code}",
                            tint = HcOnLightVariant,
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                Text(
                    text = "还没有金手指，输入码后点添加",
                    style = MaterialTheme.typography.labelMedium,
                    color = HcOnLightVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    singleLine = true,
                    isError = error != null,
                    placeholder = { Text("输入金手指码", style = MaterialTheme.typography.bodySmall) },
                    supportingText = error?.let { msg -> { Text(msg, color = HcRed) } },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        if (CheatStore.isValidGameGenie(input)) {
                            onAdd(CheatStore.normalize(input))
                            input = ""
                            error = null
                        } else {
                            error = "格式错误：需 6/8 位 GG 字母"
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = HcRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "添加", color = HcRed)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "开关即时生效",
                style = MaterialTheme.typography.labelSmall,
                color = HcOnLightVariant,
            )
        }
    }
}
