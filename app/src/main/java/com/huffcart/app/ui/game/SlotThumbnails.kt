package com.huffcart.app.ui.game

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 存档槽缩略图（audio-settings-and-save-management 决策 3）：PNG 侧车异步覆盖写，
 * 读取复用 CoverStore.decodeCover（长边限采样、损坏返回 null 由调用方占位兜底）。
 */
object SlotThumbnails {

    /** 会话级落盘作用域：调用方（游戏线程/面板）不等落盘，写完自然可见。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun file(context: Context, romName: String, slot: Int): File =
        SaveSlotStore.thumbnailFile(File(context.filesDir, SaveSlotStore.DIR_NAME), romName, slot)

    /** [bitmap] 由调用方独有，本方法消费后回收；新存档覆盖旧图。 */
    fun writeAsync(context: Context, romName: String, slot: Int, bitmap: Bitmap) {
        val appContext = context.applicationContext
        scope.launch {
            val target = file(appContext, romName, slot)
            runCatching {
                target.parentFile?.mkdirs()
                target.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
            }
            bitmap.recycle()
        }
    }

    /** 槽位缩略图解码；无图或损坏返回 null（面板占位兜底，如历史单槽档）。 */
    fun decode(context: Context, romName: String, slot: Int): Bitmap? =
        com.huffcart.app.ui.library.CoverStore.decodeCover(file(context, romName, slot))
}
