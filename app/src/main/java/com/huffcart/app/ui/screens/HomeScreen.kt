package com.huffcart.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huffcart.app.ui.game.RomPlatform
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.CoverImage
import com.huffcart.app.ui.library.CoverStore
import com.huffcart.app.ui.library.LibraryState
import com.huffcart.app.ui.library.RomLibrary
import com.huffcart.app.ui.theme.HcOutlineLight
import com.huffcart.app.ui.theme.HcRed
import java.io.File
import kotlinx.coroutines.launch

/**
 * 首页（game-library「封面墙网格」）：红顶栏（品牌 + FC 角标 + 搜索 + 导入菜单）
 * + 分类 chips（末尾「分类」入口）+ 2 列封面卡片（⋮ 菜单移除）。
 */
@Composable
fun HomeScreen(
    state: LibraryState,
    onOpenDetail: (String) -> Unit,
    onOpenCategories: () -> Unit,
) {
    val context = LocalContext.current
    var searchActive by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<File?>(null) }
    var coverTarget by remember { mutableStateOf<File?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val imported = RomLibrary.importRom(context, uri)) {
            is RomLibrary.ImportResult.Ok -> {
                state.refresh(context)
                if (imported.imported > 1) {
                    scope.launch { snackbar.showSnackbar("已导入 ${imported.imported} 个游戏") }
                }
            }
            is RomLibrary.ImportResult.Invalid -> scope.launch { snackbar.showSnackbar(imported.message) }
            is RomLibrary.ImportResult.Error -> scope.launch { snackbar.showSnackbar("导入失败：${imported.reason}") }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = coverTarget
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        if (CoverStore.importCover(context, target.nameWithoutExtension, uri)) {
            state.coverEpoch++
        } else {
            scope.launch { snackbar.showSnackbar("无法使用所选图片") }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "吹卡带",
                badge = true,
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) state.query = ""
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(10.dp),
                        containerColor = Color.White,
                        border = BorderStroke(1.dp, HcOutlineLight),
                    ) {
                            DropdownMenuItem(
                                text = { Text("导入 ROM") },
                                onClick = {
                                    menuOpen = false
                                    picker.launch(RomLibrary.ROM_PICKER_MIME)
                                },
                            )
                        }
                    }
                },
            )
            if (searchActive) {
                SearchField(query = state.query, onQuery = { state.query = it })
            }
            GenreChipsRow(
                selected = state.genre,
                onSelect = { state.genre = it },
                onOpenCategories = onOpenCategories,
            )
            when {
                state.roms.isEmpty() -> EmptyLibrary(
                    onImport = { picker.launch(RomLibrary.ROM_PICKER_MIME) },
                    modifier = Modifier.fillMaxSize(),
                )
                state.filtered.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (state.query.isBlank()) "该分类暂无游戏" else "没有找到匹配的游戏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.filtered, key = { it.name }) { rom ->
                        CoverCard(
                            rom = rom,
                            coverEpoch = state.coverEpoch,
                            onClick = { onOpenDetail(rom.name) },
                            onChangeCover = {
                                coverTarget = rom
                                coverPicker.launch(arrayOf("image/*"))
                            },
                            onRemove = { removeTarget = rom },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
    removeTarget?.let { target ->
        RemoveGameDialog(
            gameName = target.nameWithoutExtension,
            onConfirm = {
                state.remove(context, target)
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
        )
    }
}

/** 封面卡片（设计稿结构）：封面 → 标题行（名称 + ⋮）→ FC 标签；⋮ 承载更换封面与移除入口。 */
@Composable
private fun CoverCard(
    rom: File,
    coverEpoch: Int,
    onClick: () -> Unit,
    onChangeCover: () -> Unit,
    onRemove: () -> Unit,
) {
    val display = rom.nameWithoutExtension
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            CoverImage(gameName = display, version = coverEpoch, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = display,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
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
                            onChangeCover()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("移除游戏", color = HcRed) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
        Text(
            text = RomPlatform.fromExtension(rom.name)?.label ?: "FC",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
