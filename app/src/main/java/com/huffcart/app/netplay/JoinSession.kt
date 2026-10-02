package com.huffcart.app.netplay

import java.util.concurrent.LinkedBlockingQueue

/**
 * 加入端会话（design 决策 1/2）：LOBBY（握手、玩家列表、校验房主选的游戏）
 * → WAIT_START（收 Start+快照，导航进游戏屏）→ PLAYING（跟随主机节拍）。
 * 加入端游戏循环以 [awaitInput] 取到的主机帧输入为节拍；本机输入经
 * [sendLocalMask] 上送（变化即发 + 保活），核心内不本地注入。
 */
class JoinSession(
    private val endpoint: NetplayEndpoint,
    appVersion: String,
    nickname: String,
    private val romLookup: (String) -> RomFileInfo?,
) {

    var onWelcome: ((hostNickname: String) -> Unit)? = null
    var onPlayers: ((List<PlayerInfo>) -> Unit)? = null
    var onGamePicked: ((romName: String, localOk: Boolean, localReason: String) -> Unit)? = null
    var onStartReceived: ((state: ByteArray) -> Unit)? = null
    var onDesync: ((frame: Int) -> Unit)? = null
    var onDisconnected: ((reason: String) -> Unit)? = null

    private val inputQueue = LinkedBlockingQueue<NetplayMessage.Input>()

    @Volatile
    private var running = true

    @Volatile
    private var lastSentMask = 0

    init {
        endpoint.send(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, appVersion, nickname))
        endpoint.onMessage(::route)
        endpoint.onDisconnect { reason ->
            println("[StopSeq] joiner: onDisconnect " + reason)
            running = false
            inputQueue.clear()
            onDisconnected?.invoke(reason)
        }
        // 保活线程（design 决策 5）：大厅阶段加入端没有逐帧流量，房主靠
        // 周期性 CLIENT_INPUT 判定本端存活；对局中游戏循环的更高频上送并存。
        // 首跳延后 2s（HELLO 刚发，无需立即保活）
        Thread({
            while (running) {
                Thread.sleep(2_000)
                if (!running) break
                endpoint.send(NetplayMessage.ClientInput(lastSentMask))
            }
        }, "netplay-joiner-keepalive").apply { isDaemon = true; start() }
    }

    // 最近消费的输入帧（帧号连续性校验 + CRC 比对），游戏线程独占访问
    private var expectedFrame = 1
    private var consumed: NetplayMessage.Input? = null

    private fun route(msg: NetplayMessage) {
        when (msg) {
            is NetplayMessage.Welcome -> onWelcome?.invoke(msg.hostNickname)
            is NetplayMessage.Reject -> {
                val reason = when (msg.reason) {
                    RejectReason.VERSION_MISMATCH -> "双方版本不一致，请更新到相同版本"
                    RejectReason.ROOM_FULL -> "房间已满"
                    else -> "加入被拒绝"
                }
                endpoint.close()
                onDisconnected?.invoke(reason)
            }
            is NetplayMessage.PlayerList -> onPlayers?.invoke(msg.players)
            is NetplayMessage.PickGame -> {
                val info = romLookup(msg.romName)
                val ok = info != null && info.size == msg.size && info.crc32 == msg.crc32
                val reason = when {
                    info == null -> "你还没有这款游戏"
                    !ok -> "双方游戏版本不一致"
                    else -> ""
                }
                endpoint.send(NetplayMessage.Ready(ok, reason))
                onGamePicked?.invoke(msg.romName, ok, reason)
            }
            is NetplayMessage.Start -> onStartReceived?.invoke(msg.state)
            is NetplayMessage.Leave -> {
                println("[StopSeq] joiner: LEAVE received, clearing queue")
                // 与断线路径同等处理：立即停掉输入流并清空积压帧——否则游戏循环
                // 会继续消耗房主退出前缓冲的输入帧，与 stop() 的核心释放竞态
                // （game 线程还在 retro_run 时 deinit → native 崩溃，真机已复现）
                running = false
                inputQueue.clear()
                endpoint.close()
                onDisconnected?.invoke("房主退出了房间")
            }
            is NetplayMessage.Input -> {
                if (msg.frame != expectedFrame) {
                    onDesync?.invoke(msg.frame)
                    expectedFrame = msg.frame
                }
                expectedFrame = msg.frame + 1
                consumed = msg
                inputQueue.offer(msg)
            }
            else -> Unit
        }
    }

    // ---- 对局动作（游戏线程） ----

    /** 加入端游戏会话加载完开局快照后调用：房主收到才进入帧循环（task 4.2 握手）。
     *  同时把读超时从大厅的宽松值收紧到对局级——主机停止发帧 10s 才判死
     *  （容忍单次长 GC / 音频卡顿，真机联调反馈「老是断开」的缓解之一）。 */
    fun notifyGameLoaded() {
        endpoint.setReadTimeout(10_000)
        endpoint.send(NetplayMessage.GameReady)
    }

    /** 加入端节拍：阻塞等待主机第 N 帧输入；超时返回 null（由调用方判断线）。 */
    fun awaitInput(timeoutMs: Long): NetplayMessage.Input? = inputQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)

    /** 本机（P2）掩码上送：变化即发；保活线程周期重发最新值。 */
    fun sendLocalMask(mask: Int) {
        lastSentMask = mask
        endpoint.send(NetplayMessage.ClientInput(mask))
    }

    /**
     * 失同步判定（task 3.3）：调用方在跑完与所消费 INPUT 同号的帧后传入本帧
     * 视频 CRC；主机搭载的 CRC 与本地不一致（或帧号跳变，见 route）即触发
     * onDesync。不中断对局。
     */
    fun verifyFrame(localCrc32: Long) {
        val input = consumed ?: return
        val remote = input.videoCrc32 ?: return
        if (remote != localCrc32) onDesync?.invoke(input.frame)
    }

    fun leave() {
        running = false
        endpoint.send(NetplayMessage.Leave)
        endpoint.close()
    }

    fun close() {
        running = false
        endpoint.close()
    }
}
