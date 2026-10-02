package com.huffcart.app.ui.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** ROM 库文件操作（供首页/详情共用）。 */
object RomLibrary {

    sealed interface ImportResult {
        data object Ok : ImportResult
        data object Invalid : ImportResult
        data class Error(val reason: String) : ImportResult
    }

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

    /** SAF 导入：复制 → iNES 头校验 → 落入 roms 目录（零权限，spec「ROM 导入」）。 */
    fun importRom(context: Context, uri: Uri): ImportResult {
        return try {
            val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
            val display = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: "rom_${System.currentTimeMillis()}"
            val safe = display.replace(Regex("[^A-Za-z0-9 ._\\-\\u4e00-\\u9fff]"), "_")
                .trim()
                .ifEmpty { "rom_${System.currentTimeMillis()}" }
            val target = romsDir.resolve(if (safe.endsWith(".nes", true)) safe else "$safe.nes")

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return ImportResult.Error("无法读取所选文件")

            // iNES 头校验（spec：非法文件不得产生条目）
            val head = ByteArray(4)
            target.inputStream().use { it.read(head) }
            val valid = head[0] == 'N'.code.toByte() && head[1] == 'E'.code.toByte() &&
                head[2] == 'S'.code.toByte() && head[3] == 0x1A.toByte()
            if (valid) {
                ImportResult.Ok
            } else {
                target.delete()
                ImportResult.Invalid
            }
        } catch (t: Throwable) {
            ImportResult.Error(t.message ?: t.toString())
        }
    }
}
