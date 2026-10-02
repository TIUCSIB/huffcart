package com.huffcart.app.netplay

import com.huffcart.core.bridge.RetroButton
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.CRC32

/**
 * 联机线协议（design 决策 4）：帧格式 `u32 载荷长度 + u8 类型 + 载荷`，
 * 多字节整数一律大端。线格式不兼容变更时递增 [NETPLAY_PROTOCOL_VERSION]。
 *
 * v2（netplay-lobby-v2）：Seat 扩到 P1–P4（PlayerList 席位字节 0–3）、Input
 * 携带四席位掩码、Welcome 携带入座席位与房间容量、新增 Ping/Pong 心跳。
 *
 * 掩码位布局与 LibretroCore.setButton 保持逐位一致（core-native 模块，
 * bit = 1 << libretro device id：B0 SELECT2 START3 UP4 DOWN5 LEFT6 RIGHT7 A8），
 * 两处必须同改——A 键在 bit 8，故线上掩码为 u16。
 */
const val NETPLAY_PROTOCOL_VERSION: Int = 2

/** 手输 IP 加入使用的默认端口；NSD 发现的服务携带房主实际监听端口。 */
const val NETPLAY_DEFAULT_PORT: Int = 47477

/** 失同步校验周期：每 N 帧在 INPUT 帧尾搭载一次视频 CRC32（房主侧）。 */
const val DESYNC_CHECK_PERIOD: Int = 64

/** 帧长合法性上界（状态快照数百 KB，8MB 足够；超出即判协议错误断开）。 */
const val MAX_FRAME_BYTES: Int = 8 * 1024 * 1024

enum class Seat(val label: String) { P1("P1"), P2("P2"), P3("P3"), P4("P4") }

data class PlayerInfo(val seat: Seat, val nickname: String)

/** 本地 ROM 的校验信息（房间内同款校验用）。 */
data class RomFileInfo(val size: Long, val crc32: Long)

enum class RejectReason { VERSION_MISMATCH, ROOM_FULL, ERROR }

sealed class NetplayMessage {

    /** 加入端 → 房主，连接后第一条消息。 */
    data class Hello(
        val protocolVersion: Int,
        val appVersion: String,
        val nickname: String,
    ) : NetplayMessage()

    data class Welcome(
        val hostNickname: String,
        val seat: Seat,
        val capacity: Int,
    ) : NetplayMessage()

    data class Reject(val reason: RejectReason) : NetplayMessage()

    data class PlayerList(val players: List<PlayerInfo>) : NetplayMessage()

    /** 房主 → 加入端：选中游戏（内容哈希供双方校验同款）。 */
    data class PickGame(
        val romName: String,
        val size: Long,
        val crc32: Long,
    ) : NetplayMessage()

    /** 加入端 → 房主：ROM 校验结果（ok=false 时 reason 为本地提示文案）。 */
    data class Ready(
        val ok: Boolean,
        val reason: String,
    ) : NetplayMessage()

    /** 房主 → 加入端：开局 + 完整模拟状态快照（起点对齐，design 决策 2）。 */
    data class Start(val state: ByteArray) : NetplayMessage() {
        override fun equals(other: Any?) = other is Start && state.contentEquals(other.state)
        override fun hashCode() = state.contentHashCode()
    }

    /** 加入端 → 房主：本机游戏就绪（加载完快照），房主收到后才进入帧循环。 */
    data object GameReady : NetplayMessage()

    data object Leave : NetplayMessage()

    /**
     * 房主 → 加入端，每模拟帧一条：帧号 + 四席位输入掩码（普通双人游戏
     * P3/P4 恒为 0）；帧号每 [DESYNC_CHECK_PERIOD] 整除时载荷尾部追加该帧视频 CRC32。
     */
    data class Input(
        val frame: Int,
        val p1Mask: Int,
        val p2Mask: Int,
        val p3Mask: Int,
        val p4Mask: Int,
        val videoCrc32: Long?,
    ) : NetplayMessage()

    /** 加入端 → 房主：本机席位的掩码，变化即发，另作 2s 级保活。 */
    data class ClientInput(val mask: Int) : NetplayMessage()

    /** 双向心跳（房间长驻保活）：房间/大厅阶段周期互发，对局阶段停发。 */
    data object Ping : NetplayMessage()

    data object Pong : NetplayMessage()
}

object NetplayCodec {

    fun encode(msg: NetplayMessage): ByteArray {
        val body = encodeBody(msg)
        val frame = ByteArray(4 + body.remaining())
        val fb = ByteBuffer.wrap(frame)
        fb.putInt(body.remaining())
        fb.put(body)
        return frame
    }

    private fun encodeBody(msg: NetplayMessage): ByteBuffer {
        val buf = ByteBuffer.allocate(1024 * 1024)
        when (msg) {
            is NetplayMessage.Hello -> {
                buf.put(TYPE_HELLO).putShort(msg.protocolVersion.toShort())
                putString(buf, msg.appVersion)
                putString(buf, msg.nickname)
            }
            is NetplayMessage.Welcome -> {
                buf.put(TYPE_WELCOME)
                putString(buf, msg.hostNickname)
                buf.put(msg.seat.ordinal.toByte()).putShort(msg.capacity.toShort())
            }
            is NetplayMessage.Reject -> {
                buf.put(TYPE_REJECT).put(msg.reason.ordinal.toByte())
            }
            is NetplayMessage.PlayerList -> {
                buf.put(TYPE_PLAYER_LIST).put(msg.players.size.toByte())
                msg.players.forEach { p ->
                    buf.put(p.seat.ordinal.toByte())
                    putString(buf, p.nickname)
                }
            }
            is NetplayMessage.PickGame -> {
                buf.put(TYPE_PICK_GAME)
                putString(buf, msg.romName)
                buf.putLong(msg.size).putLong(msg.crc32)
            }
            is NetplayMessage.Ready -> {
                buf.put(TYPE_READY).put(if (msg.ok) 1 else 0)
                putString(buf, msg.reason)
            }
            is NetplayMessage.Start -> {
                buf.put(TYPE_START).putInt(msg.state.size).put(msg.state)
            }
            is NetplayMessage.GameReady -> buf.put(TYPE_GAME_READY)
            is NetplayMessage.Leave -> buf.put(TYPE_LEAVE)
            is NetplayMessage.Input -> {
                buf.put(TYPE_INPUT).putInt(msg.frame).putShort(msg.p1Mask.toShort())
                    .putShort(msg.p2Mask.toShort())
                    .putShort(msg.p3Mask.toShort())
                    .putShort(msg.p4Mask.toShort())
                if (isChecksumFrame(msg.frame)) buf.putLong(msg.videoCrc32 ?: 0L)
            }
            is NetplayMessage.ClientInput -> buf.put(TYPE_CLIENT_INPUT).putShort(msg.mask.toShort())
            is NetplayMessage.Ping -> buf.put(TYPE_PING)
            is NetplayMessage.Pong -> buf.put(TYPE_PONG)
        }
        return buf.flip() as ByteBuffer
    }

    /** 解析单帧载荷（不含 4 字节长度前缀），帧内容非法返回 null（断链处理）。 */
    fun decodeFrame(type: Byte, payload: ByteBuffer): NetplayMessage? = when (type) {
        TYPE_HELLO -> NetplayMessage.Hello(
            protocolVersion = payload.short.toInt() and 0xFFFF,
            appVersion = getString(payload),
            nickname = getString(payload),
        )
        TYPE_WELCOME -> NetplayMessage.Welcome(
            hostNickname = getString(payload),
            seat = Seat.entries.getOrNull(payload.get().toInt()) ?: Seat.P2,
            capacity = payload.short.toInt() and 0xFFFF,
        )
        TYPE_REJECT -> RejectReason.entries.getOrNull(payload.get().toInt())?.let {
            NetplayMessage.Reject(it)
        }
        TYPE_PLAYER_LIST -> {
            val n = payload.get().toInt() and 0xFF
            val players = ArrayList<PlayerInfo>(n)
            repeat(n) {
                val seat = Seat.entries.getOrNull(payload.get().toInt()) ?: Seat.P2
                players.add(PlayerInfo(seat, getString(payload)))
            }
            NetplayMessage.PlayerList(players)
        }
        TYPE_PICK_GAME -> NetplayMessage.PickGame(
            romName = getString(payload),
            size = payload.long,
            crc32 = payload.long,
        )
        TYPE_READY -> NetplayMessage.Ready(
            ok = payload.get().toInt() != 0,
            reason = getString(payload),
        )
        TYPE_START -> {
            val n = payload.int
            if (n < 0 || n != payload.remaining()) {
                null
            } else {
                ByteArray(n).also { payload.get(it) }.let(NetplayMessage::Start)
            }
        }
        TYPE_GAME_READY -> NetplayMessage.GameReady
        TYPE_LEAVE -> NetplayMessage.Leave
        TYPE_INPUT -> {
            val frame = payload.int
            NetplayMessage.Input(
                frame = frame,
                p1Mask = payload.short.toInt() and 0xFFFF,
                p2Mask = payload.short.toInt() and 0xFFFF,
                p3Mask = payload.short.toInt() and 0xFFFF,
                p4Mask = payload.short.toInt() and 0xFFFF,
                videoCrc32 = if (isChecksumFrame(frame)) {
                    payload.long.toLong() and 0xFFFFFFFFL
                } else {
                    null
                },
            )
        }
        TYPE_CLIENT_INPUT -> NetplayMessage.ClientInput(payload.short.toInt() and 0xFFFF)
        TYPE_PING -> NetplayMessage.Ping
        TYPE_PONG -> NetplayMessage.Pong
        else -> null
    }

    fun isChecksumFrame(frame: Int): Boolean = frame % DESYNC_CHECK_PERIOD == 0

    private const val TYPE_HELLO: Byte = 1
    private const val TYPE_WELCOME: Byte = 2
    private const val TYPE_REJECT: Byte = 3
    private const val TYPE_PLAYER_LIST: Byte = 4
    private const val TYPE_PICK_GAME: Byte = 5
    private const val TYPE_READY: Byte = 6
    private const val TYPE_START: Byte = 7
    private const val TYPE_GAME_READY: Byte = 8
    private const val TYPE_LEAVE: Byte = 9
    private const val TYPE_INPUT: Byte = 10
    private const val TYPE_CLIENT_INPUT: Byte = 11
    private const val TYPE_PING: Byte = 12
    private const val TYPE_PONG: Byte = 13

    private fun putString(buf: ByteBuffer, s: String) {
        val bytes = s.encodeToByteArray()
        check(bytes.size <= Char.MAX_VALUE.code) { "字符串超长" }
        buf.putShort(bytes.size.toShort()).put(bytes)
    }

    private fun getString(buf: ByteBuffer): String {
        val n = buf.short.toInt() and 0xFFFF
        require(n <= buf.remaining()) { "字符串长度越界" }
        return ByteArray(n).also { buf.get(it) }.decodeToString()
    }
}

/** 视频帧 CRC32（取 w×h 有效区域；videoBuffer 实际容量大于一帧）。 */
fun crc32OfVideo(pixels: IntArray, width: Int, height: Int): Long {
    val bytes = ByteArray(width * height * 4)
    ByteBuffer.wrap(bytes).asIntBuffer().put(pixels, 0, width * height)
    val crc = CRC32()
    crc.update(bytes)
    return crc.value
}

/**
 * TCP 流 → 消息：跨读积累、按长度前缀切帧。协议错误（超长/非法）抛
 * [IOException]，由链路层归一为断线。
 */
class FrameAssembler {

    private var buf = ByteArray(64 * 1024)
    private var len = 0

    fun feed(data: ByteArray, count: Int, out: MutableList<NetplayMessage>) {
        if (len + count > buf.size) {
            buf = buf.copyOf(maxOf(len + count, buf.size * 2))
        }
        System.arraycopy(data, 0, buf, len, count)
        len += count

        var pos = 0
        while (len - pos >= 4) {
            val n = ((buf[pos].toInt() and 0xFF) shl 24) or
                ((buf[pos + 1].toInt() and 0xFF) shl 16) or
                ((buf[pos + 2].toInt() and 0xFF) shl 8) or
                (buf[pos + 3].toInt() and 0xFF)
            if (n < 1 || n > MAX_FRAME_BYTES) throw IOException("协议错误：帧长 $n")
            if (len - pos - 4 < n) break
            val msg = NetplayCodec.decodeFrame(buf[pos + 4], ByteBuffer.wrap(buf, pos + 5, n - 1))
            if (msg == null) throw IOException("协议错误：未知帧类型")
            out.add(msg)
            pos += 4 + n
        }
        if (pos > 0) {
            System.arraycopy(buf, pos, buf, 0, len - pos)
            len -= pos
        }
    }
}

/** 输入掩码位操作：位布局见文件头，diff 出按下/抬起的按钮。 */
object ButtonMask {

    fun bit(button: RetroButton): Int = when (button) {
        RetroButton.B -> 1 shl 0
        RetroButton.SELECT -> 1 shl 2
        RetroButton.START -> 1 shl 3
        RetroButton.UP -> 1 shl 4
        RetroButton.DOWN -> 1 shl 5
        RetroButton.LEFT -> 1 shl 6
        RetroButton.RIGHT -> 1 shl 7
        RetroButton.A -> 1 shl 8
    }

    inline fun diff(prev: Int, next: Int, onDown: (RetroButton) -> Unit, onUp: (RetroButton) -> Unit) {
        val changed = prev xor next
        RetroButton.entries.forEach { b ->
            val mask = bit(b)
            if (changed and mask != 0) {
                if (next and mask != 0) onDown(b) else onUp(b)
            }
        }
    }
}
