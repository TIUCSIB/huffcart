package com.huffcart.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import com.huffcart.app.R
import com.huffcart.app.ui.HideSystemNavigationBars
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcRedDark
import kotlinx.coroutines.delay

/**
 * 品牌启动页（app-shell「品牌启动页」，ui-asset-integration 改版）：
 * 红底全屏 + 素材库切片美术（logo / 副标徽章 / 主机插画 / 云纹横幅条），
 * 短暂展示后自动进入主界面；无网络依赖。
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    HideSystemNavigationBars()
    LaunchedEffect(Unit) {
        delay(1200)
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HcRedDark),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.asset_logo_lockup),
                contentDescription = null,
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(475f / 237f),
                filterQuality = FilterQuality.None,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.asset_console),
                contentDescription = null,
                modifier = Modifier
                    .width(196.dp)
                    .aspectRatio(290f / 182f),
                filterQuality = FilterQuality.None,
            )
        }
        Text(
            text = "—— 按下开始键，回到童年 ——",
            style = MaterialTheme.typography.bodyMedium,
            color = HcCream.copy(alpha = 0.75f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
        )
        Image(
            bitmap = ImageBitmap.imageResource(R.drawable.asset_banner_red),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .aspectRatio(1487f / 54f),
            contentScale = ContentScale.FillWidth,
            filterQuality = FilterQuality.None,
        )
    }
}
