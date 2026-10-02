package com.huffcart.app.netplay

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 房主侧 TCP 接纳循环（design 决策 3）。从固定端口起尝试绑定
 * （被占则 +1 顺延），每个接入连接交给 [onClient]（满员拒绝等策略
 * 由上层 NetplayManager 决定）。
 */
class NetplayServer(
    private val onClient: (NetplayLink) -> Unit,
) {
    var port: Int = NETPLAY_DEFAULT_PORT
        private set

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null

    /** 尝试启动；返回实际监听端口，失败返回 -1。 */
    fun start(): Int {
        if (running.get()) return port
        var lastError: Exception? = null
        for (candidate in NETPLAY_DEFAULT_PORT until NETPLAY_DEFAULT_PORT + 10) {
            val ss = ServerSocket()
            try {
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(candidate))
                serverSocket = ss
                port = candidate
                running.set(true)
                acceptThread = Thread({ acceptLoop(ss) }, "netplay-accept").apply { start() }
                return candidate
            } catch (e: Exception) {
                lastError = e
                runCatching { ss.close() }
            }
        }
        throw IllegalStateException("无法绑定联机端口", lastError)
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        runCatching { serverSocket?.close() }
        acceptThread?.interrupt()
        acceptThread = null
        serverSocket = null
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get()) {
            try {
                val socket: Socket = ss.accept()
                // 读超时取大厅宽松值（等待/选游戏期间靠加入端 2s 保活维持）；
                // 对局中另有逐帧数据流，无需收紧
                val link = NetplayLink(socket, readTimeoutMs = 30_000)
                link.start()
                onClient(link)
            } catch (_: Exception) {
                if (running.get()) continue else return
            }
        }
    }

    companion object {
        /** 加入端建立直连（NSD 不可用时的手输 IP 兜底）。
         *  读超时先给大厅用的宽松值（等房主选游戏可静默数十秒），
         *  进入对局由 JoinSession 收紧到逐帧级。 */
        fun connect(host: String, port: Int = NETPLAY_DEFAULT_PORT, timeoutMs: Int = 4_000): NetplayLink {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            val link = NetplayLink(socket, readTimeoutMs = 30_000)
            link.start()
            return link
        }
    }
}
