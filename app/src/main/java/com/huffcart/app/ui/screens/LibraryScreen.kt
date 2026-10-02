package com.huffcart.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.LibraryState
import com.huffcart.app.ui.library.RomLibrary
import kotlinx.coroutines.launch

/**
 * 游戏库 tab（game-library「游戏库列表视图」）：行式列表（缩略封面 + 名称 + FC 角标），
 * chips / 搜索 / 导入入口与首页一致，数据同源。
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onOpenDetail: (String) -> Unit,
    onOpenCategories: () -> Unit,
) {
    val context = LocalContext.current
    var searchActive by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val imported = RomLibrary.importRom(context, uri)) {
            is RomLibrary.ImportResult.Ok -> state.refresh(context)
            is RomLibrary.ImportResult.Invalid -> scope.launch { snackbar.showSnackbar("不是有效的 FC ROM") }
            is RomLibrary.ImportResult.Error -> scope.launch { snackbar.showSnackbar("导入失败：${imported.reason}") }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "游戏库",
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
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("导入 ROM") },
                                onClick = {
                                    menuOpen = false
                                    picker.launch(arrayOf("application/octet-stream"))
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
                    onImport = { picker.launch(arrayOf("application/octet-stream")) },
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
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.filtered, key = { it.name }) { rom ->
                        GameListRow(rom = rom, coverEpoch = state.coverEpoch, onClick = { onOpenDetail(rom.name) })
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline,
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
}
