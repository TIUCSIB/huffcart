package com.huffcart.app.ui.library

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** ROM 导入（zip-import-and-release-hygiene 任务 1.2/1.3）：zip 分支五类输入 + 裸文件回归。 */
class RomLibraryImportTest {

    private fun tempDir(): File = Files.createTempDirectory("romimport").toFile()

    private fun validRom(size: Int = 32): ByteArray =
        byteArrayOf('N'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 0x1A.toByte()) +
            ByteArray(size - 4) { (it % 251).toByte() }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun import(dir: File, name: String, bytes: ByteArray): RomLibrary.ImportResult =
        RomLibrary.importStream(dir, name, ByteArrayInputStream(bytes))

    private fun nesFiles(dir: File): List<String> =
        dir.listFiles()?.map { it.name }?.sorted() ?: emptyList()

    @Test
    fun zipWithSingleRomImportsIt() {
        val dir = tempDir()
        val result = import(dir, "合集.zip", zipBytes("超级玛丽 (J).nes" to validRom()))
        assertIs<RomLibrary.ImportResult.Ok>(result)
        assertEquals(1, result.imported)
        // 括号不在文件名字符集内，沿用裸文件导入的既有清洗规则（与显示名路径一致）
        assertEquals(listOf("超级玛丽 _J_.nes"), nesFiles(dir))
    }

    @Test
    fun zipWithMultipleRomsImportsAllSkippingJunkAndSubdirs() {
        val dir = tempDir()
        val bytes = zipBytes(
            "roms/魂斗罗.nes" to validRom(),
            "说明.txt" to "不是游戏".toByteArray(),
            "坦克大战.nes" to validRom(),
            "封面.png" to byteArrayOf(0x89.toByte(), 0x50),
        )
        val result = import(dir, "合集.zip", bytes)
        assertIs<RomLibrary.ImportResult.Ok>(result)
        assertEquals(2, result.imported)
        assertEquals(listOf("坦克大战.nes", "魂斗罗.nes"), nesFiles(dir))
    }

    @Test
    fun zipWithoutValidRomYieldsInvalidAndNoEntries() {
        val dir = tempDir()
        val bytes = zipBytes(
            "伪ROM.nes" to "缺少 iNES 头".toByteArray(),
            "文档.txt" to "说明".toByteArray(),
        )
        val result = import(dir, "合集.zip", bytes)
        assertIs<RomLibrary.ImportResult.Invalid>(result)
        assertEquals("压缩包内没有有效的 FC ROM", result.message)
        assertTrue(nesFiles(dir).isEmpty())
    }

    @Test
    fun corruptZipRollsBackWrittenFiles() {
        val dir = tempDir()
        // ZipInputStream 读到中央目录即止，追加在 zip 尾部的坏字节走不到；
        // 需在 zip 内部破坏第二个条目的压缩数据：第一个条目照常入库，随后异常 → 回滚
        val good = zipBytes(
            "游戏.nes" to validRom(),
            "坦克大战.nes" to validRom(),
        ).toMutableList()
        val lastLoc = good.indices.filter { i ->
            i + 4 <= good.size &&
                good[i] == 0x50.toByte() && good[i + 1] == 0x4B.toByte() &&
                good[i + 2] == 0x03.toByte() && good[i + 3] == 0x04.toByte()
        }.last()
        val nameLen = (good[lastLoc + 26].toInt() and 0xFF) or ((good[lastLoc + 27].toInt() and 0xFF) shl 8)
        val extraLen = (good[lastLoc + 28].toInt() and 0xFF) or ((good[lastLoc + 29].toInt() and 0xFF) shl 8)
        val dataIdx = lastLoc + 30 + nameLen + extraLen + 2
        good[dataIdx] = (good[dataIdx].toInt() xor 0x55).toByte()
        val result = import(dir, "合集.zip", good.toByteArray())
        assertIs<RomLibrary.ImportResult.Error>(result)
        assertTrue(nesFiles(dir).isEmpty(), "损坏 zip 不得残留已写文件")
    }

    @Test
    fun oversizedEntryIsSkippedOthersImported() {
        val dir = tempDir()
        val bytes = zipBytes(
            "炸弹.nes" to ByteArray(8 * 1024 * 1024 + 1),
            "游戏.nes" to validRom(),
        )
        val result = import(dir, "合集.zip", bytes)
        assertIs<RomLibrary.ImportResult.Ok>(result)
        assertEquals(1, result.imported)
        assertEquals(listOf("游戏.nes"), nesFiles(dir))
    }

    @Test
    fun duplicateEntryNamesCountOnce() {
        val dir = tempDir()
        val bytes = zipBytes(
            "roms/游戏.nes" to validRom(),
            "游戏.nes" to validRom(64),
        )
        val result = import(dir, "合集.zip", bytes)
        assertIs<RomLibrary.ImportResult.Ok>(result)
        assertEquals(1, result.imported)
        assertEquals(1, nesFiles(dir).size)
    }

    @Test
    fun bareNesFileStillImportsAndRejectsRegression() {
        val dir = tempDir()
        val ok = import(dir, "塞尔达.nes", validRom())
        assertIs<RomLibrary.ImportResult.Ok>(ok)
        assertEquals(1, ok.imported)
        assertEquals(listOf("塞尔达.nes"), nesFiles(dir))

        val bad = import(dir, "坏档.nes", "垃圾数据".toByteArray())
        assertIs<RomLibrary.ImportResult.Invalid>(bad)
        assertEquals("不是有效的 FC ROM", bad.message)
        assertTrue(nesFiles(dir).size == 1, "非法文件不得产生条目")
    }

    @Test
    fun bareFileWithoutSuffixStillGetsNesAppended() {
        val dir = tempDir()
        val result = import(dir, "无后缀的卡带", validRom())
        assertIs<RomLibrary.ImportResult.Ok>(result)
        assertEquals(listOf("无后缀的卡带.nes"), nesFiles(dir))
    }
}
