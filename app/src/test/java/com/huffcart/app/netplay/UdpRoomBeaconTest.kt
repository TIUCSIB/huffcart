package com.huffcart.app.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UdpRoomBeaconTest {

    @Test
    fun `房间通告编解码 round-trip`() {
        val data = UdpRoomBeacon.encode("阿吹 的房间", 47477)
        val (name, port) = UdpRoomBeacon.decode(data, data.size)!!
        assertEquals("阿吹 的房间", name)
        assertEquals(47477, port)
    }

    @Test
    fun `房间名中的竖线被转义后可无损解出`() {
        val data = UdpRoomBeacon.encode("A|B 的房间", 47478)
        val (name, port) = UdpRoomBeacon.decode(data, data.size)!!
        assertEquals("A/B 的房间", name)
        assertEquals(47478, port)
    }

    @Test
    fun `截断与非法载荷返回 null`() {
        assertNull(UdpRoomBeacon.decode(byteArrayOf(), 0))
        val bad = UdpRoomBeacon.encode("X", 47477)
        assertNull(UdpRoomBeacon.decode(bad, 3)) // 截断
        val garbage = "OTHER|47477|X".encodeToByteArray()
        assertNull(UdpRoomBeacon.decode(garbage, garbage.size))
        val badPort = "HUFFCART1|abc|X".encodeToByteArray()
        assertNull(UdpRoomBeacon.decode(badPort, badPort.size))
        val badRange = "HUFFCART1|80|X".encodeToByteArray()
        assertNull(UdpRoomBeacon.decode(badRange, badRange.size))
    }

    @Test
    fun `关闭通告解码为端口 0`() {
        val data = UdpRoomBeacon.encodeClosed("阿吹 的房间")
        val (name, port) = UdpRoomBeacon.decode(data, data.size)!!
        assertEquals("阿吹 的房间", name)
        assertEquals(0, port)
    }
}
