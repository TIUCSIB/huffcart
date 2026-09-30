package com.huffcart.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch

/** 游戏库：ROM 目录列表 + SAF 导入（spec「ROM 导入」；完整封面墙属后续 change）。 */
@Composable
fun LibraryScreen(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    var roms by remember { mutableStateOf(listRoms(context)) }
    var message by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(text: String) {
        message = null
        scope.launch { snackbar.showSnackbar(text) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val imported = importRom(context, uri)) {
            is ImportResult.Ok -> {
                roms = listRoms(context)
            }
            is ImportResult.Invalid -> notify("不是有效的 FC ROM")
            is ImportResult.Error -> notify("导入失败：${imported.reason}")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "游戏库",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (roms.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "还没有游戏。把 .nes ROM 文件放到设备存储后，\n在这里导入即可开始游玩。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { picker.launch(arrayOf("application/octet-stream")) },
                    ) {
                        Text(text = "导入 ROM")
                    }
                    Spacer(modifier = Modifier.height(96.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(roms, key = { it.name }) { rom ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlay(rom.name) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = rom.nameWithoutExtension,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "${rom.length() / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Button(
                                onClick = { picker.launch(arrayOf("application/octet-stream")) },
                            ) {
                                Text(text = "导入更多 ROM")
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
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

private fun listRoms(context: Context): List<File> =
    File(context.filesDir, "roms")
        .apply { mkdirs() }
        .listFiles { f -> f.isFile && f.extension.lowercase() == "nes" }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

private sealed interface ImportResult {
    data object Ok : ImportResult
    data object Invalid : ImportResult
    data class Error(val reason: String) : ImportResult
}

/** SAF 导入：复制 → iNES 头校验 → 落入 roms 目录（零权限，spec「ROM 导入」）。 */
private fun importRom(context: Context, uri: Uri): ImportResult {
    return try {
        val romsDir = File(context.filesDir, "roms").apply { mkdirs() }
        val display = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: "rom_${System.currentTimeMillis()}"
        val safe = display.replace(Regex("[^A-Za-z0-9 ._\\-\\u4e00-\\u9fff]"), "_")
            .trim()
            .ifEmpty { "rom_${System.currentTimeMillis()}" }
        val target = romsDir.resolve(if (safe.endsWith(".nes", true)) safe else "$safe.nes")

        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return ImportResult.Error("无法读取所选文件")

        // iNES 头校验（spec：非法文件不得产生条目）
        val head = ByteArray(4)
        target.inputStream().use { it.read(head) }
        val valid = head[0] == 'N'.code.toByte() && head[1] == 'E'.code.toByte() &&
            head[2] == 'S'.code.toByte() && head[3] == 0x1A.toByte()
        if (valid) {
            ImportResult.Ok
        } else {
            target.delete()
            ImportResult.Invalid
        }
    } catch (t: Throwable) {
        ImportResult.Error(t.message ?: t.toString())
    }
}
