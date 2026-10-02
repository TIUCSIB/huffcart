package com.huffcart.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huffcart.app.ui.library.CoverImage
import com.huffcart.app.ui.library.GenreCatalog
import com.huffcart.app.ui.theme.HcChipBg
import com.huffcart.app.ui.theme.HcRed
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.huffcart.app.R

/** 全 app 封面图统一比例：FC 原生 256:240（首页卡片 / 游戏库列表缩略 / 详情 hero）。 */
val CoverAspect = 256f / 240f

/** 分类 chips 行：全部 + 6 分类，末尾「分类」入口（spec「分类筛选」）。 */
@Composable
fun GenreChipsRow(
    selected: GenreCatalog.Genre?,
    onSelect: (GenreCatalog.Genre?) -> Unit,
    onOpenCategories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            GenreChip(label = "全部", selected = selected == null) { onSelect(null) }
        }
        items(GenreCatalog.Genre.entries.size) { index ->
            val genre = GenreCatalog.Genre.entries[index]
            GenreChip(label = genre.label, selected = selected == genre) { onSelect(genre) }
        }
        item {
            GenreChip(label = "分类", selected = false, leading = {
                Icon(
                    imageVector = Icons.Filled.Category,
                    contentDescription = null,
                    tint = HcRed,
                    modifier = Modifier.height(18.dp),
                )
            }) { onOpenCategories() }
        }
    }
}

@Composable
private fun GenreChip(
    label: String,
    selected: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        leadingIcon = leading,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = HcChipBg,
            labelColor = MaterialTheme.colorScheme.onBackground,
            selectedContainerColor = HcRed,
            selectedLabelColor = Color.White,
        ),
    )
}

/** 就地搜索框（spec「名称搜索」）：关键字 × 分类叠加过滤。 */
@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("搜索游戏", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "清空",
                    modifier = Modifier.clickable { onQuery("") },
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = HcRed,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 空库引导（app-shell「游戏库空状态」）。 */
@Composable
fun EmptyLibrary(onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.asset_landscape),
            contentDescription = null,
            modifier = Modifier
                .width(230.dp)
                .aspectRatio(313f / 94f),
            filterQuality = FilterQuality.None,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有游戏。把 .nes ROM 文件放到设备存储后，\n在这里导入即可开始游玩。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onImport) {
            Text(text = "导入 ROM")
        }
    }
}

/** 移除确认（spec「移除游戏」：取消无变更，确认删 ROM 与存档）。 */
@Composable
fun RemoveGameDialog(gameName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "移除游戏") },
        text = {
            Text(text = "确定要从库中移除《$gameName》吗？\n该游戏的存档数据也会一并删除。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "移除", color = HcRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        },
    )
}

/** 列表行（游戏库 tab / 分类列表共用）：缩略封面 + 名称 + FC 角标。 */
@Composable
fun GameListRow(rom: File, coverEpoch: Int, onClick: () -> Unit) {
    val display = rom.nameWithoutExtension
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            gameName = display,
            version = coverEpoch,
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(CoverAspect)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = display,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "FC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
