package com.huffcart.app.netplay

/** 测试用内存双端：send 进对端收件箱，pump() 同步派发——状态机测试确定性执行。 */
class LoopbackEndpoint private constructor() : NetplayEndpoint {

    private var peer: LoopbackEndpoint? = null
    private val inbox = ArrayDeque<NetplayMessage>()
    private var handler: ((NetplayMessage) -> Unit)? = null
    private var disconnectHandler: ((String) -> Unit)? = null

    var closed = false
        private set

    override fun send(msg: NetplayMessage) {
        if (closed) return // 与 NetplayLink 一致：关闭后静默丢弃
        peer?.inbox?.addLast(msg)
    }

    override fun onMessage(handler: (NetplayMessage) -> Unit) {
        this.handler = handler
    }

    override fun onDisconnect(handler: (reason: String) -> Unit) {
        disconnectHandler = handler
    }

    override fun close() {
        closed = true
    }

    /** 把收件箱里的消息同步派发给本端 handler（模拟读线程）。 */
    fun pump() {
        while (inbox.isNotEmpty()) {
            handler?.invoke(inbox.removeFirst())
        }
    }

    /** 模拟本端链路断开（读异常/超时/对端关闭的归一出口）。 */
    fun fireDisconnect(reason: String) {
        disconnectHandler?.invoke(reason)
    }

    companion object {
        fun pair(): Pair<LoopbackEndpoint, LoopbackEndpoint> {
            val a = LoopbackEndpoint()
            val b = LoopbackEndpoint()
            a.peer = b
            b.peer = a
            return a to b
        }
    }
}
