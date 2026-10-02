package com.huffcart.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.game.DisplayAspect
import com.huffcart.app.ui.game.VideoSettingsStore
import com.huffcart.app.ui.theme.HcChipBg

/**
 * 画面设置页（pad-feedback-and-display-settings）：画面比例三档（原生 8:7 / 经典 4:3 / 铺满），
 * 复用 FC 单选弹窗（FcOptionDialog）；选择即持久化，游戏屏（GameSession 构造）读取一次生效。
 * 行样式对齐按键设置页 MappingRow（左标签右值芯片）；后续显示类设置（滤镜等）在本页扩展。
 */
@Composable
fun DisplaySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var aspect by remember { mutableStateOf(VideoSettingsStore.load(context)) }
    var aspectDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "画面设置", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            AspectRow(
                label = "画面比例",
                value = aspect.label,
                onClick = { aspectDialog = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
    if (aspectDialog) {
        FcOptionDialog(
            title = "画面比例",
            options = DisplayAspect.entries.map { it to it.label },
            current = aspect,
            onSelect = { option ->
                aspect = option
                VideoSettingsStore.save(context, option)
                aspectDialog = false
            },
            onDismiss = { aspectDialog = false },
        )
    }
}

@Composable
private fun AspectRow(label: String, value: String, onClick: () -> Unit) {
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
        Surface(shape = RoundedCornerShape(8.dp), color = HcChipBg) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
