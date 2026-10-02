package com.huffcart.app.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 任务 3.1/3.3 验证：房主端状态机（内存 transport 模拟加入端）。 */
class HostSessionTest {

    /** joiner 端收集器：装 handler → pump 收集派发消息。 */
    private class Collector {
        val received = ArrayList<NetplayMessage>()
        fun attach(endpoint: LoopbackEndpoint) {
            endpoint.onMessage { received.add(it) }
        }
        inline fun <reified T : NetplayMessage> filterIs(): List<T> = received.filterIsInstance<T>()
    }

    @Test
    fun `握手成功广播玩家列表`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        val joinerIn = Collector().also { it.attach(joinerEnd) }
        var joined: PlayerInfo? = null
        val host = HostSession(hostEnd, "阿吹")
        host.onJoinerJoined = { joined = it }

        joinerEnd.send(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"))
        hostEnd.pump()

        assertEquals(PlayerInfo(Seat.P2, "小刚"), joined)
        assertEquals(PlayerInfo(Seat.P2, "小刚"), host.joiner)

        joinerEnd.pump()
        assertEquals(NetplayMessage.Welcome("阿吹"), joinerIn.received[0])
        assertEquals(
            NetplayMessage.PlayerList(
                listOf(PlayerInfo(Seat.P1, "阿吹"), PlayerInfo(Seat.P2, "小刚")),
            ),
            joinerIn.received[1],
        )
    }

    @Test
    fun `旧协议版本被拒绝`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        val joinerIn = Collector().also { it.attach(joinerEnd) }
        val host = HostSession(hostEnd, "阿吹")

        joinerEnd.send(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION - 1, "0.0.9", "小刚"))
        hostEnd.pump()
        joinerEnd.pump() // Reject 已入 joiner 收件箱

        assertEquals(
            listOf<NetplayMessage>(NetplayMessage.Reject(RejectReason.VERSION_MISMATCH)),
            joinerIn.received,
        )
        assertNull(host.joiner)
    }

    @Test
    fun `选游戏_快照开局_就绪_输入链路`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        val joinerIn = Collector().also { it.attach(joinerEnd) }
        val host = HostSession(hostEnd, "阿吹")
        var pickOk: Boolean? = null
        var pickReason: String? = null
        var gameReady = false
        host.onPickResult = { ok, reason -> pickOk = ok; pickReason = reason }
        host.onGameReady = { gameReady = true }

        joinerEnd.send(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"))
        hostEnd.pump()
        joinerEnd.pump()

        host.pickGame("BattleCity.nes", 24592L, 0x11223344L)
        joinerEnd.pump()
        assertEquals(
            NetplayMessage.PickGame("BattleCity.nes", 24592L, 0x11223344L),
            joinerIn.filterIs<NetplayMessage.PickGame>().single(),
        )

        joinerEnd.send(NetplayMessage.Ready(false, "你还没有这款游戏"))
        hostEnd.pump()
        assertEquals(false, pickOk)
        assertEquals("你还没有这款游戏", pickReason)

        host.startGame(ByteArray(100) { 7 })
        assertEquals(HostSession.Phase.STARTING, host.phase)
        joinerEnd.pump()
        assertEquals(
            NetplayMessage.Start(ByteArray(100) { 7 }),
            joinerIn.filterIs<NetplayMessage.Start>().single(),
        )

        val waiter = Thread { assertTrue(host.awaitGameReady(5_000)) }
        waiter.start()
        joinerEnd.send(NetplayMessage.GameReady)
        hostEnd.pump()
        waiter.join(5_000)
        assertEquals(HostSession.Phase.PLAYING, host.phase)
        assertTrue(gameReady)

        joinerEnd.send(NetplayMessage.ClientInput(0x1FF))
        hostEnd.pump()
        assertEquals(0x1FF, host.latestRemoteP2Mask())

        host.sendInputFrame(63, 0x10, 0x20, null)
        host.sendInputFrame(64, 0x10, 0x20, 0xDEADBEEFL)
        joinerEnd.pump()
        val inputs = joinerIn.filterIs<NetplayMessage.Input>()
        assertNull(inputs[0].videoCrc32)
        assertEquals(0xDEADBEEFL, inputs[1].videoCrc32)
    }

    @Test
    fun `Leave 与断线都归一为加入者离开`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        val host = HostSession(hostEnd, "阿吹")
        var leftReason: String? = null
        host.onJoinerLeft = { leftReason = it }

        joinerEnd.send(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"))
        hostEnd.pump()
        joinerEnd.send(NetplayMessage.Leave)
        hostEnd.pump()
        assertEquals("对方退出了房间", leftReason)
        assertNull(host.joiner)

        joinerEnd.send(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"))
        hostEnd.pump()
        hostEnd.fireDisconnect("连接中断") // 断线事件触发在房主端
        assertEquals("连接中断", leftReason)
    }
}
