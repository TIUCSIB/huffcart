package com.huffcart.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.CoverImage
import com.huffcart.app.ui.library.CoverStore
import com.huffcart.app.ui.library.GenreCatalog
import com.huffcart.app.ui.library.LibraryState
import com.huffcart.app.ui.theme.HcChipBg
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcRed
import java.io.File
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.huffcart.app.R
import com.huffcart.app.ui.game.RomPlatform

/** 游戏详情页（game-library）：hero 封面 + 标签 + 简介/文件信息 + 开始游戏 + 菜单移除。 */
@Composable
fun DetailScreen(
    romName: String,
    state: LibraryState,
    onBack: () -> Unit,
    onStartGame: () -> Unit,
    onRemoved: () -> Unit,
) {
    val context = LocalContext.current
    val display = remember(romName) {
        romName.substringBeforeLast('.', romName)
    }
    val entry = remember(romName) { GenreCatalog.lookup(romName) }
    val platform = remember(romName) { RomPlatform.fromExtension(romName) ?: RomPlatform.FC }
    val romFile = remember(romName) {
        File(context.filesDir, "roms").resolve(romName)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && CoverStore.importCover(context, display, uri)) {
            state.coverEpoch++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部横条与首页同构（AppTopBar：红底 + 返回 + 标题 + ⋮ 菜单；不再用云纹横幅）
        AppTopBar(
            title = display,
            onBack = onBack,
            // 长文件名用系统粗体（像素字对生僻字/长名可读性差）
            titleStyle = MaterialTheme.typography.titleLarge
                .copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold),
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(10.dp),
                        containerColor = Color.White,
                        border = BorderStroke(1.dp, HcOutlineLight),
                    ) {
                        DropdownMenuItem(
                            text = { Text("更换封面") },
                            onClick = {
                                menuOpen = false
                                coverPicker.launch(arrayOf("image/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("移除游戏", color = HcRed) },
                            onClick = {
                                menuOpen = false
                                confirmRemove = true
                            },
                        )
                    }
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 230.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                CoverImage(gameName = display, version = state.coverEpoch, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 游戏名是内容标题（非页面标题），用系统粗体保证长名可读（页面顶栏仍为像素字）
            Text(
                text = display,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Default),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip(text = entry?.genre?.label ?: "未分类")
                TagChip(text = platform.label)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (entry?.summary != null) {
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Text(
                    text = "文件大小：${romFile.length() / 1024} KB\n格式：${platform.formatText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            // 素材按钮的按压质感：整体下沉 3dp + SrcAtop 变暗（只染按钮像素，透明边距不变灰，无 ripple）
            var ctaPressed by remember { mutableStateOf(false) }
            val ctaInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(335f / 90f)
                    .offset(y = if (ctaPressed) 3.dp else 0.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            ctaPressed = true
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break
                            }
                            ctaPressed = false
                        }
                    }
                    .clickable(
                        interactionSource = ctaInteraction,
                        indication = null,
                        onClick = onStartGame,
                    ),
            ) {
                Image(
                    bitmap = ImageBitmap.imageResource(R.drawable.asset_btn_start),
                    contentDescription = "开始游戏",
                    modifier = Modifier.matchParentSize(),
                    filterQuality = FilterQuality.None,
                    colorFilter = if (ctaPressed) {
                        ColorFilter.tint(Color.Black.copy(alpha = 0.18f), BlendMode.SrcAtop)
                    } else {
                        null
                    },
                )
            }
        }
    }
    if (confirmRemove) {
        RemoveGameDialog(
            gameName = display,
            onConfirm = {
                state.remove(context, romFile)
                confirmRemove = false
                onRemoved()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = HcChipBg,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
