package com.huffcart.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.netplay.PlayerInfo
import com.huffcart.app.netplay.Seat
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.CoverImage
import com.huffcart.app.ui.theme.HcRed
import kotlinx.coroutines.delay

/**
 * 组队房间屏（netplay-lobby-v2「加入与玩家列表」「游戏选择与开局同步」）：
 * 信息行（房间码/状态/人数）+ 游戏卡片（建房时已选）+ 席位头像（P1–P4、
 * 空位）+ 准备/开始。游戏于建房时选定，入座即校验 ROM；解散/断线提示后
 * 自动退出房间。
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

    val capacity = if (room.capacity in 2..4) room.capacity else 2
    val statusText = when {
        room.ended != null -> room.ended!!
        room.startRequested -> "开局中"
        room.isHost && room.joinerReady -> "等待开局"
        else -> "等待玩家"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            title = if (room.isHost) "我的房间" else "联机房间",
            onBack = { exitRoom() },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 信息行：房间码 / 状态 / 人数 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x14000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                InfoCell("房间码", if (room.isHost) (roomCode ?: "—") else "—", Modifier.weight(1f))
                InfoCell("状态", statusText, Modifier.weight(1f))
                InfoCell("人数", "${room.playerCount}/$capacity", Modifier.weight(1f))
            }

            room.notice?.let {
                Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            room.ended?.let {
                Text(it, color = HcRed, style = MaterialTheme.typography.bodyMedium)
            }

            // ---- 游戏卡片（建房时已选） ----
            Text("游戏", style = MaterialTheme.typography.titleMedium)
            room.pickedGame?.let { picked ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x14000000), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverImage(
                        gameName = picked.romName.removeSuffix(".nes"),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            picked.romName.removeSuffix(".nes"),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .border(1.dp, Color(0x33000000), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text("FC", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ---- 玩家席位（P1–P4，空位虚位显示） ----
            Text("玩家", style = MaterialTheme.typography.titleMedium)
            val seated = room.players.associateBy { it.seat }
            Seat.entries.take(capacity).forEach { seat ->
                SeatRow(
                    seat = seat,
                    player = seated[seat],
                    isHostSeat = seat == Seat.P1,
                )
            }

            // ---- 底部：准备状态 / 开局 ----
            Spacer(Modifier.height(4.dp))
            if (room.isHost) {
                when {
                    room.ended != null -> Unit
                    room.players.size < 2 -> Text(
                        "等待小伙伴加入…（同一 Wi-Fi 打开「联机」即可看到房间；也可让对方输入房间码 $roomCode）",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                    else -> Text(
                        if (room.joinerReady) "全员就绪，可以开始！" else "等待加入端校验游戏…",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (room.joinerReady) Color(0xFF2E7D32) else Color.Gray,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        NetplayManager.prepareHostStart()?.let { onStartGame(it.romName) }
                    },
                    enabled = room.joinerReady && room.pickedGame != null && room.players.size >= 2,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HcRed),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("开始联机", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HcRed.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .border(1.dp, HcRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            room.startRequested -> "房主已开始，正在进入游戏…"
                            room.ended != null -> "房间已解散"
                            else -> "已准备，等待房主开始"
                        },
                        color = HcRed,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

/** 信息行单元格（房间码/状态/人数）。 */
@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 席位行（netplay-lobby-v2）：席位头像 + 昵称 + 房主/已准备标识；空位虚位显示。 */
@Composable
private fun SeatRow(seat: Seat, player: PlayerInfo?, isHostSeat: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (player != null) HcRed.copy(alpha = 0.15f) else Color(0x0D000000),
                    CircleShape,
                )
                .border(
                    2.dp,
                    if (player != null) HcRed else Color(0x33000000),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                seat.label,
                color = if (player != null) HcRed else Color(0x66000000),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.width(14.dp))
        if (player != null) {
            Column(modifier = Modifier.weight(1f)) {
                Text(player.nickname, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    if (isHostSeat) "房主 · 已准备" else "已准备",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                )
            }
        } else {
            Text(
                "等待加入",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0x66000000),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
