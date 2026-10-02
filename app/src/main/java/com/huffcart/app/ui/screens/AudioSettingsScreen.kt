package com.huffcart.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.huffcart.app.ui.game.AudioSettings
import com.huffcart.app.ui.game.AudioSettingsStore
import com.huffcart.app.ui.theme.HcChipBg
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcSurfaceLight

/**
 * 声音设置页（audio-settings-and-save-management）：游戏音量滑条 + 静音 / 快进静音开关，
 * 任一调整即时持久化；游戏屏（GameSession 构造）读取一次生效。行样式对齐画面设置页；
 * 后续声音类设置在本页扩展。
 */
@Composable
fun AudioSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AudioSettingsStore.load(context)) }

    fun update(transform: (AudioSettings) -> AudioSettings) {
        settings = transform(settings)
        AudioSettingsStore.save(context, settings)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "声音设置", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            VolumeRow(
                volumePercent = settings.volumePercent,
                muted = settings.muted,
                onVolumeChange = { percent ->
                    update { it.copy(volumePercent = percent) }
                },
            )
            HorizontalDivider(color = HcOutlineLight)
            SwitchRow(
                label = "静音",
                note = "开启后游戏全程无声",
                checked = settings.muted,
                onCheckedChange = { muted -> update { it.copy(muted = muted) } },
            )
            HorizontalDivider(color = HcOutlineLight)
            SwitchRow(
                label = "快进静音",
                note = "快进期间无声，恢复常速后声音照常",
                checked = settings.ffMuted,
                onCheckedChange = { ffMuted -> update { it.copy(ffMuted = ffMuted) } },
            )
            HorizontalDivider(color = HcOutlineLight)
        }
    }
}

/** 音量行：左标签 + 百分比芯片，下滑条占整行（禁用时滑条置灰但保留当前值展示）。 */
@Composable
private fun VolumeRow(
    volumePercent: Int,
    muted: Boolean,
    onVolumeChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "游戏音量",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$volumePercent%",
                style = MaterialTheme.typography.labelLarge,
                color = if (muted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    HcRed
                },
            )
        }
        Slider(
            value = volumePercent.toFloat(),
            onValueChange = { onVolumeChange(it.toInt().coerceIn(0, 100)) },
            valueRange = 0f..100f,
            steps = 19, // 5% 一档：拖动可细调，值稳定可读
            enabled = !muted,
            colors = SliderDefaults.colors(
                thumbColor = HcRed,
                activeTrackColor = HcRed,
                inactiveTrackColor = HcChipBg,
            ),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    note: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HcRed,
                checkedThumbColor = HcSurfaceLight,
            ),
        )
    }
}
