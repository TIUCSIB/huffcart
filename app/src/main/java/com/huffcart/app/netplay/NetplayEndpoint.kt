package com.huffcart.app.netplay

import java.io.Closeable

/**
 * 一条已建立的联机端点（传输无关）。会话状态机只依赖本接口，
 * 单测以内存双端实现替代真实 TCP（design 决策 3/4）。
 *
 * 线程约定：[onMessage] 与 [onDisconnect] 在传输层线程回调；
 * 会话实现不得假设回调发生在特定线程，须自行保证状态可见性。
 */
interface NetplayEndpoint : Closeable {

    /** 发送一条消息（实现须不阻塞调用方超过毫秒级；入队即可）。 */
    fun send(msg: NetplayMessage)

    fun onMessage(handler: (NetplayMessage) -> Unit)

    /** 断线归一出口：读写异常/读超时/对端 Leave/本地 close 后对端感知，统一走这里。 */
    fun onDisconnect(handler: (reason: String) -> Unit)

    /** 调整读超时（阶段相关：大厅宽松、对局收紧；无实现差异的传输可忽略）。 */
    fun setReadTimeout(ms: Int) {}
}
