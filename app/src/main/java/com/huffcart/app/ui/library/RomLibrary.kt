package com.huffcart.app.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** ROM 库文件操作（供首页/详情共用）。 */
object RomLibrary {

    sealed interface ImportResult {
        /** imported：本次实际入库条数（zip 可 >1，spec「批量导入多 ROM 压缩包」）。 */
        data class Ok(val imported: Int) : ImportResult
        data class Invalid(val message: String) : ImportResult
        data class Error(val reason: String) : ImportResult
    }

    /** 单条目解压上限：NES ROM 实际上限约 1MB，超限视为异常条目跳过（zip 炸弹防御）。 */
    private const val MAX_ENTRY_BYTES = 8L * 1024 * 1024

    /** 文件选择器 MIME：zip 两式 + octet-stream 兜底厂商把 zip 标为未知类型（spec「ROM 导入」）。 */
    val ROM_PICKER_MIME = arrayOf(
        "application/octet-stream",
        "application/zip",
        "application/x-zip-compressed",
    )

    fun listRoms(context: Context): List<File> =
        File(context.filesDir, "roms")
            .apply { mkdirs() }
            .listFiles { f -> f.isFile && f.extension.lowercase() == "nes" }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    /** 移除游戏（spec「移除游戏」）：删 ROM 本体 + romsaves 下该游戏前缀的全部存档。 */
    fun removeRom(context: Context, rom: File) {
        rom.delete()
        val stem = rom.name.removeSuffix(".nes")
        File(context.filesDir, "romsaves")
            .listFiles { f -> f.isFile && f.name.startsWith(stem) }
            ?.forEach { it.delete() }
    }

    /** SAF 导入：按所选文件名分派裸 .nes / zip 压缩包分支（spec「ROM 导入」，零权限）。 */
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

    /** 纯逻辑导入（JVM 可测）：文件名分派 → 单文件 / zip 压缩包。 */
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

    /** iNES 头校验：`NES\x1a`（spec「ROM 导入」）。 */
    fun isINes(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 'N'.code.toByte() && head[1] == 'E'.code.toByte() &&
            head[2] == 'S'.code.toByte() && head[3] == 0x1A.toByte()

    private fun sanitize(display: String): String =
        display.replace(Regex("[^A-Za-z0-9 ._\\-\\u4e00-\\u9fff]"), "_")
            .trim()
            .ifEmpty { "rom_${System.currentTimeMillis()}" }

    private fun importSingle(romsDir: File, safe: String, input: InputStream): ImportResult {
        val target = romsDir.resolve(if (safe.endsWith(".nes", true)) safe else "$safe.nes")
        input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
        val head = ByteArray(4)
        target.inputStream().use { it.read(head) }
        return if (isINes(head)) {
            ImportResult.Ok(1)
        } else {
            target.delete()
            ImportResult.Invalid("不是有效的 FC ROM")
        }
    }

    /** zip 分支：流式遍历，合法 .nes 全部入库；损坏则回滚本次已写文件（spec：损坏 → 不新增条目）。 */
    private fun importZip(romsDir: File, input: InputStream): ImportResult {
        val written = mutableListOf<File>()
        var imported = 0
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                val seen = HashSet<String>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".nes", true)) {
                        val bytes = readBounded(zip)
                        val base = sanitize(entry.name.substringAfterLast('/').substringAfterLast('\\'))
                        if (bytes != null && isINes(bytes) && base.isNotEmpty() && seen.add(base)) {
                            val target = romsDir.resolve(if (base.endsWith(".nes", true)) base else "$base.nes")
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
            return ImportResult.Error("压缩包已损坏或无法读取：${t.message ?: t.toString()}")
        }
        return if (imported > 0) {
            ImportResult.Ok(imported)
        } else {
            ImportResult.Invalid("压缩包内没有有效的 FC ROM")
        }
    }

    /** 读至多 MAX_ENTRY_BYTES 字节，超限返回 null（调用方跳过该条目）。 */
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
}
