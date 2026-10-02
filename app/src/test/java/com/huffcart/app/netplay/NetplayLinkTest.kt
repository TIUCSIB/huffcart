package com.huffcart.app.netplay

import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 任务 2.1 验证：本机回环双端对传——host 收到 HELLO、joiner 收到 WELCOME、断线归一。 */
class NetplayLinkTest {

    @Test
    fun `TCP 回环对传消息与断线`() {
        val hostGotHello = CountDownLatch(1)
        val joinerGotWelcome = CountDownLatch(1)
        val hostDisconnected = CountDownLatch(1)
        var hostMessage: NetplayMessage? = null

        // 回声服务端（房主侧）：收到 HELLO 回 WELCOME
        val server = ServerSocket(0).also { it.reuseAddress = true }
        val serverThread = Thread {
            val link = NetplayLink(server.accept(), readTimeoutMs = 5_000)
            link.onMessage { msg ->
                when (msg) {
                    is NetplayMessage.Hello -> {
                        hostMessage = msg
                        hostGotHello.countDown()
                        link.send(NetplayMessage.Welcome("阿吹"))
                    }
                    else -> Unit
                }
            }
            link.onDisconnect { hostDisconnected.countDown() }
            link.start()
        }.apply { start() }

        // 加入端：连接、发 HELLO、等 WELCOME
        val joinerLink = NetplayServer.connect("127.0.0.1", server.localPort)
        joinerLink.onMessage { msg ->
            if (msg is NetplayMessage.Welcome) joinerGotWelcome.countDown()
        }
        serverThread.join(5_000)
        joinerLink.send(NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"))

        assertTrue(hostGotHello.await(5, TimeUnit.SECONDS), "host 未收到 HELLO")
        assertEquals(
            NetplayMessage.Hello(NETPLAY_PROTOCOL_VERSION, "0.1.0", "小刚"),
            hostMessage,
        )
        assertTrue(joinerGotWelcome.await(5, TimeUnit.SECONDS), "joiner 未收到 WELCOME")

        // 断线归一：加入端关闭 → 房主侧收到断线
        joinerLink.close()
        assertTrue(hostDisconnected.await(5, TimeUnit.SECONDS), "host 未感知断线")

        server.close()
    }
}
