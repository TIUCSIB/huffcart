package com.huffcart.app.ui.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.huffcart.app.ui.theme.HcOnLight
import com.huffcart.app.ui.theme.HcOnLightVariant
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcSurfaceLight
import com.huffcart.app.ui.theme.PixelFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 存档槽面板（audio-settings-and-save-management 决策 4）：快捷菜单「存档 / 读档」点开后弹出，
 * 四槽卡片（槽号 / 缩略图 / 时间戳 / 空槽态），两种模式复用同一组件——竖屏顶栏与横屏浮层
 * 各自打开同一面板。SAVE 模式点槽即存（覆盖已有槽为覆盖语义）；LOAD 模式空槽置灰不可点。
 */
enum class SaveSlotPanelMode(val title: String, val hint: String) {
    SAVE("存档", "点选槽位即保存；点已有槽位为覆盖"),
    LOAD("读档", "点选已有存档的槽位"),
}

@Composable
fun SaveSlotPanel(
    mode: SaveSlotPanelMode,
    metas: List<SlotMeta?>,
    thumbnails: List<android.graphics.Bitmap?>,
    onSelect: (Int) -> Unit,
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
                text = mode.title,
                style = MaterialTheme.typography.titleMedium,
                color = HcOnLight,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mode.hint,
                style = MaterialTheme.typography.labelSmall,
                color = HcOnLightVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            metas.forEachIndexed { index, meta ->
                SlotRow(
                    slotLabel = "槽位 ${index + 1}",
                    meta = meta,
                    thumbnail = thumbnails.getOrNull(index),
                    enabled = mode == SaveSlotPanelMode.SAVE || meta != null,
                    showSaveHint = mode == SaveSlotPanelMode.SAVE,
                    onClick = { onSelect(index) },
                )
                if (index != metas.lastIndex) {
                    HorizontalDivider(color = HcOutlineLight)
                }
            }
            if (mode == SaveSlotPanelMode.LOAD && metas.all { it == null }) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "暂无存档，先在游戏中存一档吧",
                    style = MaterialTheme.typography.labelMedium,
                    color = HcRed,
                )
            }
        }
    }
}

@Composable
private fun SlotRow(
    slotLabel: String,
    meta: SlotMeta?,
    thumbnail: android.graphics.Bitmap?,
    enabled: Boolean,
    showSaveHint: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = slotLabel,
            fontFamily = PixelFontFamily,
            fontSize = 13.sp,
            color = if (enabled) HcOnLight else HcOnLightVariant,
            modifier = Modifier.width(64.dp),
        )
        SlotThumb(thumbnail = thumbnail, occupied = meta != null)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (meta != null) formatTime(meta.savedAt) else "空",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) HcOnLight else HcOnLightVariant,
            )
            if (meta == null && showSaveHint) {
                Text(
                    text = "点击保存",
                    style = MaterialTheme.typography.labelSmall,
                    color = HcOnLightVariant,
                )
            }
        }
    }
}

/** 槽位缩略图：有图按 256:240 原比例最近邻呈现；无图/空槽为深色占位块。 */
@Composable
private fun SlotThumb(thumbnail: android.graphics.Bitmap?, occupied: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (occupied) HcPanelDark else HcPanelDark.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumbnail
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(bmp.width.toFloat() / bmp.height.toFloat()),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        } else {
            Text(
                text = if (occupied) "无图" else "空",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

private fun formatTime(savedAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(savedAt))
