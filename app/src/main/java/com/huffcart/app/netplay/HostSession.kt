package com.huffcart.app.netplay

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 房主端会话（design 决策 1/2）：LOBBY（接纳、握手、选游戏）→
 * STARTING（发出 Start+快照，等加入端 GAME_READY）→ PLAYING（逐帧输入流）。
 * 房主是权威端，保持自己的音频节拍；加入端输入经最新值槽在帧首取用。
 */
class HostSession(
    private val endpoint: NetplayEndpoint,
    private val hostNickname: String,
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

    private val remoteP2Mask = AtomicInteger(0)
    private var gameReadyLatch = CountDownLatch(0)

    init {
        endpoint.onMessage(::route)
        endpoint.onDisconnect { reason ->
            val left = joiner
            joiner = null
            if (left != null) onJoinerLeft?.invoke(reason)
            onSessionClosed?.invoke()
        }
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
                joiner = PlayerInfo(Seat.P2, msg.nickname)
                endpoint.send(NetplayMessage.Welcome(hostNickname))
                endpoint.send(
                    NetplayMessage.PlayerList(
                        listOf(PlayerInfo(Seat.P1, hostNickname), joiner!!),
                    ),
                )
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
            is NetplayMessage.ClientInput -> remoteP2Mask.set(msg.p2Mask)
            else -> Unit
        }
    }

    // ---- 房间动作（UI 线程） ----

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
        endpoint.send(NetplayMessage.Leave)
        endpoint.close()
    }

    fun close() {
        endpoint.close()
    }

    // ---- 对局动作（游戏线程） ----

    fun sendInputFrame(frame: Int, p1Mask: Int, p2Mask: Int, videoCrc32: Long?) {
        endpoint.send(NetplayMessage.Input(frame, p1Mask, p2Mask, videoCrc32))
    }

    /** 帧首取用加入端最新输入掩码（最新值槽，design 决策 5）。 */
    fun latestRemoteP2Mask(): Int = remoteP2Mask.get()
}
