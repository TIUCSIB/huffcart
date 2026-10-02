package com.huffcart.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.huffcart.app.ui.AppTopBar
import com.huffcart.app.ui.library.GenreCatalog
import com.huffcart.app.ui.library.LibraryState
import com.huffcart.app.ui.theme.HcGenreAdventure
import com.huffcart.app.ui.theme.HcGenrePuzzle
import com.huffcart.app.ui.theme.HcGenreRacing
import com.huffcart.app.ui.theme.HcGenreShooter
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcYellow

private data class GenreVisual(val color: Color, val icon: ImageVector)

/** 分类卡片视觉（设计稿：彩色底 + 像素风图标；颜色为 UI 层映射，词典保持纯 JVM）。 */
private val genreVisuals: Map<GenreCatalog.Genre, GenreVisual> = mapOf(
    GenreCatalog.Genre.ACTION to GenreVisual(HcRed, Icons.Filled.Star),
    GenreCatalog.Genre.SHOOTER to GenreVisual(HcGenreShooter, Icons.Filled.Rocket),
    GenreCatalog.Genre.ADVENTURE to GenreVisual(HcGenreAdventure, Icons.Filled.Explore),
    GenreCatalog.Genre.PUZZLE to GenreVisual(HcGenrePuzzle, Icons.Filled.Extension),
    GenreCatalog.Genre.FIGHTING to GenreVisual(HcYellow, Icons.Filled.SportsMma),
    GenreCatalog.Genre.RACING to GenreVisual(HcGenreRacing, Icons.Filled.DirectionsCar),
)

/** 分类页（game-library「分类页」）：6 张彩色分类卡片，点卡片进该分类列表。 */
@Composable
fun CategoriesScreen(onBack: () -> Unit, onOpenGenre: (GenreCatalog.Genre) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = "分类", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(GenreCatalog.Genre.entries, key = { it.name }) { genre ->
                val visual = genreVisuals.getValue(genre)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(visual.color)
                        .clickable { onOpenGenre(genre) }
                        .padding(14.dp),
                ) {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .height(30.dp),
                    )
                    Text(
                        text = genre.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}

/** 分类列表页：该分类下的游戏（复用列表行），点行进详情。 */
@Composable
fun CategoryScreen(
    genre: GenreCatalog.Genre,
    state: LibraryState,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val games = remember(genre, state.roms) {
        state.roms.filter { GenreCatalog.lookup(it.name)?.genre == genre }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(title = genre.label, onBack = onBack)
        if (games.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "该分类暂无游戏",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(games, key = { it.name }) { rom ->
                    GameListRow(rom = rom, coverEpoch = state.coverEpoch, onClick = { onOpenDetail(rom.name) })
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
