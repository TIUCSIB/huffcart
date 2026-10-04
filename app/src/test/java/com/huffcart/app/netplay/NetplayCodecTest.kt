package com.huffcart.app.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.IOException
import java.nio.ByteBuffer

class NetplayCodecTest {

    /** feed 编码帧 → 解出的消息列表（helper）。 */
    private fun decodeFrame(encoded: ByteArray): List<NetplayMessage> {
        val out = ArrayList<NetplayMessage>()
        FrameAssembler().feed(encoded, encoded.size, out)
        return out
    }

    @Test
    fun `全部消息类型 round-trip`() {
        val messages = listOf(
            NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"),
            NetplayMessage.Welcome("阿吹", Seat.P3, 4),
            NetplayMessage.Reject(RejectReason.ROOM_FULL),
            NetplayMessage.Reject(RejectReason.VERSION_MISMATCH),
            NetplayMessage.PlayerList(
                listOf(
                    PlayerInfo(Seat.P1, "阿吹"),
                    PlayerInfo(Seat.P2, "小刚"),
                    PlayerInfo(Seat.P3, "大毛"),
                    PlayerInfo(Seat.P4, "二毛"),
                ),
            ),
            NetplayMessage.PickGame("Contra (J).nes", 131072L, 0x1A2B3C4D5EL),
            NetplayMessage.Ready(ok = true, reason = ""),
            NetplayMessage.Ready(ok = false, reason = "你还没有这款游戏"),
            NetplayMessage.Start(byteArrayOf(1, 2, 3, 4, 5)),
            NetplayMessage.Start(ByteArray(300_000) { (it % 251).toByte() }),
            NetplayMessage.GameReady,
            NetplayMessage.Leave,
            NetplayMessage.ClientInput(0x1FF),
            NetplayMessage.Ping,
            NetplayMessage.Pong,
        )
        messages.forEach { original ->
            val decoded = decodeFrame(NetplayCodec.encode(original))
            assertEquals(listOf(original), decoded, "round-trip 失败: $original")
        }
    }

    @Test
    fun `INPUT 帧非校验帧不带 CRC`() {
        val msg = NetplayMessage.Input(
            frame = 63, p1Mask = 0x10, p2Mask = 0x201, p3Mask = 0x2, p4Mask = 0x1,
            videoCrc32 = null,
        )
        val decoded = decodeFrame(NetplayCodec.encode(msg)).single() as NetplayMessage.Input
        assertEquals(63, decoded.frame)
        assertEquals(0x10, decoded.p1Mask)
        assertEquals(0x201, decoded.p2Mask)
        assertEquals(0x2, decoded.p3Mask)
        assertEquals(0x1, decoded.p4Mask)
        assertNull(decoded.videoCrc32)
    }

    @Test
    fun `INPUT 帧 64 的倍数搭载 CRC`() {
        val msg = NetplayMessage.Input(
            frame = 128, p1Mask = 0, p2Mask = 0, p3Mask = 0, p4Mask = 0,
            videoCrc32 = 0xDEADBEEFL,
        )
        val decoded = decodeFrame(NetplayCodec.encode(msg)).single() as NetplayMessage.Input
        assertEquals(128, decoded.frame)
        assertEquals(0xDEADBEEFL, decoded.videoCrc32)
        assertTrue(NetplayCodec.isChecksumFrame(64) && NetplayCodec.isChecksumFrame(128))
        assertTrue(!NetplayCodec.isChecksumFrame(63))
    }

    @Test
    fun `截断帧先不出消息，补齐后解出`() {
        val encoded = NetplayCodec.encode(NetplayMessage.Welcome("阿吹", Seat.P2, 4))
        val assembler = FrameAssembler()
        val out = ArrayList<NetplayMessage>()

        assembler.feed(encoded, 3, out) // 只有长度前缀的一部分
        assertTrue(out.isEmpty())
        assembler.feed(encoded.copyOfRange(3, encoded.size), encoded.size - 3, out) // 余下部分
        assertEquals(listOf<NetplayMessage>(NetplayMessage.Welcome("阿吹", Seat.P2, 4)), out)
    }

    @Test
    fun `粘帧一次解出多条`() {
        val a = NetplayCodec.encode(NetplayMessage.Leave)
        val b = NetplayCodec.encode(NetplayMessage.GameReady)
        val both = a + b
        val out = ArrayList<NetplayMessage>()
        FrameAssembler().feed(both, both.size, out)
        assertEquals(listOf(NetplayMessage.Leave, NetplayMessage.GameReady), out)
    }

    @Test
    fun `超长帧长拒收为协议错误`() {
        val garbage = ByteBuffer.allocate(4).putInt(MAX_FRAME_BYTES + 1).array()
        val out = ArrayList<NetplayMessage>()
        assertFailsWith<IOException> { FrameAssembler().feed(garbage, garbage.size, out) }
    }

    @Test
    fun `未知帧类型拒收`() {
        val frame = ByteBuffer.allocate(4 + 1).putInt(1).put(99.toByte()).array()
        val out = ArrayList<NetplayMessage>()
        assertFailsWith<IOException> { FrameAssembler().feed(frame, frame.size, out) }
    }

    @Test
    fun `超长昵称编码截断而不抛异常（审计 S5）`() {
        val big = "超".repeat(100_000) // 300KB，远超编码上限
        val decoded = decodeFrame(
            NetplayCodec.encode(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", big)),
        ).single() as NetplayMessage.Hello
        assertTrue(decoded.nickname.isNotEmpty() && decoded.nickname.length < big.length)
    }

    @Test
    fun `超大 Start 快照按需分配并 round-trip（审计 M6）`() {
        // 原 encodeBody 恒定 1MB：>1MB 快照直接 BufferOverflow，且每帧 Input 白扔 1MB
        val state = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val decoded = decodeFrame(NetplayCodec.encode(NetplayMessage.Start(state))).single()
        assertTrue(decoded is NetplayMessage.Start && decoded.state.contentEquals(state))
    }

    @Test
    fun `按钮掩码 diff 出按下与抬起`() {
        val b = ButtonMask.bit(com.huffcart.core.bridge.RetroButton.B)
        val a = ButtonMask.bit(com.huffcart.core.bridge.RetroButton.A)
        val up = ButtonMask.bit(com.huffcart.core.bridge.RetroButton.UP)
        // A 在 bit8：确保 u16 线上格式保留它
        assertEquals(1 shl 8, a)

        val downs = ArrayList<com.huffcart.core.bridge.RetroButton>()
        val lifts = ArrayList<com.huffcart.core.bridge.RetroButton>()
        ButtonMask.diff(prev = b or up, next = a or b, { downs.add(it) }, { lifts.add(it) })
        assertEquals(listOf(com.huffcart.core.bridge.RetroButton.A), downs)
        assertEquals(listOf(com.huffcart.core.bridge.RetroButton.UP), lifts)
    }
}
