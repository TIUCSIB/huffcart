package com.huffcart.app.netplay

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * UDP 广播房间发现（真机联调反馈：NSD/mDNS 在模拟器与部分机型上不可靠）：
 * 房主创建房间后周期性广播房间通告（房间名 + TCP 端口），加入端在联机屏
 * 期间监听。与 NSD 并行运行、结果合并；广播收发不依赖 mdnssd 守护进程。
 *
 * 线格式（v2）：`HUFFCART2|<tcpPort>|<房间名>|<房主昵称>|<容量>|<人数>|<游戏名>`
 * （字段中的 '|' 转义为 '/'）。tcpPort=0 为**关闭通告**：房主退出时连发数个，
 * 加入端收到即移除条目——正常退出即时消失；TTL 仅兜底进程被杀等异常死亡。
 * 广播地址双发：受限广播 255.255.255.255 + 本机 /24 定向广播；接收端持有
 * MulticastLock（部分机型收广播/组播必需）。
 */
object UdpRoomBeacon {

    const val PORT: Int = 47478
    private const val MAGIC = "HUFFCART2"
    private const val MAX_PACKET = 512

    /** 广播负载（房间名之外的卡片数据源），人数变化时由 Announcer 动态取值。 */
    data class RoomMeta(
        val hostNickname: String,
        val capacity: Int,
        val gameName: String,
    )

    fun encode(roomName: String, tcpPort: Int, meta: RoomMeta, playerCount: Int): ByteArray {
        val safe = { s: String -> s.replace("|", "/") }
        return (
            "$MAGIC|$tcpPort|${safe(roomName)}|${safe(meta.hostNickname)}|" +
                "${meta.capacity}|$playerCount|${safe(meta.gameName)}"
            ).encodeToByteArray()
    }

    /** 房间关闭通告（端口记 0）：房主退出时连发数个，加入端收到即移除条目。 */
    fun encodeClosed(roomName: String): ByteArray {
        val safe = roomName.replace("|", "/")
        return "$MAGIC|0|$safe".encodeToByteArray()
    }

    /** 解码结果：房间名 + 端口 + v2 卡片元数据（v1 载荷无 meta，count 恒 1）。 */
    data class Decoded(
        val roomName: String,
        val tcpPort: Int,
        val meta: RoomMeta?,
        val playerCount: Int,
    )

    /**
     * 解出广播载荷；房主 IP 从数据报来源地址取。
     * 端口 0 = 关闭通告；v1 载荷（无 meta 字段）兼容为缺省 meta；其余非法返回 null。
     */
    fun decode(data: ByteArray, length: Int): Decoded? {
        val text = data.decodeToString(0, length.coerceAtMost(MAX_PACKET))
        val parts = text.split("|")
        if (parts.size < 3 || parts[0] != MAGIC) return null
        val port = parts[1].toIntOrNull() ?: return null
        if (port == 0) return Decoded(parts.drop(2).joinToString("|"), 0, null, 0)
        if (port !in 1024..65535) return null
        if (parts.size < 7) {
            // v1 兼容：仅房间名
            return Decoded(parts.drop(2).joinToString("|"), port, null, 1)
        }
        val meta = RoomMeta(
            hostNickname = parts[3],
            capacity = parts[4].toIntOrNull() ?: 0,
            gameName = parts.drop(6).joinToString("|"),
        )
        val count = parts[5].toIntOrNull() ?: 1
        return Decoded(parts[2], port, meta, count)
    }

    fun broadcastAddresses(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses.asSequence() }
                .forEach { ia ->
                    val addr = ia.address
                    if (addr is Inet4Address && ia.broadcast != null) {
                        targets.add(ia.broadcast)
                    }
                }
        }
        return targets.distinctBy { it.hostAddress }
    }

    /** 房主侧：每 1.5s 广播一次房间通告（人数动态取自 [playerCount]）；[stop] 时连发关闭通告再收摊。 */
    class Announcer(
        private val roomName: String,
        private val tcpPort: Int,
        private val meta: RoomMeta,
        private val playerCount: () -> Int,
    ) {

        @Volatile
        private var running = false
        private var socket: DatagramSocket? = null
        private var thread: Thread? = null

        fun start() {
            if (running) return
            running = true
            thread = Thread({
                try {
                    val sock = DatagramSocket().apply { broadcast = true }
                    socket = sock
                    val targets = broadcastAddresses()
                    while (running) {
                        val data = UdpRoomBeacon.encode(roomName, tcpPort, meta, playerCount())
                        targets.forEach { target ->
                            runCatching { sock.send(DatagramPacket(data, data.size, target, PORT)) }
                        }
                        Thread.sleep(1_500)
                    }
                } catch (_: Throwable) {
                    // socket 关闭（stop）或网络消失：安静退出
                }
            }, "netplay-announcer").apply { isDaemon = true; start() }
        }

        fun stop() {
            if (!running) return
            running = false
            val sock = socket
            socket = null
            thread = null
            if (sock == null) return
            // 关闭通告连发 3 遍（UDP 尽力而为），在线的加入端即时移除条目；
            // 短生命周期线程发送，避免阻塞主线程（leaveRoom 在 UI 调用）
            Thread({
                try {
                    val data = UdpRoomBeacon.encodeClosed(roomName)
                    repeat(3) {
                        broadcastAddresses().forEach { target ->
                            runCatching { sock.send(DatagramPacket(data, data.size, target, PORT)) }
                        }
                        Thread.sleep(60)
                    }
                } catch (_: Throwable) {
                } finally {
                    runCatching { sock.close() }
                }
            }, "netplay-goodbye").apply { isDaemon = true; start() }
        }
    }

    /** 加入端侧：联机屏期间监听广播，命中/关闭即回调（回调在监听线程上）。 */
    class Listener(
        private val onFound: (DiscoveredRoom) -> Unit,
        private val onClosed: (host: String) -> Unit = {},
    ) {

        @Volatile
        private var running = false
        private var socket: DatagramSocket? = null
        private var multicastLock: WifiManager.MulticastLock? = null

        fun start(context: Context) {
            if (running) return
            running = true
            multicastLock = (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                ?.createMulticastLock("huffcart-discovery")
                ?.apply { setReferenceCounted(false); acquire() }
            Thread({
                try {
                    val sock = DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(PORT))
                    }
                    socket = sock
                    val buf = ByteArray(MAX_PACKET)
                    while (running) {
                        val pkt = DatagramPacket(buf, buf.size)
                        sock.receive(pkt)
                        val decoded = UdpRoomBeacon.decode(pkt.data, pkt.length) ?: continue
                        val host = pkt.address?.hostAddress ?: continue
                        if (decoded.tcpPort == 0) {
                            onClosed(host)
                        } else {
                            onFound(
                                DiscoveredRoom(
                                    serviceName = decoded.roomName,
                                    host = host,
                                    port = decoded.tcpPort,
                                    hostNickname = decoded.meta?.hostNickname.orEmpty(),
                                    capacity = decoded.meta?.capacity ?: 0,
                                    playerCount = decoded.playerCount,
                                    gameName = decoded.meta?.gameName.orEmpty(),
                                ),
                            )
                        }
                    }
                } catch (_: Throwable) {
                    // stop() 关闭 socket 或网络消失：安静退出
                }
            }, "netplay-listener").apply { isDaemon = true; start() }
        }

        fun stop() {
            running = false
            runCatching { socket?.close() }
            socket = null
            runCatching { multicastLock?.release() }
            multicastLock = null
        }
    }
}
