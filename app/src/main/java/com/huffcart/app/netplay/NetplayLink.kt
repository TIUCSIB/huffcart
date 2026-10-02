package com.huffcart.app.netplay

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TCP 实现的 [NetplayEndpoint]（design 决策 3）。
 *
 * 线程模型：独立读线程（FrameAssembler 切帧）+ 写线程（队列化，游戏线程
 * send 只入队，永不阻塞在 socket 上——retro_* 单线程纪律不受网络 IO 影响）。
 * 读超时按端配置：加入端期待每帧 INPUT（超时即断）；房主端依赖加入端
 * 0.5s 级 CLIENT_INPUT 保活（超时即断）。EOF/异常/超时/协议错误全部归一
 * 为一次 onDisconnect。
 */
class NetplayLink(
    private val socket: Socket,
    readTimeoutMs: Int,
) : NetplayEndpoint {

    private val closed = AtomicBoolean(false)
    private val input: InputStream = socket.getInputStream().buffered()
    private val output: OutputStream = socket.getOutputStream().buffered()
    private val outbox = LinkedBlockingQueue<ByteArray>()

    private var messageHandler: ((NetplayMessage) -> Unit)? = null
    private var disconnectHandler: ((String) -> Unit)? = null

    private val reader = Thread({ readLoop() }, "netplay-reader")
    private val writer = Thread({ writeLoop() }, "netplay-writer")

    init {
        socket.tcpNoDelay = true
        socket.soTimeout = readTimeoutMs
    }

    fun start() {
        reader.start()
        writer.start()
    }

    override fun send(msg: NetplayMessage) {
        if (closed.get()) return
        outbox.offer(NetplayCodec.encode(msg))
    }

    override fun onMessage(handler: (NetplayMessage) -> Unit) {
        messageHandler = handler
    }

    override fun onDisconnect(handler: (reason: String) -> Unit) {
        disconnectHandler = handler
    }

    override fun setReadTimeout(ms: Int) {
        runCatching { socket.soTimeout = ms }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        outbox.offer(ByteArray(0)) // 写线程的毒丸：冲刷完队列后退出
        // 等写线程把排队的控制帧（REJECT/LEAVE）冲刷出去再断开，避免对端收不到
        runCatching { writer.join(500) }
        runCatching { socket.close() }
    }

    private fun readLoop() {
        val assembler = FrameAssembler()
        val chunk = ByteArray(16 * 1024)
        val ready = ArrayList<NetplayMessage>()
        try {
            while (!closed.get()) {
                val n = input.read(chunk)
                if (n < 0) throw IOException("对端关闭连接")
                if (n == 0) continue
                ready.clear()
                assembler.feed(chunk, n, ready)
                ready.forEach { messageHandler?.invoke(it) }
            }
        } catch (_: Exception) {
            val reason = if (closed.get()) "本端关闭" else "连接中断"
            finish(reason)
        }
    }

    private fun writeLoop() {
        try {
            while (true) {
                val bytes = outbox.take()
                if (bytes.isEmpty()) return
                output.write(bytes)
                output.flush()
            }
        } catch (_: Exception) {
            if (!closed.get()) finish("发送失败，连接中断")
        }
    }

    private fun finish(reason: String) {
        if (closed.compareAndSet(false, true)) {
            // println 而非 Log：本类被 JVM 单测直用，android.util.Log 桩会抛 not mocked；
            // 真机上经 System.out 进 logcat（tag System.out）
            println("[NetplayLink] finish: $reason")
            runCatching { socket.close() }
            disconnectHandler?.invoke(reason)
        }
    }
}
