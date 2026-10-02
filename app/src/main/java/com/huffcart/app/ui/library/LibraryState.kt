package com.huffcart.app.ui.library

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * 库共享状态（retro-ui-redesign 决策 5）：首页网格 / 游戏库列表 / 分类列表三屏共用，
 * 保证移除游戏后所有呈现面同步刷新。
 */
class LibraryState {

    var roms by mutableStateOf<List<File>>(emptyList())
        private set
    var query by mutableStateOf("")
    var genre by mutableStateOf<GenreCatalog.Genre?>(null)

    /** 封面纪元：导入封面后自增，驱动各屏 [CoverImage] 按新 key 重读磁盘（game-cover-art 2.1）。 */
    var coverEpoch by mutableStateOf(0)

    val filtered: List<File>
        get() = filterRoms(roms, query, genre)

    /** 预抓取进程内只发一次：封面落盘持久，后续 refresh（导入/移除后）无需重跑。 */
    private var prefetchStarted = false

    fun refresh(context: Context) {
        roms = RomLibrary.listRoms(context)
        if (roms.isNotEmpty() && !prefetchStarted) {
            prefetchStarted = true
            CoverStore.prefetchAll(context, roms.map { it.nameWithoutExtension })
        }
    }

    fun remove(context: Context, rom: File) {
        RomLibrary.removeRom(context, rom)
        refresh(context)
    }

    companion object {
        /** 纯过滤逻辑：关键字（忽略大小写包含）× 分类叠加；未命中词典的游戏仅归「全部」。 */
        fun filterRoms(roms: List<File>, query: String, genre: GenreCatalog.Genre?): List<File> =
            roms.filter { file ->
                val queryHit = query.isBlank() ||
                    file.nameWithoutExtension.contains(query.trim(), ignoreCase = true)
                val genreHit = genre == null || GenreCatalog.lookup(file.name)?.genre == genre
                queryHit && genreHit
            }
    }
}

/** 活跃期持有（Activity 因 configChanges 不重建，旋转不丢）；进程重启后重扫磁盘。 */
@Composable
fun rememberLibraryState(): LibraryState {
    val state = remember { LibraryState() }
    val context = LocalContext.current
    LaunchedEffect(state) { state.refresh(context) }
    return state
}
