package com.huffcart.app.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UdpRoomBeaconTest {

    @Test
    fun `房间通告编解码 round-trip（含 v2 卡片数据）`() {
        val meta = UdpRoomBeacon.RoomMeta("阿吹", 4, "SnowBros.nes")
        val data = UdpRoomBeacon.encode("联机房间", 47477, meta, playerCount = 2)
        val d = UdpRoomBeacon.decode(data, data.size)!!
        assertEquals("联机房间", d.roomName)
        assertEquals(47477, d.tcpPort)
        assertEquals("阿吹", d.meta!!.hostNickname)
        assertEquals(4, d.meta.capacity)
        assertEquals("SnowBros.nes", d.meta.gameName)
        assertEquals(2, d.playerCount)
    }

    @Test
    fun `v1 载荷兼容为缺省 meta`() {
        val data = "HUFFCART2|47477|旧的房间".encodeToByteArray()
        val d = UdpRoomBeacon.decode(data, data.size)!!
        assertEquals("旧的房间", d.roomName)
        assertEquals(47477, d.tcpPort)
        assertNull(d.meta)
        assertEquals(1, d.playerCount)
    }

    @Test
    fun `字段中的竖线被转义后可无损解出`() {
        val meta = UdpRoomBeacon.RoomMeta("A|B", 2, "X|Y.nes")
        val data = UdpRoomBeacon.encode("A|B 的房间", 47478, meta, playerCount = 1)
        val d = UdpRoomBeacon.decode(data, data.size)!!
        assertEquals("A/B 的房间", d.roomName)
        assertEquals(47478, d.tcpPort)
        assertEquals("A/B", d.meta!!.hostNickname)
        assertEquals("X/Y.nes", d.meta.gameName)
    }

    @Test
    fun `截断与非法载荷返回 null`() {
        assertNull(UdpRoomBeacon.decode(byteArrayOf(), 0))
        val meta = UdpRoomBeacon.RoomMeta("阿吹", 2, "X.nes")
        val bad = UdpRoomBeacon.encode("X", 47477, meta, playerCount = 1)
        assertNull(UdpRoomBeacon.decode(bad, 3)) // 截断
        val garbage = "OTHER|47477|X".encodeToByteArray()
        assertNull(UdpRoomBeacon.decode(garbage, garbage.size))
        val badPort = "HUFFCART2|abc|X|阿吹|2|1|X.nes".encodeToByteArray()
        assertNull(UdpRoomBeacon.decode(badPort, badPort.size))
        val badRange = "HUFFCART2|80|X|阿吹|2|1|X.nes".encodeToByteArray()
        assertNull(UdpRoomBeacon.decode(badRange, badRange.size))
    }

    @Test
    fun `关闭通告解码为端口 0`() {
        val data = UdpRoomBeacon.encodeClosed("阿吹 的房间")
        val d = UdpRoomBeacon.decode(data, data.size)!!
        assertEquals("阿吹 的房间", d.roomName)
        assertEquals(0, d.tcpPort)
        assertNull(d.meta)
    }
}
