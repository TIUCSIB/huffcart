package com.huffcart.app.netplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** 一间被发现的房间（NSD 解析结果 / UDP 广播命中）。
 *  capacity/count/gameName 由广播负载携带（v2），缺省时卡片按未知容量呈现。 */
data class DiscoveredRoom(
    val serviceName: String,
    val host: String,
    val port: Int,
    val hostNickname: String = "",
    val capacity: Int = 0,
    val playerCount: Int = 1,
    val gameName: String = "",
)

/**
 * NSD/mDNS 房间发现（design 决策 3）：房主注册 `_huffcart._tcp.` 服务
 * （服务名即房间名），加入端浏览并解析出 IP/端口。部分 OEM 的 mDNS
 * 不稳，手输 IP 直连是 spec 要求的兜底，不在这里补救。
 */
class NsdRoomDiscovery(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var regListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var onFound: ((DiscoveredRoom) -> Unit)? = null
    private var onLost: ((String) -> Unit)? = null

    private val pendingResolves = HashMap<String, NsdServiceInfo>()
    private var resolving = false

    fun registerRoom(roomName: String, port: Int, meta: String) {
        unregisterRoom()
        val info = NsdServiceInfo().apply {
            serviceName = roomName
            serviceType = SERVICE_TYPE
            setPort(port)
            // 广播负载（房间名之外的房间信息）：txt attribute，v2 卡片数据源
            runCatching { setAttribute("meta", meta) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        regListener = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun unregisterRoom() {
        regListener?.let {
            runCatching { nsd.unregisterService(it) }
            regListener = null
        }
    }

    fun startBrowsing(onFound: (DiscoveredRoom) -> Unit, onLost: (String) -> Unit) {
        stopBrowsing()
        this.onFound = onFound
        this.onLost = onLost
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType?.startsWith(SERVICE_TYPE.trimEnd('.')) != true) return
                synchronized(pendingResolves) {
                    if (pendingResolves.containsKey(serviceInfo.serviceName)) return
                    pendingResolves[serviceInfo.serviceName] = serviceInfo
                    pumpResolve()
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                onLost?.invoke(serviceInfo.serviceName ?: "")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopBrowsing() {
        discoveryListener?.let {
            runCatching { nsd.stopServiceDiscovery(it) }
            discoveryListener = null
        }
        synchronized(pendingResolves) { pendingResolves.clear() }
        onFound = null
        onLost = null
    }

    /** NSD 同一时刻只允许一个 resolve；逐个消化待解析服务。 */
    private fun pumpResolve() {
        if (resolving) return
        val next = pendingResolves.entries.firstOrNull() ?: return
        resolving = true
        val listener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                val name = serviceInfo.serviceName ?: ""
                val meta = serviceInfo.attributes?.get("meta")
                    ?.toString(Charsets.UTF_8) ?: ""
                synchronized(pendingResolves) {
                    pendingResolves.remove(name)
                    resolving = false
                    if (host != null) {
                        onFound?.invoke(parseMeta(DiscoveredRoom(name, host, serviceInfo.port), meta))
                    }
                    pumpResolve()
                }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(pendingResolves) {
                    pendingResolves.remove(serviceInfo.serviceName ?: "")
                    resolving = false
                    pumpResolve()
                }
            }
        }
        nsd.resolveService(next.value, listener)
    }

    private companion object {
        const val SERVICE_TYPE = "_huffcart._tcp."

        /** 广播 meta（hostNick|capacity|count|game）解析进房间条目；字段缺失用缺省。 */
        fun parseMeta(room: DiscoveredRoom, meta: String): DiscoveredRoom {
            if (meta.isBlank()) return room
            val parts = meta.split("|")
            return room.copy(
                hostNickname = parts.getOrNull(0).orEmpty(),
                capacity = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                playerCount = parts.getOrNull(2)?.toIntOrNull() ?: 1,
                gameName = parts.getOrNull(3).orEmpty(),
            )
        }
    }
}
