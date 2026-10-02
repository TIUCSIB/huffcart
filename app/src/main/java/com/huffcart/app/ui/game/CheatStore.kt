package com.huffcart.app.ui.game

import android.content.Context
import java.io.File

/** 单条金手指：归一化后的 Game Genie 码与启用态。 */
data class CheatEntry(val code: String, val enabled: Boolean)

/**
 * 金手指存储（cheat-codes）：按游戏一行式持久化 `CODE\t0|1`，与 covers 索引缓存同惯例
 * （纯文本行格式 JVM 单测可测，不引 JSON 依赖）。校验与解析为纯逻辑，Context 胶水分离。
 */
object CheatStore {

    const val DIR_NAME = "cheats"

    /** NES Game Genie 字母表（FCEUmm 遵循标准字母表）。 */
    const val GG_ALPHABET = "APZLGITYEOXUKSVN"

    fun file(context: Context, romBase: String): File =
        File(context.filesDir, DIR_NAME).resolve("$romBase.cheats")

    /** 归一化：大写、去连字符与空白。 */
    fun normalize(raw: String): String =
        raw.trim().uppercase().replace("-", "").replace(" ", "")

    /** Game Genie 校验：归一化后 6 或 8 位、全部在 GG 字母表内。 */
    fun isValidGameGenie(raw: String): Boolean {
        val code = normalize(raw)
        return (code.length == 6 || code.length == 8) && code.all { it in GG_ALPHABET }
    }

    fun load(context: Context, romBase: String): List<CheatEntry> {
        val file = file(context, romBase)
        if (!file.isFile) return emptyList()
        return parseLines(file.readLines())
    }

    fun save(context: Context, romBase: String, entries: List<CheatEntry>) {
        val file = file(context, romBase)
        file.parentFile?.mkdirs()
        file.writeText(serializeLines(entries))
    }

    /** 行格式解析：`CODE\t1`；损坏行与非法码丢弃（自愈），合法码归一化兜底。 */
    fun parseLines(lines: List<String>): List<CheatEntry> =
        lines.mapNotNull { line ->
            val sep = line.indexOf('\t')
            if (sep <= 0) return@mapNotNull null
            val code = normalize(line.take(sep))
            if (!isValidGameGenie(code)) return@mapNotNull null
            CheatEntry(code, line.substring(sep + 1) == "1")
        }

    fun serializeLines(entries: List<CheatEntry>): String =
        entries.joinToString("\n") { "${it.code}\t${if (it.enabled) "1" else "0"}" }
}
