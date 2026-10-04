package com.huffcart.app.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.huffcart.app.ui.game.RomPlatform
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** ROM 库文件操作(供首页/详情共用)。 */
object RomLibrary {

    /** 库收录的平台扩展名集合(gb-gbc-platform:FC + GB/GBC)。 */
    private val ROM_EXTENSIONS = setOf("nes", "gb", "gbc")

    sealed interface ImportResult {
        /** imported:本次实际入库条数(zip 可 >1,spec「批量导入多 ROM 压缩包」)。 */
        data class Ok(val imported: Int) : ImportResult
        data class Invalid(val message: String) : ImportResult
        data class Error(val reason: String) : ImportResult
    }

    /** 单条目解压上限:GB 最大 8MB(FCEUmm 约 1MB),超限视为异常条目跳过(zip 炸弹防御)。 */
    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

    /** zip 导入总上限（审计 S3）:条目数与解压总量双重封顶，防炸弹填盘。 */
    private const val MAX_ZIP_ENTRIES = 512
    private const val MAX_ZIP_TOTAL_BYTES = 512L * 1024 * 1024

    /** 头部判定所需前缀长度(GB logo 至 $134 + $143/$148 元数据)。 */
    private const val HEADER_PREFIX_BYTES = 0x150

    /** 文件选择器 MIME:zip 两式 + octet-stream 兜底厂商把 zip 标为未知类型(spec「ROM 导入」)。 */
    val ROM_PICKER_MIME = arrayOf(
        "application/octet-stream",
        "application/zip",
        "application/x-zip-compressed",
    )

    fun listRoms(context: Context): List<File> =
        File(context.filesDir, "roms")
            .apply { mkdirs() }
            .listFiles { f -> f.isFile && f.extension.lowercase() in ROM_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** 移除游戏(spec「移除游戏」):删 ROM 本体 + romsaves 下该游戏存档键前缀的全部文件。 */
    fun removeRom(context: Context, rom: File) {
        rom.delete()
        val stem = com.huffcart.app.ui.game.SaveSlotStore.baseName(rom.name)
        File(context.filesDir, "romsaves")
            .listFiles { f -> f.isFile && f.name.startsWith(stem) }
            ?.forEach { it.delete() }
    }

    /** SAF 导入:按头部判定平台(决策 1,头为唯一真源),复制 → 校验 → 落入 roms 目录(零权限)。 */
    fun importRom(context: Context, uri: Uri): ImportResult {
        val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
        val display = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: "rom_${System.currentTimeMillis()}"
        val input = context.contentResolver.openInputStream(uri)
            ?: return ImportResult.Error("无法读取所选文件")
        return importStream(romsDir, display, input)
    }

    /** 纯逻辑导入(JVM 可测):单文件 / zip 压缩包,条目校验统一走 RomPlatform 头部判定。 */
    fun importStream(romsDir: File, displayName: String, input: InputStream): ImportResult = try {
        val safe = sanitize(displayName)
        if (safe.endsWith(".zip", true)) {
            importZip(romsDir, input)
        } else {
            importSingle(romsDir, safe, input)
        }
    } catch (t: Throwable) {
        ImportResult.Error(t.message ?: t.toString())
    }

    private fun sanitize(display: String): String =
        display.replace(Regex("[^A-Za-z0-9 ._\\-\\u4e00-\\u9fff]"), "_")
            .trim()
            .ifEmpty { "rom_${System.currentTimeMillis()}" }

    private fun importSingle(romsDir: File, safe: String, input: InputStream): ImportResult {
        // 先落临时文件再按判定平台归一扩展名——扩展名与真实平台不一致会导致核心误选黑屏
        val tmp = romsDir.resolve("__import_${System.nanoTime()}")
        input.use { source -> tmp.outputStream().use { output -> source.copyTo(output) } }
        val platform = RomPlatform.headerPlatform(readHeaderPrefix(tmp))
        if (platform == null) {
            tmp.delete()
            return ImportResult.Invalid(invalidMessage(safe))
        }
        val target = romsDir.resolve(normalizeExtension(safe, platform))
        target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return ImportResult.Ok(1)
    }

    /** 文件名扩展名归一为判定平台;无扩展名则补平台扩展名。 */
    private fun normalizeExtension(name: String, platform: RomPlatform): String {
        val dot = name.lastIndexOf('.')
        val hasKnownExt = dot >= 0 && name.substring(dot + 1).lowercase() in ROM_EXTENSIONS
        return if (hasKnownExt) {
            "${name.substring(0, dot)}.${platform.ext}"
        } else {
            "$name.${platform.ext}"
        }
    }

    /** zip 分支:流式遍历,逐条目按头部判定平台;损坏则回滚本次已写文件(spec:损坏 → 不新增条目)。 */
    private fun importZip(romsDir: File, input: InputStream): ImportResult {
        val written = mutableListOf<File>()
        var imported = 0
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                val seen = HashSet<String>()
                var entries = 0
                var totalBytes = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    // 条目数封顶（审计 S3）：海量小条目亦可拖垮导入
                    if (++entries > MAX_ZIP_ENTRIES) throw IOException("压缩包条目数超限")
                    if (!entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in ROM_EXTENSIONS) {
                        val bytes = readBounded(zip)
                        val base = sanitize(entry.name.substringAfterLast('/').substringAfterLast('\\'))
                        val platform = bytes?.let { RomPlatform.headerPlatform(it) }
                        if (platform != null && base.isNotEmpty() && seen.add(base)) {
                            // 解压总量封顶（审计 S3）：超限条目按 spec 既有语义跳过，
                            // 但入库总量必须有界，防"多合法条目累计填盘"
                            totalBytes += bytes.size
                            if (totalBytes > MAX_ZIP_TOTAL_BYTES) throw IOException("解压总量超限")
                            val target = romsDir.resolve(normalizeExtension(base, platform))
                            target.outputStream().use { it.write(bytes) }
                            written += target
                            imported++
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (t: Throwable) {
            written.forEach { it.delete() }
            return ImportResult.Error("压缩包已损坏或无法读取:${t.message ?: t.toString()}")
        }
        return if (imported > 0) {
            ImportResult.Ok(imported)
        } else {
            ImportResult.Invalid("压缩包内没有有效的 ROM")
        }
    }

    /** 读至多 MAX_ENTRY_BYTES 字节,超限返回 null(调用方跳过该条目)。 */
    private fun readBounded(input: InputStream): ByteArray? {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) return buf.toByteArray()
            total += n
            if (total > MAX_ENTRY_BYTES) return null
            buf.write(chunk, 0, n)
        }
    }

    private fun readHeaderPrefix(file: File): ByteArray {
        val prefix = ByteArray(HEADER_PREFIX_BYTES)
        val read = file.inputStream().use { stream ->
            var off = 0
            while (off < prefix.size) {
                val n = stream.read(prefix, off, prefix.size - off)
                if (n < 0) break
                off += n
            }
            off
        }
        return prefix.copyOf(read)
    }

    private fun invalidMessage(fileName: String): String = when (fileName.substringAfterLast('.').lowercase()) {
        "gb", "gbc" -> "不是有效的 GB/GBC ROM"
        else -> "不是有效的 FC ROM"
    }
}

