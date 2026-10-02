package com.huffcart.app.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomCodeTest {

    @Test
    fun `从 IP 提取三位房间码`() {
        assertEquals("066", RoomCode.fromIp("192.168.31.66"))
        assertEquals("001", RoomCode.fromIp("10.0.0.1"))
        assertEquals("254", RoomCode.fromIp("192.168.43.254"))
        assertEquals("000", RoomCode.fromIp("192.168.1.0"))
    }

    @Test
    fun `非法 IP 返回 null`() {
        assertNull(RoomCode.fromIp("not-an-ip"))
        assertNull(RoomCode.fromIp("192.168.1"))
        assertNull(RoomCode.fromIp("192.168.1.999"))
        assertNull(RoomCode.fromIp("192.168.1.x"))
    }

    @Test
    fun `房间码经加入端网段还原房主 IP`() {
        assertEquals(
            "192.168.31.66",
            RoomCode.resolve("066", localIp = "192.168.31.12"),
        )
        assertEquals(
            "192.168.43.7",
            RoomCode.resolve("7", localIp = "192.168.43.99"), // 兼容不带前导零
        )
    }

    @Test
    fun `非法房间码或无本机 IP 返回 null`() {
        assertNull(RoomCode.resolve("", "192.168.1.2"))
        assertNull(RoomCode.resolve("abc", "192.168.1.2"))
        assertNull(RoomCode.resolve("0", "192.168.1.2"))
        assertNull(RoomCode.resolve("255", "192.168.1.2"))
        assertNull(RoomCode.resolve("1234", "192.168.1.2"))
        assertNull(RoomCode.resolve("66", "no-ip"))
    }
}
