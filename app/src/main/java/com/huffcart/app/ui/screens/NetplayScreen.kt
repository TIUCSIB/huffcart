package com.huffcart.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.huffcart.app.ui.theme.HcRed

/**
 * 联机屏（netplay-lan「联机入口」）：昵称、创建房间、附近房间列表（NSD 发现）、
 * 手输 IP 兜底。无局域网时给出明确提示（spec「房间创建与发现」）。
 */
@Composable
fun NetplayScreen(
    onBack: () -> Unit,
    onOpenRoom: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    var nickname by remember { mutableStateOf(NetplayManager.loadNickname(context)) }
    var manualCode by remember { mutableStateOf("") }
    // 房间码解析提示：合法码展示将连接的地址；无效码给红色提示
    val resolvedIp = if (manualCode.isBlank()) null else NetplayManager.resolveRoomCode(manualCode)
    val resolveHint = when {
        manualCode.isBlank() -> null
        resolvedIp != null -> "将连接房主 $resolvedIp"
        else -> "无法解析房间码：请输入房主房间屏上的 3 位数字，并确认已连接同一 Wi-Fi"
    }
    val wifiOk = NetplayManager.wifiAvailable
    val joining = NetplayManager.joining
    val error = NetplayManager.lobbyError
    val rooms = NetplayManager.discoveredRooms
    val room = NetplayManager.room

    DisposableEffect(Unit) {
        NetplayManager.refreshLobby(context)
        onDispose { NetplayManager.stopLobby(context) }
    }
    LaunchedEffect(room) { if (room != null) onOpenRoom() }

    fun confirmedNickname() = nickname.trim().ifEmpty { "玩家" }

    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "联机", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!wifiOk) {
                Text(
                    text = "未连接 Wi-Fi：联机需要与小伙伴在同一网络（房主也可开热点由你连接）",
                    color = HcRed,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("你的昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    NetplayManager.saveNickname(context, confirmedNickname())
                    NetplayManager.hostRoom(context, confirmedNickname())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HcRed),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("创建房间", style = MaterialTheme.typography.titleMedium)
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

            Text("附近的房间", style = MaterialTheme.typography.titleMedium)
            if (!wifiOk) {
                Text("连接 Wi-Fi 后自动搜索", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else if (rooms.isEmpty()) {
                Text(
                    "正在搜索附近房间…（搜不到时，请对方把房间屏上的 3 位房间码告诉你，在下方输入即可）",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            } else {
                rooms.forEach { found ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x14000000), RoundedCornerShape(12.dp))
                            .clickable {
                                NetplayManager.saveNickname(context, confirmedNickname())
                                NetplayManager.joinRoom(context, found.host, found.port, confirmedNickname())
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(found.serviceName, style = MaterialTheme.typography.bodyLarge)
                            Text("${found.host}:${found.port}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text("加入", color = HcRed, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Text("手动加入（搜不到房间时）", style = MaterialTheme.typography.titleMedium)
            // 房间码加入（探索反馈 3）：房主屏展示 3 位房间码（IP 末段），
            // 加入端输入码 → 自身网段前缀还原房主 IP；解析结果透明展示
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("房间码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        NetplayManager.saveNickname(context, confirmedNickname())
                        NetplayManager.joinByCode(context, manualCode, confirmedNickname())
                    },
                    enabled = manualCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = HcRed),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "加入")
                }
            }
            resolveHint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (resolvedIp != null) Color.Gray else HcRed,
                )
            }
        }
    }
}
