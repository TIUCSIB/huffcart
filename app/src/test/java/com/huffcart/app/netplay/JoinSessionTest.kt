package com.huffcart.app.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 任务 3.2/3.3 验证：加入端状态机（含被拒两分支、失同步判定）。 */
class JoinSessionTest {

    private class Collector {
        val received = ArrayList<NetplayMessage>()
        fun attach(endpoint: LoopbackEndpoint) {
            endpoint.onMessage { received.add(it) }
        }
        inline fun <reified T : NetplayMessage> filterIs(): List<T> = received.filterIsInstance<T>()
    }

    @Test
    fun `构造即发 HELLO 且收到玩家列表`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        val hostIn = Collector().also { it.attach(hostEnd) }
        var welcome: String? = null
        var players: List<PlayerInfo>? = null
        val join = JoinSession(joinerEnd, "0.1.0", "小刚", romLookup = { null })
        join.onWelcome = { welcome = it }
        join.onPlayers = { players = it }

        hostEnd.pump() // 构造时入箱的 HELLO
        assertEquals(
            NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"),
            hostIn.filterIs<NetplayMessage.Hello>().single(),
        )

        hostEnd.send(NetplayMessage.Welcome("阿吹"))
        hostEnd.send(
            NetplayMessage.PlayerList(
                listOf(PlayerInfo(Seat.P1, "阿吹"), PlayerInfo(Seat.P2, "小刚")),
            ),
        )
        joinerEnd.pump()
        assertEquals("阿吹", welcome)
        assertEquals(2, players?.size)
    }

    @Test
    fun `版本不一致与满员被拒`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        var reason: String? = null
        val join = JoinSession(joinerEnd, "0.1.0", "小刚", romLookup = { null })
        join.onDisconnected = { reason = it }

        hostEnd.send(NetplayMessage.Reject(RejectReason.VERSION_MISMATCH))
        joinerEnd.pump()
        assertEquals("双方版本不一致，请更新到相同版本", reason)
        assertTrue(joinerEnd.closed)

        val (h2, j2) = LoopbackEndpoint.pair()
        var reason2: String? = null
        val join2 = JoinSession(j2, "0.1.0", "小刚", romLookup = { null })
        join2.onDisconnected = { reason2 = it }
        h2.send(NetplayMessage.Reject(RejectReason.ROOM_FULL))
        j2.pump()
        assertEquals("房间已满", reason2)
    }

    @Test
    fun `选游戏校验三分支`() {
        run { // 本地有且哈希一致
            val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
            val hostIn = Collector().also { it.attach(hostEnd) }
            var picked: String? = null
            var ok: Boolean? = null
            val join = JoinSession(
                joinerEnd, "0.1.0", "小刚",
                romLookup = { RomFileInfo(24592L, 0x11223344L) },
            )
            join.onGamePicked = { name, localOk, _ -> picked = name; ok = localOk }

            hostEnd.send(NetplayMessage.PickGame("BattleCity.nes", 24592L, 0x11223344L))
            joinerEnd.pump()
            hostEnd.pump() // Ready 已入 host 收件箱
            assertEquals("BattleCity.nes", picked)
            assertEquals(true, ok)
            assertEquals(
                NetplayMessage.Ready(true, ""),
                hostIn.filterIs<NetplayMessage.Ready>().single(),
            )
        }
        run { // 本地没有
            val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
            val hostIn = Collector().also { it.attach(hostEnd) }
            JoinSession(joinerEnd, "0.1.0", "小刚", romLookup = { null })
            hostEnd.send(NetplayMessage.PickGame("X.nes", 1L, 2L))
            joinerEnd.pump()
            hostEnd.pump()
            assertEquals(
                NetplayMessage.Ready(false, "你还没有这款游戏"),
                hostIn.filterIs<NetplayMessage.Ready>().single(),
            )
        }
        run { // 哈希不一致
            val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
            val hostIn = Collector().also { it.attach(hostEnd) }
            JoinSession(
                joinerEnd, "0.1.0", "小刚",
                romLookup = { RomFileInfo(1L, 0x99L) },
            )
            hostEnd.send(NetplayMessage.PickGame("X.nes", 1L, 2L))
            joinerEnd.pump()
            hostEnd.pump()
            assertEquals(
                NetplayMessage.Ready(false, "双方游戏版本不一致"),
                hostIn.filterIs<NetplayMessage.Ready>().single(),
            )
        }
    }

    @Test
    fun `开局快照与输入节拍`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        val hostIn = Collector().also { it.attach(hostEnd) }
        var startState: ByteArray? = null
        val join = JoinSession(joinerEnd, "0.1.0", "小刚", romLookup = { null })
        join.onStartReceived = { startState = it }

        hostEnd.send(NetplayMessage.Start(byteArrayOf(9, 8, 7)))
        joinerEnd.pump()
        assertTrue(startState?.contentEquals(byteArrayOf(9, 8, 7)) == true)

        join.notifyGameLoaded()
        hostEnd.pump()
        assertEquals(1, hostIn.filterIs<NetplayMessage.GameReady>().size)

        join.sendLocalMask(0x14)
        hostEnd.pump()
        assertEquals(
            NetplayMessage.ClientInput(0x14),
            hostIn.filterIs<NetplayMessage.ClientInput>().single(),
        )

        hostEnd.send(NetplayMessage.Input(1, 0x01, 0x00, null))
        hostEnd.send(NetplayMessage.Input(2, 0x01, 0x14, null))
        joinerEnd.pump()
        assertEquals(1, join.awaitInput(100)?.frame)
        assertEquals(2, join.awaitInput(100)?.frame)
        assertNull(join.awaitInput(10))
    }

    @Test
    fun `帧号跳变与 CRC 不一致触发失同步`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        var desyncFrame = -1
        val join = JoinSession(joinerEnd, "0.1.0", "小刚", romLookup = { null })
        join.onDesync = { desyncFrame = it }

        hostEnd.send(NetplayMessage.Input(1, 0, 0, null))
        hostEnd.send(NetplayMessage.Input(64, 0, 0, 0xAAL)) // 帧号跳变：2..63 丢失
        joinerEnd.pump()
        assertEquals(64, desyncFrame)
        // 收到的帧仍按序全部消费（失同步是检测而非纠正）；两帧先后出队
        assertEquals(1, join.awaitInput(100)?.frame)
        assertEquals(64, join.awaitInput(100)?.frame)
        join.verifyFrame(0xAAL) // CRC 一致不重复触发
        join.verifyFrame(0x66L) // CRC 不一致触发
        assertEquals(64, desyncFrame)

        desyncFrame = -1
        hostEnd.send(NetplayMessage.Input(65, 0, 0, null)) // 帧号恢复连续
        joinerEnd.pump()
        assertEquals(-1, desyncFrame)
    }

    @Test
    fun `房主 Leave 归一为断线`() {
        val (hostEnd, joinerEnd) = LoopbackEndpoint.pair()
        var reason: String? = null
        val join = JoinSession(joinerEnd, "0.1.0", "小刚", romLookup = { null })
        join.onDisconnected = { reason = it }

        hostEnd.send(NetplayMessage.Leave)
        joinerEnd.pump()
        assertEquals("房主退出了房间", reason)
        assertTrue(joinerEnd.closed)
    }
}
