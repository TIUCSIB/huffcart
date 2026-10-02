package com.huffcart.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * 封面统一组件（game-cover-art 任务 1.2，三级解析）：
 * 本地文件（导入/联网落盘，其次运行截图）同步解码 → 两者皆无则后台联网抓取（CoverStore
 * 内存去重）→ 均无渲染确定性占位图。[version] 供「更换封面」后强制重新解析（换 key 即重读磁盘）。
 */
@Composable
fun CoverImage(
    gameName: String,
    version: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(gameName, version) {
        mutableStateOf(CoverStore.decodeDisplayCover(context, gameName))
    }
    LaunchedEffect(gameName, version) {
        if (bitmap == null && CoverStore.ensureCover(context, gameName)) {
            bitmap = CoverStore.decodeDisplayCover(context, gameName)
        }
    }
    val resolved = bitmap
    if (resolved != null) {
        Image(
            bitmap = resolved.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        PlaceholderCover(gameName = gameName, modifier = modifier)
    }
}
