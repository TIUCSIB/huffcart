package com.huffcart.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// 游戏页容器占位：libretro-playback change 将在此接入 SurfaceView（256×240 帧
// 整数缩放上屏）与 CRT 扫描线效果——AGSL RuntimeShader 需 API 33+，低版本回退
// 半透明扫描线叠加图，接入时以 Build.VERSION.SDK_INT 做特性检测。
@Composable
fun GamePlaceholderScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "游戏画面占位",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "核心播放与画面容器将在 libretro-playback 接入",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onBack) {
            Text(text = "返回")
        }
    }
}
