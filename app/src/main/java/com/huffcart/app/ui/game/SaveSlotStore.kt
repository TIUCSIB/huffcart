package com.huffcart.app.ui.game

import java.io.File

/** 单个存档槽的元数据：槽号（0 基内部索引，UI 展示 +1）与最近保存时间（状态文件 mtime）。 */
data class SlotMeta(val slot: Int, val savedAt: Long)

/**
 * 存档槽文件布局（audio-settings-and-save-management 决策 3）：
 *
 * ```
 * files/romsaves/
 *   <romName>.state0        # 槽 1——历史单槽文件原地沿用，零迁移
 *   <romName>.state0.png    # 缩略图侧车（可选，缺图占位兜底）
 *   <romName>.state1 ... .state3
 * ```
 *
 * 纯文件逻辑，不 import Android 类——JVM 单测直接覆盖（缩略图位图操作见 SlotThumbnails）。
 */
object SaveSlotStore {

    const val SLOT_COUNT = 4
    const val DIR_NAME = "romsaves"

    private val slotFileRegex = Regex("^(.+)\\.state([0-${SLOT_COUNT - 1}])$")

    /** 库文件名 → 存档文件基名（gb-gbc-platform 决策 3）：
     *  FC 沿用历史 .nes 剥离约定（零迁移）；GB/GBC 保留完整文件名——跨平台同名不串档。 */
    fun baseName(romName: String): String = if (romName.endsWith(".nes")) romName.removeSuffix(".nes") else romName

    fun stateFile(savesDir: File, romName: String, slot: Int): File =
        savesDir.resolve("${baseName(romName)}.state$slot")

    fun thumbnailFile(savesDir: File, romName: String, slot: Int): File =
        savesDir.resolve("${baseName(romName)}.state$slot.png")

    /** 四个槽的元数据（长度恒为 SLOT_COUNT，空槽为 null）。 */
    fun slots(savesDir: File, romName: String): List<SlotMeta?> =
        (0 until SLOT_COUNT).map { slot ->
            val file = stateFile(savesDir, romName, slot)
            if (file.isFile) SlotMeta(slot, file.lastModified()) else null
        }

    /** 状态文件同步写入（量级为百 KB 内，沿用原单槽在游戏线程直写的做法）。 */
    fun writeState(savesDir: File, romName: String, slot: Int, data: ByteArray) {
        val file = stateFile(savesDir, romName, slot)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
    }

    /** 删除一个槽：状态文件与缩略图侧车一并移除；其他槽与 SRAM 不受影响。 */
    fun deleteSlot(savesDir: File, romName: String, slot: Int) {
        stateFile(savesDir, romName, slot).delete()
        thumbnailFile(savesDir, romName, slot).delete()
    }

    /** 拥有任意存档槽的游戏基名列表，按名称排序（存档管理页列表）。 */
    fun gamesWithSlots(savesDir: File): List<String> =
        savesDir.listFiles()
            ?.mapNotNull { file -> slotFileRegex.find(file.name)?.groupValues?.get(1) }
            ?.distinct()
            ?.sorted()
            .orEmpty()
}
