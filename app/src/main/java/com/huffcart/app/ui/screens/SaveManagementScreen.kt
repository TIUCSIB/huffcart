package com.huffcart.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.game.SaveSlotStore
import com.huffcart.app.ui.game.SlotMeta
import com.huffcart.app.ui.game.SlotThumbnails
import com.huffcart.app.ui.library.CoverImage
import com.huffcart.app.ui.theme.HcOnLight
import com.huffcart.app.ui.theme.HcOnLightVariant
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcPanelDark
import com.huffcart.app.ui.theme.HcRed
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 存档管理页（audio-settings-and-save-management「存档槽位管理」）：按游戏管理存档槽。
 * 首层列出拥有存档槽的游戏（封面 + 名称 + 槽位数），进入后展示 4 槽缩略图/时间戳并支持
 * 删除（即时移除状态文件与缩略图侧车，不影响其他槽与 SRAM）。页面内二级钻取，返回逐级退出。
 */
@Composable
fun SaveManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val savesDir = remember { File(context.filesDir, SaveSlotStore.DIR_NAME) }
    var selectedGame by remember { mutableStateOf<String?>(null) }
    // 删除后自增，驱动槽位/游戏列表重扫磁盘
    var refresh by remember { mutableIntStateOf(0) }

    BackHandler(enabled = selectedGame != null) { selectedGame = null }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "存档管理", onBack = onBack)
        val games = remember(refresh) { SaveSlotStore.gamesWithSlots(savesDir) }
        val selected = selectedGame
        when {
            games.isEmpty() -> EmptyGuide()
            selected != null -> SlotList(
                savesDir = savesDir,
                game = selected,
                refreshKey = refresh,
                onDelete = { slot ->
                    SaveSlotStore.deleteSlot(savesDir, selected, slot)
                    // 删到一无所有则退回游戏列表（该游戏随之从列表消失）
                    if (SaveSlotStore.slots(savesDir, selected).all { it == null }) {
                        selectedGame = null
                    }
                    refresh++
                },
            )
            else -> GameList(
                savesDir = savesDir,
                games = games,
                refreshKey = refresh,
                onOpen = { selectedGame = it },
            )
        }
    }
}

@Composable
private fun EmptyGuide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = null,
            tint = HcOutlineLight,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无存档",
            style = MaterialTheme.typography.titleMedium,
            color = HcOnLight,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "进入游戏，菜单选择「存档」即可保存进度；\n存档在这里管理。",
            style = MaterialTheme.typography.bodyMedium,
            color = HcOnLightVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GameList(
    savesDir: File,
    games: List<String>,
    refreshKey: Int,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        games.forEachIndexed { index, game ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(game) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    gameName = game,
                    version = refreshKey,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = game,
                    style = MaterialTheme.typography.bodyLarge,
                    color = HcOnLight,
                    modifier = Modifier.weight(1f),
                )
                val slotCount = remember(game, refreshKey) {
                    SaveSlotStore.slots(savesDir, game).count { it != null }
                }
                Text(
                    text = "$slotCount 个存档",
                    style = MaterialTheme.typography.labelMedium,
                    color = HcOnLightVariant,
                )
            }
            if (index != games.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = HcOutlineLight,
                )
            }
        }
    }
}

@Composable
private fun SlotList(
    savesDir: File,
    game: String,
    refreshKey: Int,
    onDelete: (Int) -> Unit,
) {
    val context = LocalContext.current
    val slots = remember(game, refreshKey) { SaveSlotStore.slots(savesDir, game) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = game,
            style = MaterialTheme.typography.titleSmall,
            color = HcOnLight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        slots.forEachIndexed { index, meta ->
            SlotManagementRow(
                slotLabel = "槽位 ${index + 1}",
                meta = meta,
                thumbnail = meta?.let { SlotThumbnails.decode(context, game, it.slot) },
                onDelete = { onDelete(index) },
            )
            if (index != slots.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = HcOutlineLight,
                )
            }
        }
    }
}

@Composable
private fun SlotManagementRow(
    slotLabel: String,
    meta: SlotMeta?,
    thumbnail: android.graphics.Bitmap?,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = slotLabel,
            fontFamily = com.huffcart.app.ui.theme.PixelFontFamily,
            fontSize = 13.sp,
            color = HcOnLight,
            modifier = Modifier.width(64.dp),
        )
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 52.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HcPanelDark.copy(alpha = if (meta != null) 1f else 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(thumbnail.width.toFloat() / thumbnail.height.toFloat()),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Text(
                    text = if (meta != null) "无图" else "空",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = meta?.let { formatSlotTime(it.savedAt) } ?: "空",
            style = MaterialTheme.typography.labelMedium,
            color = if (meta != null) HcOnLight else HcOnLightVariant,
            modifier = Modifier.weight(1f),
        )
        // 无存档的槽没有可删对象，删除钮仅对有档槽呈现
        if (meta != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "删除$slotLabel",
                    tint = HcRed,
                )
            }
        }
    }
}

private fun formatSlotTime(savedAt: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(savedAt))
