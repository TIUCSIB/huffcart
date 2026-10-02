package com.huffcart.app.netplay

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 房主端单席位会话（design 决策 1/2）：一个加入端占用一个席位（P2–P4 由
 * NetplayManager 按空位分配），多席位 = 多个本会话实例并行。阶段：
 * LOBBY（接纳、握手、入座校验）→ STARTING（发出 Start+快照，等 GAME_READY）
 * → PLAYING（逐帧输入流）。
 *
 * 心跳（netplay-lobby-v2「房间长驻保活」）：大厅阶段房主每 10s 发 [Ping]，
 * 加入端回 [Pong]——加入端的读循环靠它维持（房主侧本就有加入端 2s 级
 * CLIENT_INPUT 喂着）；对局阶段停发（逐帧输入流即保活）。
 */
class HostSession(
    private val endpoint: NetplayEndpoint,
    private val hostNickname: String,
    /** 本会话加入端被分配的席位（P2–P4）。 */
    val joinerSeat: Seat,
    /** 房间容量（Welcome 携带；构造期传入避免 Hello 异步竞态）。 */
    private val capacity: Int,
) {

    var onJoinerJoined: ((PlayerInfo) -> Unit)? = null
    var onPickResult: ((ok: Boolean, reason: String) -> Unit)? = null
    var onGameReady: (() -> Unit)? = null
    var onJoinerLeft: ((reason: String) -> Unit)? = null

    /** 链路断开且无人入座（连接后未发 HELLO 即死）时也须通知：
     *  否则 stale 会话永久占位，房间拒绝一切后续加入（真机联调发现的 bug）。 */
    var onSessionClosed: (() -> Unit)? = null

    @Volatile
    var joiner: PlayerInfo? = null
        private set

    @Volatile
    var phase: Phase = Phase.LOBBY
        private set

    enum class Phase { LOBBY, STARTING, PLAYING }

    @Volatile
    private var closed = false

    private val remoteMask = AtomicInteger(0)
    private var gameReadyLatch = CountDownLatch(0)

    init {
        endpoint.onMessage(::route)
        endpoint.onDisconnect { reason ->
            closed = true
            val left = joiner
            joiner = null
            if (left != null) onJoinerLeft?.invoke(reason)
            onSessionClosed?.invoke()
        }
        Thread({
            while (!closed && phase != Phase.PLAYING) {
                try {
                    // 先睡后发：握手期本身有 Welcome/PlayerList/PickGame 流量，
                    // 首个心跳延后 10s 即可维持加入端 30s 读超时
                    Thread.sleep(10_000)
                } catch (_: InterruptedException) {
                    break
                }
                if (closed || phase == Phase.PLAYING) break
                endpoint.send(NetplayMessage.Ping)
            }
        }, "netplay-host-heartbeat").apply { isDaemon = true; start() }
    }

    private fun route(msg: NetplayMessage) {
        when (msg) {
            is NetplayMessage.Hello -> {
                if (msg.protocolVersion != NETPLAY_PROTOCOL_VERSION) {
                    endpoint.send(NetplayMessage.Reject(RejectReason.VERSION_MISMATCH))
                    endpoint.close()
                    return
                }
                if (joiner != null) return // 满员拒绝由上层在接纳前处理
                joiner = PlayerInfo(joinerSeat, msg.nickname)
                endpoint.send(NetplayMessage.Welcome(hostNickname, joinerSeat, capacity))
                onJoinerJoined?.invoke(joiner!!)
            }
            is NetplayMessage.Ready -> onPickResult?.invoke(msg.ok, msg.reason)
            is NetplayMessage.GameReady -> {
                if (phase == Phase.STARTING) {
                    phase = Phase.PLAYING
                    onGameReady?.invoke()
                    gameReadyLatch.countDown()
                }
            }
            is NetplayMessage.Leave -> {
                endpoint.close()
                val left = joiner
                joiner = null
                if (left != null) onJoinerLeft?.invoke("对方退出了房间")
            }
            is NetplayMessage.ClientInput -> remoteMask.set(msg.mask)
            is NetplayMessage.Ping -> endpoint.send(NetplayMessage.Pong)
            else -> Unit
        }
    }

    // ---- 房间动作（UI 线程） ----

    /** 向本席位加入端广播最新成员列表（席位/昵称）。 */
    fun sendPlayers(players: List<PlayerInfo>) {
        endpoint.send(NetplayMessage.PlayerList(players))
    }

    fun pickGame(romName: String, size: Long, crc32: Long) {
        endpoint.send(NetplayMessage.PickGame(romName, size, crc32))
    }

    /** 发出开局快照并等待加入端游戏就绪；[awaitGameReady] 供游戏线程阻塞等待。 */
    fun startGame(state: ByteArray) {
        phase = Phase.STARTING
        gameReadyLatch = CountDownLatch(1)
        endpoint.send(NetplayMessage.Start(state))
    }

    fun awaitGameReady(timeoutMs: Long): Boolean {
        if (phase == Phase.PLAYING) return true
        val ok = gameReadyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return ok && phase == Phase.PLAYING
    }

    fun leave() {
        closed = true
        endpoint.send(NetplayMessage.Leave)
        endpoint.close()
    }

    fun close() {
        closed = true
        endpoint.close()
    }

    // ---- 对局动作（游戏线程） ----

    fun sendInputFrame(frame: Int, p1: Int, p2: Int, p3: Int, p4: Int, videoCrc32: Long?) {
        endpoint.send(NetplayMessage.Input(frame, p1, p2, p3, p4, videoCrc32))
    }

    /** 帧首取用本席位加入端最新输入掩码（最新值槽，design 决策 5）。 */
    fun latestRemoteMask(): Int = remoteMask.get()
}
