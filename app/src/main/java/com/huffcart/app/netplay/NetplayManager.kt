package com.huffcart.app.netplay

import android.content.Context
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.CRC32

/** 房间屏可观察状态。 */
data class RoomUi(
    val isHost: Boolean,
    val players: List<PlayerInfo>,
    /** 建房所选容量（2–4）；加入端经 Welcome 获知。 */
    val capacity: Int,
    val pickedGame: PickedGameUi?,
    val pickMessage: String?,
    /** 全部加入端均已入座并通过 ROM 校验（房主可开局）。 */
    val joinerReady: Boolean,
    val startRequested: Boolean,
    /** 解散级事件：触发房间屏自动退出（仅加入端在房主离开时出现） */
    val ended: String?,
    /** 非致命提示（如"对方已退出"）：仅展示，房间保持开放 */
    val notice: String?,
) {
    val full: Boolean get() = capacity in 2..4 && players.size >= capacity
    val playerCount: Int get() = players.size
}

data class PickedGameUi(
    val romName: String,
    val size: Long,
    val crc32: Long,
)

/** 交给游戏屏的联机装配（consume 一次）。 */
class NetplayGameSetup(
    val role: Seat,
    val romName: String,
    /** 对局全体成员（含自己），横幅展示成员昵称/席位。 */
    val members: List<PlayerInfo>,
    val joinerState: ByteArray?,
    val joinSession: JoinSession?,
)

/** 对局中事件：GameScreen 注册监听（pill 提示 / 退出对局）。 */
sealed interface NetplayGameEvent {
    data class Message(val text: String) : NetplayGameEvent
    data class Ended(val exitGame: Boolean) : NetplayGameEvent
}

/**
 * 联机流程的总装（进程级单例）：大厅（发现/加入）、建房（名称/容量/游戏）、
 * 房间（多席位 2–4 人）、对局交接。状态经 Compose mutableStateOf 暴露给
 * 联机大厅与房间屏；网络回调一律 post 回主线程后再改状态。
 */
object NetplayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialized = false
    private var appVersion: String = "?"

    // ---- 大厅（联机屏） ----

    var wifiAvailable by mutableStateOf(true)
        private set
    var joining by mutableStateOf(false)
        private set
    var lobbyError by mutableStateOf<String?>(null)
        private set
    var discoveredRooms by mutableStateOf<List<DiscoveredRoom>>(emptyList())
        private set

    /** 本机局域网 IPv4（状态条展示）；refreshLobby 时刷新。 */
    var localIp by mutableStateOf<String?>(null)
        private set

    // ---- 房间（房间屏） ----

    var room by mutableStateOf<RoomUi?>(null)
        private set

    /** 房主端房间码（IP 末段三位数字）；仅房主创建后非空。 */
    var roomCode by mutableStateOf<String?>(null)
        private set

    // ---- 对局交接 ----

    var gameListener: ((NetplayGameEvent) -> Unit)? = null

    private var pendingGame: NetplayGameSetup? = null
    private val gameActive = AtomicBoolean(false)

    private var server: NetplayServer? = null
    private var discovery: NsdRoomDiscovery? = null
    private var announcer: UdpRoomBeacon.Announcer? = null
    private var beaconListener: UdpRoomBeacon.Listener? = null

    /** 房主端多席位会话（P2–P4 各一个加入端）；对局/房间阶段共用。 */
    private val hostSessions = ConcurrentHashMap<Seat, HostSession>()
    private var joinSession: JoinSession? = null
    private var hostNickname: String = ""

    /** 加入端记录的房主地址（房间码展示用：与房主本机算出的码同源）。 */
    private var joinedHostAddress: String? = null

    /** 已通过 ROM 校验的加入端席位（房主端状态）。 */
    private val verifiedSeats = HashSet<Seat>()

    // ---- 昵称 ----

    fun loadNickname(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NICKNAME, null) ?: Build.MODEL ?: "玩家"
    }

    fun saveNickname(context: Context, nickname: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NICKNAME, nickname).apply()
    }

    // ---- 大厅动作 ----

    /** 联机屏进入时刷新：Wi-Fi 可用性、本机 IP 与附近房间浏览（NSD + UDP 双路）。 */
    fun refreshLobby(context: Context) {
        ensureInit(context)
        wifiAvailable = isOnWifi(context)
        localIp = localIpv4()
        if (wifiAvailable) {
            discovery?.startBrowsing(
                onFound = { found ->
                    mainHandler.post { mergeDiscovered(found) }
                },
                onLost = { name ->
                    mainHandler.post { removeDiscoveredByServiceName(name) }
                },
            )
            beaconListener?.stop()
            beaconListener = UdpRoomBeacon.Listener(
                onFound = { found -> mainHandler.post { mergeDiscovered(found) } },
                onClosed = { host ->
                    mainHandler.post { removeDiscoveredByHost(host) }
                },
            ).also { it.start(context) }
            // 周期清扫（真机反馈：清扫搭在"收到新广播"上会因停播而永不执行，
            // 退出的房间残留列表）——必须独立于入站流量运行
            mainHandler.removeCallbacks(pruneDiscoveredRunnable)
            mainHandler.postDelayed(pruneDiscoveredRunnable, 1_000)
        } else {
            discovery?.stopBrowsing()
            beaconListener?.stop()
            beaconListener = null
            mainHandler.removeCallbacks(pruneDiscoveredRunnable)
            discoveredMap.clear()
            discoveredRooms = emptyList()
        }
    }

    // ---- 发现条目：统一 5s TTL ----
    // 房主同时开 NSD + UDP 广播（广播每 1.5s 刷新所有条目的 lastSeen），
    // 退出/进程冻结后条目 5s 内从所有设备消失，不依赖经常丢失的 mDNS lost 事件。
    private class DiscoveredEntry(val room: DiscoveredRoom, var lastSeenMs: Long)

    private val discoveredMap = LinkedHashMap<String, DiscoveredEntry>() // key: "host:port"
    private val discoveryTtlMs: Long = 4_000

    // 自续期循环：清扫必须独立于入站流量持续运行（一次性 postDelayed 是
    // 上一版"条目永不消失"的直接根因）
    private val pruneDiscoveredRunnable = object : Runnable {
        override fun run() {
            pruneDiscovered()
            mainHandler.postDelayed(this, 1_000)
        }
    }

    private fun mergeDiscovered(found: DiscoveredRoom) {
        discoveredMap["${found.host}:${found.port}"] =
            DiscoveredEntry(found, android.os.SystemClock.elapsedRealtime())
        publishDiscovered()
    }

    private fun removeDiscoveredByServiceName(serviceName: String) {
        discoveredMap.entries.removeAll { it.value.room.serviceName == serviceName }
        publishDiscovered()
    }

    /** 收到房主的关闭通告：移除该房主的全部条目（正常退出 = 即时消失）。 */
    private fun removeDiscoveredByHost(host: String) {
        discoveredMap.entries.removeAll { it.value.room.host == host }
        publishDiscovered()
    }

    private fun pruneDiscovered() {
        val now = android.os.SystemClock.elapsedRealtime()
        val before = discoveredMap.size
        discoveredMap.entries.removeAll { now - it.value.lastSeenMs > discoveryTtlMs }
        if (discoveredMap.size != before) publishDiscovered()
    }

    private fun publishDiscovered() {
        discoveredRooms = discoveredMap.values.map { it.room }.sortedBy { it.serviceName }
    }

    fun stopLobby(context: Context) {
        discovery?.stopBrowsing()
        beaconListener?.stop()
        beaconListener = null
        mainHandler.removeCallbacks(pruneDiscoveredRunnable)
        discoveredMap.clear()
        discoveredRooms = emptyList()
    }

    // ---- 建房（创建房间页 → 开放房间） ----

    /** 开放房间：房主 P1 入座，广播房间名/容量/房主/人数/所选游戏。 */
    fun hostRoom(
        context: Context,
        nickname: String,
        roomName: String,
        capacity: Int,
        game: PickedGameUi,
    ) {
        ensureInit(context)
        resetSessionState()
        lobbyError = null
        hostNickname = nickname
        verifiedSeats.clear()
        try {
            val srv = NetplayServer { link -> onIncoming(link) }
            srv.start()
            server = srv
            // 广播 meta：房主昵称|容量|游戏名（人数动态取自 room 状态）
            val meta = UdpRoomBeacon.RoomMeta(nickname, capacity, game.romName)
            val roomDisplayName = roomName.trim().ifEmpty { "联机房间" }
            discovery?.registerRoom(roomDisplayName, srv.port, "$nickname|$capacity|${game.romName}")
            // UDP 广播发现（与 NSD 并行）：mdnssd 在模拟器/部分机型上不可靠
            announcer = UdpRoomBeacon.Announcer(roomDisplayName, srv.port, meta) {
                room?.players?.size ?: 1
            }.also { it.start() }
            roomCode = RoomCode.fromIp(localIpv4() ?: "")
            room = RoomUi(
                isHost = true,
                players = listOf(PlayerInfo(Seat.P1, nickname)),
                capacity = capacity,
                pickedGame = game,
                pickMessage = null,
                joinerReady = false,
                startRequested = false,
                ended = null,
                notice = null,
            )
        } catch (e: Exception) {
            lobbyError = "创建房间失败：${e.message}"
            server?.stop()
            server = null
            announcer?.stop()
            announcer = null
        }
    }

    fun joinRoom(context: Context, host: String, port: Int, nickname: String) {
        ensureInit(context)
        resetSessionState()
        lobbyError = null
        joinedHostAddress = host
        joining = true
        Thread {
            try {
                val link = NetplayServer.connect(host, port)
                mainHandler.post {
                    joining = false
                    setupJoinSession(link, nickname)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    joining = false
                    lobbyError = "无法连接房主（$host）：${e.message}"
                }
            }
        }.start()
    }

    /** 房间码 → 房主 IP（用加入端自身网段前缀还原）；本机无局域网地址或码非法返回 null。 */
    fun resolveRoomCode(code: String): String? {
        val local = localIpv4() ?: return null
        return RoomCode.resolve(code, local)
    }

    /** 按房间码加入（探索反馈 3）：解析失败给出明确提示，不发起连接。 */
    fun joinByCode(context: Context, code: String, nickname: String) {
        val ip = resolveRoomCode(code)
        if (ip == null) {
            lobbyError = "房间码无效：请输入房主房间屏上的 3 位数字，并确认已连接同一 Wi-Fi"
            return
        }
        joinRoom(context, ip, NETPLAY_DEFAULT_PORT, nickname)
    }

    private fun setupJoinSession(link: NetplayLink, nickname: String) {
        val context = appContext ?: return
        val session = JoinSession(
            endpoint = link,
            appVersion = appVersion,
            nickname = nickname,
            romLookup = { name -> lookupRom(context, name) },
        )
        session.onWelcome = { hostNick, seat, capacity ->
            mainHandler.post {
                hostNickname = hostNick
                roomCode = RoomCode.fromIp(joinedHostAddress ?: "")
                room = RoomUi(
                    isHost = false,
                    players = listOf(PlayerInfo(Seat.P1, hostNick), PlayerInfo(seat, nickname)),
                    capacity = capacity,
                    pickedGame = null,
                    pickMessage = null,
                    joinerReady = false,
                    startRequested = false,
                    ended = null,
                    notice = null,
                )
            }
        }
        session.onPlayers = { players ->
            mainHandler.post {
                room = room?.copy(players = players)
            }
        }
        session.onGamePicked = { romName, ok, reason ->
            mainHandler.post {
                room = room?.copy(
                    pickedGame = PickedGameUi(romName, 0, 0),
                    pickMessage = reason.ifEmpty { null },
                    joinerReady = ok,
                )
                if (!ok) {
                    // ROM 校验失败：展示提示后自动退房（无法入座，spec「ROM 不一致阻止开局」）
                    mainHandler.postDelayed({ if (room?.pickedGame?.romName == romName) leaveRoom(context) }, 2_500)
                }
            }
        }
        session.onStartReceived = { state ->
            mainHandler.post {
                val romName = room?.pickedGame?.romName ?: return@post
                pendingGame = NetplayGameSetup(
                    role = room?.players?.lastOrNull()?.seat ?: Seat.P2,
                    romName = romName,
                    members = room?.players ?: listOf(PlayerInfo(Seat.P1, hostNickname), PlayerInfo(Seat.P2, nickname)),
                    joinerState = state,
                    joinSession = session,
                )
                room = room?.copy(startRequested = true)
            }
        }
        session.onDesync = { frame ->
            mainHandler.post {
                if (gameActive.get()) {
                    gameListener?.invoke(NetplayGameEvent.Message("第 $frame 帧画面失同步"))
                }
            }
        }
        session.onDisconnected = { reason ->
            mainHandler.post { onPeerLeft(reason) }
        }
        joinSession = session
    }

    // ---- 房间动作 ----

    /** 房主点开始（全体加入端入座且校验通过）：装配对局，返回 null 表示不可开局。 */
    fun prepareHostStart(): NetplayGameSetup? {
        val r = room ?: return null
        val game = r.pickedGame ?: return null
        if (r.players.size < 2 || !r.joinerReady) return null
        val setup = NetplayGameSetup(
            role = Seat.P1,
            romName = game.romName,
            members = r.players,
            joinerState = null,
            joinSession = null,
        )
        pendingGame = setup
        return setup
    }

    /** 宿主 Activity 离开前台（onStop）：大厅房间立即关闭并停播。
     *  房间只在房主看得见房间屏时有意义；否则在其他设备上表现为
     *  "人已退出、房间还在"（MuMu 冻结进程前的那段窗口期）。对局不在此列。 */
    fun onHostActivityStopped() {
        if (room != null && !gameActive.get()) {
            leaveRoomInternal()
        }
    }

    private fun leaveRoomInternal() {
        discovery?.unregisterRoom()
        val selfPort = server?.port
        server?.stop()
        server = null
        announcer?.stop()
        announcer = null
        if (selfPort != null) {
            localIpv4()?.let { ip -> discoveredMap.remove("$ip:$selfPort") }
            discoveredRooms = discoveredMap.values.map { it.room }.sortedBy { it.serviceName }
        }
        hostSessions.values.forEach { it.leave() }
        hostSessions.clear()
        joinSession?.leave()
        joinSession = null
        room = null
        roomCode = null
        pendingGame = null
        verifiedSeats.clear()
        joinedHostAddress = null
        gameActive.set(false)
    }

    fun leaveRoom(context: Context) {
        // 自己房间的发现条目在 leaveRoomInternal 内立即清除（NSD unregister 的
        // onLost 异步且可能丢失，退出即刻消失才符合直觉）
        leaveRoomInternal()
    }

    // ---- 对局（GameScreen / GameSession 调用） ----

    fun consumePendingGame(): NetplayGameSetup? {
        val setup = pendingGame ?: return null
        pendingGame = null
        gameActive.set(true)
        return setup
    }

    /** 房主 GameSession 装载完毕（游戏线程）：向全体席位发快照开局。 */
    fun onHostSnapshotReady(state: ByteArray) {
        hostSessions.values.forEach { it.startGame(state) }
    }

    /** 房主 GameSession 等待全体加入端就绪（游戏线程，阻塞有界）。 */
    fun awaitJoinerGameReady(timeoutMs: Long): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (allJoinersPlaying()) return true
            Thread.sleep(50)
        }
        return allJoinersPlaying()
    }

    private fun allJoinersPlaying(): Boolean {
        val sessions = hostSessions.values.toList()
        return sessions.isNotEmpty() && sessions.all { it.phase == HostSession.Phase.PLAYING }
    }

    /** 加入端 GameSession 加载完快照（游戏线程）：放行房主开跑。 */
    fun onJoinerGameLoaded() {
        joinSession?.notifyGameLoaded()
    }

    /** 加入端上送本地席位掩码（游戏线程）。 */
    fun sendJoinerMask(mask: Int) {
        joinSession?.sendLocalMask(mask)
    }

    /** 加入端节拍：阻塞等待主机第 N 帧输入（游戏线程，阻塞有界）。 */
    fun awaitJoinerInput(timeoutMs: Long): NetplayMessage.Input? =
        joinSession?.awaitInput(timeoutMs)

    /** 房主广播输入帧（游戏线程）：全体席位同帧同掩码。 */
    fun sendHostInput(frame: Int, p1Mask: Int, p2Mask: Int, p3Mask: Int, p4Mask: Int, crc: Long?) {
        hostSessions.values.forEach {
            it.sendInputFrame(frame, p1Mask, p2Mask, p3Mask, p4Mask, crc)
        }
    }

    /** 房主帧首取各席位最新掩码（游戏线程；席位 → 掩码）。 */
    fun latestRemoteMasks(): Map<Seat, Int> =
        hostSessions.entries.associate { it.key to it.value.latestRemoteMask() }

    /** 房主链路是否仍在线（游戏循环每帧轮询断线）。 */
    fun isHostLinked(): Boolean = hostSessions.isNotEmpty()

    /** 线程安全的对局事件投递（游戏线程 → 主线程 UI）。 */
    fun postGameEvent(event: NetplayGameEvent) {
        mainHandler.post { gameListener?.invoke(event) }
    }

    /** 离开游戏屏：联机中则发 Leave 并拆除（单机无副作用）。 */
    fun onGameScreenLeft() {
        if (!gameActive.compareAndSet(true, false)) return
        hostSessions.values.forEach { it.leave() }
        hostSessions.clear()
        joinSession?.leave()
        joinSession = null
        discovery?.unregisterRoom()
        server?.stop()
        server = null
        announcer?.stop()
        announcer = null
        verifiedSeats.clear()
        room = null
        roomCode = null
        pendingGame = null
    }

    // ---- 房主端：多席位接纳与维护（主线程） ----

    private fun onIncoming(link: NetplayLink) {
        println("[NetplayManager] incoming connection")
        mainHandler.post {
            val r = room
            val seat = Seat.entries.firstOrNull { s ->
                s != Seat.P1 && hostSessions[s] == null && r?.players?.none { it.seat == s } == true
            }
            if (r == null || !r.isHost || r.full || seat == null) {
                link.send(NetplayMessage.Reject(RejectReason.ROOM_FULL))
                link.close()
                return@post
            }
            val session = HostSession(link, hostNickname, seat, r.capacity)
            session.onJoinerJoined = { joiner ->
                mainHandler.post {
                    room = room?.copy(players = mergePlayer(room?.players, joiner))
                    broadcastPlayers()
                    // 建房时已选好的游戏立即下发：加入端入座即校验 ROM
                    room?.pickedGame?.let { g ->
                        session.pickGame(g.romName, g.size, g.crc32)
                    }
                }
            }
            session.onPickResult = { ok, reason ->
                mainHandler.post { onSeatVerified(session, ok, reason) }
            }
            session.onJoinerLeft = { reason ->
                mainHandler.post { onJoinerSeatLeft(session, reason) }
            }
            session.onSessionClosed = {
                mainHandler.post {
                    if (hostSessions[session.joinerSeat] === session) {
                        hostSessions.remove(session.joinerSeat)
                        verifiedSeats.remove(session.joinerSeat)
                        val r = room
                        room = r?.copy(players = r.players.filter { it.seat != session.joinerSeat })
                        broadcastPlayers()
                    }
                }
            }
            hostSessions[seat] = session
        }
    }

    /** 席位合入（同席位重入以新昵称覆盖，理论不可达，防御性处理）。 */
    private fun mergePlayer(players: List<PlayerInfo>?, joiner: PlayerInfo): List<PlayerInfo> =
        (players.orEmpty().filter { it.seat != joiner.seat } + joiner).sortedBy { it.seat.ordinal }

    /** 加入端 ROM 校验结果：通过 → 席位就绪；失败 → 移出席位并提示双方。 */
    private fun onSeatVerified(session: HostSession, ok: Boolean, reason: String) {
        val seat = session.joinerSeat
        if (ok) {
            verifiedSeats.add(seat)
            room = room?.copy(joinerReady = recomputeJoinerReady())
        } else {
            session.close()
            hostSessions.remove(seat)
            verifiedSeats.remove(seat)
            val r = room
            room = r?.copy(
                players = r.players.filter { it.seat != seat },
                notice = "对方游戏不匹配，已移出：$reason",
            )
            broadcastPlayers()
        }
    }

    /** 加入端离开（房间/对局阶段入口在会话回调，spec「加入与玩家列表」「断线处理」）。 */
    private fun onJoinerSeatLeft(session: HostSession, reason: String) {
        val seat = session.joinerSeat
        hostSessions.remove(seat)
        verifiedSeats.remove(seat)
        session.close()
        if (gameActive.get()) {
            // 对局中任一加入者断开 → 房主回单机（spec「断线处理」）：全员拆联机层，
            // GameSession 轮询 isHostLinked 降级；其余加入端经 Ended 回大厅
            gameListener?.invoke(NetplayGameEvent.Message(reason))
            gameListener?.invoke(NetplayGameEvent.Ended(exitGame = false))
            hostSessions.values.forEach { it.close() }
            hostSessions.clear()
            verifiedSeats.clear()
            discovery?.unregisterRoom()
            server?.stop()
            server = null
            announcer?.stop()
            announcer = null
            room = null
            pendingGame = null
        } else {
            val r = room
            room = r?.copy(
                players = r.players.filter { it.seat != seat },
                joinerReady = recomputeJoinerReady(),
                notice = reason,
            )
            broadcastPlayers()
        }
    }

    private fun recomputeJoinerReady(): Boolean {
        val r = room ?: return false
        val joiners = r.players.filter { it.seat != Seat.P1 }
        return joiners.isNotEmpty() && joiners.all { it.seat in verifiedSeats }
    }

    /** 向全体加入端广播最新成员列表（席位/昵称）。 */
    private fun broadcastPlayers() {
        val players = room?.players ?: return
        hostSessions.values.forEach { it.sendPlayers(players) }
    }

    /** 对端离开/断线（仅加入端会话挂此回调；房主席位侧走 onJoinerSeatLeft）。 */
    private fun onPeerLeft(reason: String) {
        println("[NetplayManager] peer left: $reason")
        if (gameActive.get()) {
            // 加入端对局中断开：退出游戏屏回大厅（spec「断线处理」）
            gameListener?.invoke(NetplayGameEvent.Message(reason))
            gameListener?.invoke(NetplayGameEvent.Ended(exitGame = true))
            // 清掉 room 态：否则返回房间屏时 startRequested 仍为 true，会再次
            // 导航进游戏、在刚 deinit 完的共享核心库上开新会话 → native 崩溃
            room = null
            pendingGame = null
        } else {
            room = room?.copy(ended = reason)
        }
        joinSession?.close()
        joinSession = null
    }

    /** 房主侧：对局开始后把 onJoinerLeft 从“房间解散”切换为“游戏事件”已由
     *  onPeerLeft / onJoinerSeatLeft 的 gameActive 分支覆盖，无需额外接线。 */

    private fun ensureInit(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        discovery = NsdRoomDiscovery(context.applicationContext)
    }

    private fun resetSessionState() {
        joinedHostAddress = null
        hostSessions.values.forEach { it.close() }
        hostSessions.clear()
        verifiedSeats.clear()
        joinSession?.close()
        joinSession = null
        discovery?.unregisterRoom()
        server?.stop()
        server = null
        announcer?.stop()
        announcer = null
    }

    private fun lookupRom(context: Context, romName: String): RomFileInfo? {
        val file = File(context.filesDir, "roms").resolve(romName)
        if (!file.isFile) return null
        return RomFileInfo(file.length(), crc32Of(file))
    }

    /** 创建房间页选游戏时计算校验信息（调用方自管 IO 线程）。 */
    fun romFileInfo(file: File): RomFileInfo = RomFileInfo(file.length(), crc32Of(file))

    private fun crc32Of(file: File): Long {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    private fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** 本机局域网 IPv4（站点本地、非回环）；取不到返回 null。 */
    private fun localIpv4(): String? =
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress

    private var appContext: Context? = null

    private const val PREFS = "netplay"
    private const val KEY_NICKNAME = "nickname"
}
