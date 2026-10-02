package com.huffcart.app.netplay

/**
 * 房间码（netplay-lan，探索反馈 3）：局域网内 IPv4 唯一 ⇒ 取房主地址末段
 * 作 3 位十进制码（192.168.31.66 → "066"）。加入端用自身地址的 /24 前缀
 * 还原完整 IP——同一 Wi-Fi（含房主热点，通常为 192.168.43.x）下无歧义。
 * 非常规子网（/16 等）解析可能落空，NSD 发现是主路径，此处只是兜底。
 */
object RoomCode {

    /** 从 IPv4 字符串取房间码；非法或非 IPv4 返回 null。 */
    fun fromIp(ip: String): String? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return "%03d".format(octets[3])
    }

    /** 用加入端自身 IP 的网段前缀把房间码还原成房主 IP；无法解析返回 null。 */
    fun resolve(code: String, localIp: String): String? {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || trimmed.length > 3 || trimmed.any { !it.isDigit() }) return null
        val octet = trimmed.toInt()
        if (octet !in 1..254) return null
        val prefix = localIp.split(".").take(3).joinToString(".")
        if (prefix.split(".").size != 3 || prefix.any { !it.isDigit() && it != '.' }) return null
        return "$prefix.$octet"
    }
}
