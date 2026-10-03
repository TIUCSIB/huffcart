package com.huffcart.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.netplay.PickedGameUi
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.CoverImage
import com.huffcart.app.ui.game.RomPlatform
import com.huffcart.app.ui.library.RomLibrary
import com.huffcart.app.ui.theme.HcRed
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * 创建房间页（netplay-lobby-v2）：房间名称、联机人数（2/3/4）、游戏选择区
 * （搜索 + 定高列表 + 可视滚动条，不用无限滚）、席位预览与「开放房间」固定
 * 底部常驻——选完游戏一键开放。
 */
@Composable
fun CreateRoomScreen(
    onBack: () -> Unit,
    onOpenRoom: () -> Unit,
) {
    val context = LocalContext.current
    val room = NetplayManager.room

    BackHandler { onBack() }
    // 已开放：房间就绪即随导航进房间屏（经 LaunchedEffect 导航，避免组合期副作用）
    LaunchedEffect(room?.isHost) {
        if (room?.isHost == true) onOpenRoom()
    }

    var roomName by remember { mutableStateOf("联机房间") }
    var capacity by remember { mutableStateOf(2) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<File?>(null) }
    var gameInfo by remember { mutableStateOf<PickedGameUi?>(null) }
    // 联机游戏仅支持 FC(gb-gbc-platform):联机输入同步按 FC 席位协议设计
    val allRoms = remember {
        RomLibrary.listRoms(context).filter { RomPlatform.fromExtension(it.name) == RomPlatform.FC }
    }
    val roms = remember(search) {
        if (search.isBlank()) {
            allRoms
        } else {
            allRoms.filter { it.nameWithoutExtension.contains(search.trim(), ignoreCase = true) }
        }
    }
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "创建房间", onBack = onBack)

        // ---- 上半：名称 / 人数 / 游戏列表（定高滚动 + 滚动条） ----
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it.take(12) },
                label = { Text("房间名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("联机人数", style = MaterialTheme.typography.titleSmall)
                listOf(2, 3, 4).forEach { n ->
                    FilterChip(
                        selected = capacity == n,
                        onClick = { capacity = n },
                        label = { Text("$n 人") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HcRed.copy(alpha = 0.15f),
                            selectedLabelColor = HcRed,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("搜索游戏") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0x0D000000), RoundedCornerShape(12.dp))
                    .gameListScrollbar(listState),
            ) {
                if (roms.isEmpty()) {
                    Text(
                        if (search.isBlank()) "游戏库为空：先回首页导入 ROM" else "没有匹配「${search.trim()}」的游戏",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(roms, key = { it.name }) { file ->
                            val isPicked = selected?.name == file.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = file
                                        // CRC 在 IO 线程算，完成前按钮置灰
                                        gameInfo = PickedGameUi(file.name, 0, 0)
                                        thread {
                                            val info = NetplayManager.romFileInfo(file)
                                            gameInfo = PickedGameUi(file.name, info.size, info.crc32)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CoverImage(
                                    gameName = file.nameWithoutExtension,
                                    modifier = Modifier.size(44.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    file.name.removeSuffix(".nes"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isPicked) {
                                    Icon(Icons.Filled.Check, contentDescription = "已选", tint = HcRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 底部固定：席位预览 + 已选状态 + 开放房间 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SeatPreview(capacity)
                Spacer(Modifier.weight(1f))
                val game = gameInfo
                Text(
                    when {
                        game == null -> "未选游戏"
                        game.crc32 == 0L -> "校验计算中…"
                        else -> "已选：${game.romName.removeSuffix(".nes")}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        game == null -> Color.Gray
                        game.crc32 == 0L -> Color.Gray
                        else -> Color(0xFF2E7D32)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(2f),
                )
            }
            Button(
                onClick = {
                    val game = gameInfo ?: return@Button
                    NetplayManager.hostRoom(
                        context,
                        nickname = NetplayManager.loadNickname(context).trim().ifEmpty { "玩家" },
                        roomName = roomName.trim().ifEmpty { "联机房间" },
                        capacity = capacity,
                        game = game,
                    )
                },
                enabled = gameInfo != null && gameInfo!!.crc32 != 0L,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HcRed),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("开放房间", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** 席位预览（我 + 空位，紧凑单行；数量跟随所选联机人数）。 */
@Composable
private fun SeatPreview(capacity: Int) {
    Row(
        modifier = Modifier
            .background(Color(0x0D000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(capacity) { index ->
            val isMe = index == 0
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (isMe) HcRed.copy(alpha = 0.15f) else Color(0x0D000000),
                        CircleShape,
                    )
                    .border(
                        2.dp,
                        if (isMe) HcRed else Color(0x33000000),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isMe) "我" else "P${index + 1}",
                    color = if (isMe) HcRed else Color(0x66000000),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** 游戏列表滚动条：按可见/总条目比例绘制右侧滑块（仅当需要滚动时显示）。 */
private fun Modifier.gameListScrollbar(state: androidx.compose.foundation.lazy.LazyListState): Modifier =
    drawWithContent label@{
        drawContent()
        val total = state.layoutInfo.totalItemsCount
        val visible = state.layoutInfo.visibleItemsInfo.size
        if (total <= 0 || visible >= total) return@label
        val viewport = size.height
        val thumbH = max(viewport * visible / total, 48.dp.toPx())
        val first = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        val scrollable = viewport - thumbH
        val y = scrollable * first / (total - visible)
        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(size.width - 8.dp.toPx(), y),
            size = Size(4.dp.toPx(), thumbH),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
