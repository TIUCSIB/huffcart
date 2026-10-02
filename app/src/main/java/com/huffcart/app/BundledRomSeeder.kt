package com.huffcart.app

import android.content.Context
import java.io.File
import java.io.IOException

/**
 * 内置游戏播种（netplay-lan「内置游戏播种」）：首次启动把 assets/roms 的
 * 内置 ROM 复制进游戏库目录（filesDir/roms），沿用 iNES 头校验与同名跳过。
 * 播种一次性执行（标记位），用户移除内置游戏后不回填（与「移除游戏」需求自洽）。
 *
 * 同步执行于冷启动（FC ROM 体量小，一次性成本可接受）；标记位在全部成功后
 * 落盘，进程中断会在下次启动补播（已存在文件按同名跳过，天然幂等）。
 */
object BundledRomSeeder {

    private const val PREFS = "bundled_roms"
    private const val KEY_SEEDED = "seeded"

    fun seedIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return

        val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
        val names = runCatching { context.assets.list("roms") }.getOrNull()
        if (names.isNullOrEmpty()) {
            // 无内置资产（未执行 sync 脚本的构建）也算完成，避免每次启动重扫
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return
        }

        var failed = 0
        for (name in names) {
            if (!name.endsWith(".nes", ignoreCase = true)) continue
            val target = romsDir.resolve(name)
            if (target.exists()) continue // 同名跳过（含用户自导入的同款）
            val tmp = romsDir.resolve(".$name.seeding")
            try {
                context.assets.open("roms/$name").use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (isValidINes(tmp)) {
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        failed++
                    }
                } else {
                    tmp.delete() // 非法文件不产生库条目（spec）
                }
            } catch (_: IOException) {
                tmp.delete()
                failed++
            }
        }

        if (failed == 0) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }

    private fun isValidINes(file: File): Boolean {
        if (file.length() < 16) return false
        val head = ByteArray(4)
        file.inputStream().use { it.read(head) }
        return head[0] == 'N'.code.toByte() && head[1] == 'E'.code.toByte() &&
            head[2] == 'S'.code.toByte() && head[3] == 0x1A.toByte()
    }
}
