package com.huffcart.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.netplay.PickedGameUi
import com.huffcart.app.netplay.Seat
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.RomLibrary
import com.huffcart.app.ui.theme.HcRed
import kotlinx.coroutines.delay

/**
 * 房间屏（netplay-lan「加入与玩家列表」「游戏选择与开局同步」）：
 * 房主与加入端共用。玩家列表实时显示席位；房主从游戏库选游戏并开局，
 * 加入端等待并展示校验结果。解散/断线提示后自动退出房间。
 */
@Composable
fun RoomScreen(
    onBack: () -> Unit,
    onStartGame: (String) -> Unit,
) {
    val context = LocalContext.current
    val room = NetplayManager.room
    val roomCode = NetplayManager.roomCode

    // 退出幂等（探索反馈 1）：返回键会先把 room 置空，正在退场时其余
    // onBack 路径必须保持沉默，否则连弹两次把联机屏也弹掉、直接落回首页
    var leaving by remember { mutableStateOf(false) }
    fun exitRoom() {
        if (leaving) return
        leaving = true
        NetplayManager.leaveRoom(context)
        onBack()
    }
    BackHandler { exitRoom() }

    if (room == null) {
        // 房间被外部清空（如进程重建后状态丢失）：仅在非主动退出时回退一次
        if (!leaving) {
            LaunchedEffect(Unit) { onBack() }
        }
        return
    }

    // 解散/断线提示 → 稍候自动退出
    LaunchedEffect(room.ended) {
        if (room.ended != null) {
            delay(1500)
            exitRoom()
        }
    }
    // 加入端：房主已开局（收到快照）→ 跟随进入游戏屏
    LaunchedEffect(room.startRequested) {
        if (room.startRequested && !room.isHost) {
            room.pickedGame?.let { onStartGame(it.romName) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = if (room.isHost) "我的房间" else "联机房间",
            onBack = { exitRoom() },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 玩家列表 ----
            Text("玩家", style = MaterialTheme.typography.titleMedium)
            room.players.forEach { player ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x14000000), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(HcRed, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(player.seat.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(player.nickname, style = MaterialTheme.typography.bodyLarge)
                    if (player.seat == Seat.P1) {
                        Spacer(Modifier.weight(1f))
                        Text("房主", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    }
                }
            }
            if (!room.full) {
                Text(
                    if (room.isHost && roomCode != null) {
                        "等待小伙伴加入…（同一 Wi-Fi 打开「联机」即可看到房间；也可让对方输入房间码 $roomCode）"
                    } else {
                        "等待小伙伴加入…（同一 Wi-Fi 下打开「联机」即可看到房间；房主开热点也行）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }

            room.notice?.let {
                Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }

            room.ended?.let {
                Text(it, color = HcRed, style = MaterialTheme.typography.bodyMedium)
            }

            // ---- 游戏与开局 ----
            if (room.isHost) {
                Text("选择游戏", style = MaterialTheme.typography.titleMedium)
                val allRoms = remember { RomLibrary.listRoms(context) }
                var search by remember { mutableStateOf("") }
                // 搜索过滤（探索反馈 2）：内置 ROM 数量大，按名称包含匹配就地过滤
                val roms = remember(search) {
                    if (search.isBlank()) {
                        allRoms
                    } else {
                        allRoms.filter { it.nameWithoutExtension.contains(search.trim(), ignoreCase = true) }
                    }
                }
                var selected by remember(room.pickedGame?.romName) {
                    mutableStateOf(room.pickedGame?.romName)
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("搜索游戏") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (roms.isEmpty()) {
                    Text("没有匹配「${search.trim()}」的游戏", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(roms, key = { it.name }) { file ->
                        val isPicked = selected == file.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isPicked) HcRed.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .border(
                                    1.dp,
                                    if (isPicked) HcRed else Color(0x22000000),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    selected = file.name
                                    NetplayManager.pickGame(context, file)
                                }
                                .padding(14.dp),
                        ) {
                            Text(file.name.removeSuffix(".nes"), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                room.pickedGame?.let { picked: PickedGameUi ->
                    when {
                        room.joinerReady -> Text("双方校验通过，可以开始！", color = Color(0xFF2E7D32))
                        room.pickMessage != null -> Text(room.pickMessage!!, color = HcRed)
                        picked.crc32 != 0L -> Text("等待对方校验游戏…", color = Color.Gray)
                        else -> Text("正在计算游戏校验…", color = Color.Gray)
                    }
                }
                Button(
                    onClick = {
                        NetplayManager.prepareHostStart()?.let { onStartGame(it.romName) }
                    },
                    enabled = room.joinerReady && room.pickedGame != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HcRed),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("开始联机", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                val picked = room.pickedGame
                when {
                    picked == null -> Text("等待房主选择游戏…", color = Color.Gray)
                    room.startRequested -> Text("房主已开始，正在进入游戏…", color = Color.Gray)
                    else -> {
                        Text("房主选择了：${picked.romName.removeSuffix(".nes")}")
                        room.pickMessage?.let { Text(it, color = HcRed) }
                    }
                }
                Text(
                    "开局后画面会与房主完全同步，你的操作通过 P2 手柄生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}
