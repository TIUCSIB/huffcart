package com.huffcart.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.CoverImage
import com.huffcart.app.ui.theme.HcRed

/**
 * 联机大厅（netplay-lobby-v2「联机入口」）：局域网状态条、搜索过滤、附近房间
 * 卡片（封面/房间名/房主/人数÷容量）、「创建房间」进入建房页。搜索框兼作房间码
 * 输入（恰 3 位数字时出行动条），无独立表单；昵称固定取设备名。顶层 tab 无返回箭头。
 */
@Composable
fun NetplayScreen(
    onOpenRoom: () -> Unit,
    onCreateRoom: () -> Unit,
) {
    val context = LocalContext.current

    var search by remember { mutableStateOf("") }
    // 搜索框兼作房间码输入（netplay-lobby-v2 简化）：恰好 3 位数字 → 房间码模式
    val roomCodeInput = search.trim().takeIf { it.length == 3 && it.all { c -> c.isDigit() } }
    val resolvedIp = roomCodeInput?.let { NetplayManager.resolveRoomCode(it) }
    val wifiOk = NetplayManager.wifiAvailable
    val localIp = NetplayManager.localIp
    val joining = NetplayManager.joining
    val error = NetplayManager.lobbyError
    val rooms = NetplayManager.discoveredRooms
    val room = NetplayManager.room

    DisposableEffect(Unit) {
        NetplayManager.refreshLobby(context)
        onDispose { NetplayManager.stopLobby(context) }
    }
    LaunchedEffect(room) { if (room != null) onOpenRoom() }

    // 搜索过滤：房间名 / 房主昵称 / 游戏名（不区分大小写包含）；房间码模式下不滤
    val filteredRooms = remember(rooms, search, roomCodeInput) {
        val kw = search.trim()
        if (kw.isEmpty() || roomCodeInput != null) {
            rooms
        } else {
            rooms.filter {
                it.serviceName.contains(kw, ignoreCase = true) ||
                    it.hostNickname.contains(kw, ignoreCase = true) ||
                    it.gameName.contains(kw, ignoreCase = true)
            }
        }
    }

    fun confirmedNickname() = NetplayManager.loadNickname(context).trim().ifEmpty { "玩家" }

    Scaffold(
        topBar = { AppTopBar(title = "联机") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    NetplayManager.saveNickname(context, confirmedNickname())
                    onCreateRoom()
                },
                containerColor = HcRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("创建房间", style = MaterialTheme.typography.titleSmall)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- 局域网状态条 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x14000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (wifiOk) Color(0xFF2E7D32) else Color.Gray,
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (wifiOk) "局域网 · 在线" else "局域网 · 离线",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (wifiOk) Color(0xFF2E7D32) else Color.Gray,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "本机 IP: ${localIp ?: "未知"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                )
            }

            if (!wifiOk) {
                Text(
                    "未连接 Wi-Fi：联机需要与小伙伴在同一网络（房主也可开热点由你连接）",
                    color = HcRed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (joining) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在连接房主…")
                }
            }
            error?.let {
                Text(text = it, color = HcRed, style = MaterialTheme.typography.bodyMedium)
            }

            // ---- 附近房间 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("附近的房间", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = {
                    NetplayManager.refreshLobby(context)
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = HcRed)
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("搜索房间、游戏或 3 位房间码…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // ---- 房间码模式：搜索框恰为 3 位数字时给出行动条，取代独立输入区 ----
            roomCodeInput?.let { code ->
                val ok = resolvedIp != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (ok) HcRed.copy(alpha = 0.12f) else Color(0x14000000),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = ok && !joining) {
                            NetplayManager.saveNickname(context, confirmedNickname())
                            NetplayManager.joinByCode(context, code, confirmedNickname())
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "房间码 $code",
                        color = if (ok) HcRed else Color.Gray,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (ok) "将连接房主 $resolvedIp，点击加入"
                        else "无法解析：确认对方房间屏上的 3 位数字且本机已连同一 Wi-Fi",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) Color(0xFF2E7D32) else HcRed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            when {
                !wifiOk -> Text(
                    "连接 Wi-Fi 后自动搜索",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                filteredRooms.isEmpty() && roomCodeInput == null -> Text(
                    if (search.isBlank()) "正在搜索附近房间…（也可在上方输入 3 位房间码加入）"
                    else "没有匹配「${search.trim()}」的房间",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                else -> filteredRooms.forEach { found ->
                    RoomCard(
                        title = found.serviceName,
                        gameName = found.gameName,
                        hostNickname = found.hostNickname,
                        count = found.playerCount,
                        capacity = found.capacity,
                        onClick = {
                            NetplayManager.saveNickname(context, confirmedNickname())
                            NetplayManager.joinRoom(context, found.host, found.port, confirmedNickname())
                        },
                    )
                }
            }

        }
    }
}

/** 附近房间卡片（netplay-lobby-v2）：游戏封面 + 房间名 + 房主 + 人数÷容量。 */
@Composable
private fun RoomCard(
    title: String,
    gameName: String,
    hostNickname: String,
    count: Int,
    capacity: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x14000000), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            gameName = gameName.removeSuffix(".nes"),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (gameName.isNotEmpty()) {
                Text(
                    gameName.removeSuffix(".nes"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                )
            }
            if (hostNickname.isNotEmpty()) {
                Text(
                    "房主：$hostNickname",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(HcRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                "等待玩家 $count/${if (capacity in 2..4) capacity else "?"}",
                color = HcRed,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
