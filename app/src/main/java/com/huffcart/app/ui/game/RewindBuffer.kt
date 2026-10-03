package com.huffcart.app.ui.game

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/** 倒带环形缓冲（rewind-and-session-split 决策 2）：逐采样 zlib 压缩 + 字节上限淘汰最老。
 *  纯 JVM 逻辑（无 Android 依赖）；push/pop 由游戏线程独占调用，无并发要求。 */
internal class RewindBuffer(private val maxBytes: Int = DEFAULT_MAX_BYTES) {

    private val samples = ArrayDeque<ByteArray>()
    private var bytes = 0

    /** 当前采样数（观测用）。 */
    val size: Int get() = samples.size

    /** 采样入队：压缩后加入最新端；超出上限淘汰最老采样（至少保留最新一条）。 */
    fun push(raw: ByteArray) {
        val compressed = deflate(raw) ?: return
        samples.addLast(compressed)
        bytes += compressed.size
        while (bytes > maxBytes && samples.size > 1) {
            bytes -= samples.removeFirst().size
        }
    }

    /** 弹出最新采样（解压返回）；缓冲空或数据损坏返回 null。 */
    fun pop(): ByteArray? {
        val compressed = samples.removeLastOrNull() ?: return null
        bytes -= compressed.size
        return inflate(compressed)
    }

    /** 清空（时间线切换：读档/续玩恢复后调用，禁止跨时间线回退）。 */
    fun clear() {
        samples.clear()
        bytes = 0
    }

    private fun deflate(raw: ByteArray): ByteArray? = try {
        val deflater = Deflater()
        try {
            deflater.setInput(raw)
            deflater.finish()
            val out = ByteArrayOutputStream(raw.size / 2)
            val buf = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                out.write(buf, 0, deflater.deflate(buf))
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    } catch (t: Throwable) {
        null
    }

    private fun inflate(data: ByteArray): ByteArray? = try {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 2)
            val buf = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    } catch (t: Throwable) {
        null
    }

    companion object {
        /** 30 秒 @20 采样/s = 600 条 NES 压缩状态（约 30–60KB/条）的宽松上限（决策 2）。 */
        const val DEFAULT_MAX_BYTES = 24 * 1024 * 1024
    }
}
