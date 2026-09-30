package com.huffcart.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huffcart.app.ui.theme.HcBrick
import com.huffcart.app.ui.theme.HcCream
import com.huffcart.app.ui.theme.HcOnLight
import com.huffcart.app.ui.theme.HcRed
import com.huffcart.app.ui.theme.HcRedDark
import com.huffcart.app.ui.theme.HcYellow
import kotlinx.coroutines.delay

/**
 * 品牌启动页（app-shell「品牌启动页」）：红底全屏 + 像素字品牌名 + Canvas 主机插画，
 * 短暂展示后自动进入主界面；装饰全部代码绘制，零位图。
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HcRedDark),
    ) {
        // 底部砖块条纹装饰
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(120.dp),
        ) {
            drawBricks(size)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "吹卡带",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "FC 小霸王 · 经典回忆",
                style = MaterialTheme.typography.bodyMedium,
                color = HcCream.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(36.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            ) {
                drawConsole(size)
            }
        }
        Text(
            text = "—— 按下开始键，回到童年 ——",
            style = MaterialTheme.typography.bodyMedium,
            color = HcCream.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
    }
}

/** FC 主机 + 卡带 + 手柄插画（矢量代码绘制）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConsole(canvas: Size) {
    val cx = canvas.width / 2f
    val top = canvas.height * 0.18f

    // 卡带（插在主机顶部的黄色卡带）
    val cartW = canvas.width * 0.22f
    val cartH = canvas.height * 0.30f
    drawRoundRect(
        color = HcYellow,
        topLeft = Offset(cx - cartW / 2f, top),
        size = Size(cartW, cartH),
        cornerRadius = CornerRadius(6f, 6f),
    )
    drawRoundRect(
        color = HcRedDark,
        topLeft = Offset(cx - cartW * 0.30f, top + cartH * 0.22f),
        size = Size(cartW * 0.60f, cartH * 0.42f),
        cornerRadius = CornerRadius(3f, 3f),
    )

    // 主机机身（米白圆角矩形）
    val bodyW = canvas.width * 0.72f
    val bodyH = canvas.height * 0.42f
    val bodyX = cx - bodyW / 2f
    val bodyY = top + cartH * 0.55f
    drawRoundRect(
        color = HcCream,
        topLeft = Offset(bodyX, bodyY),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(12f, 12f),
    )
    // 卡槽
    drawRoundRect(
        color = HcOnLight,
        topLeft = Offset(bodyX + bodyW * 0.10f, bodyY + bodyH * 0.18f),
        size = Size(bodyW * 0.55f, bodyH * 0.16f),
        cornerRadius = CornerRadius(3f, 3f),
    )
    // 电源键
    drawCircle(
        color = HcOnLight,
        radius = bodyH * 0.08f,
        center = Offset(bodyX + bodyW * 0.78f, bodyY + bodyH * 0.26f),
    )
    // 两枚红色圆钮
    drawCircle(
        color = HcRed,
        radius = bodyH * 0.11f,
        center = Offset(bodyX + bodyW * 0.74f, bodyY + bodyH * 0.66f),
    )
    drawCircle(
        color = HcRed,
        radius = bodyH * 0.11f,
        center = Offset(bodyX + bodyW * 0.90f, bodyY + bodyH * 0.66f),
    )
    // 手柄（机身左下侧）：十字键
    val padCx = bodyX + bodyW * 0.16f
    val padCy = bodyY + bodyH * 0.62f
    val armW = bodyW * 0.055f
    val armL = bodyW * 0.17f
    drawRoundRect(
        color = HcOnLight,
        topLeft = Offset(padCx - armL / 2f, padCy - armW / 2f),
        size = Size(armL, armW),
        cornerRadius = CornerRadius(2f, 2f),
    )
    drawRoundRect(
        color = HcOnLight,
        topLeft = Offset(padCx - armW / 2f, padCy - armL / 2f),
        size = Size(armW, armL),
        cornerRadius = CornerRadius(2f, 2f),
    )
}

/** 复古砖块条纹（两排错缝）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBricks(canvas: Size) {
    val brickW = canvas.width / 7f
    val brickH = canvas.height / 4f
    val gap = 6f
    val brickColor = HcBrick
    for (row in 0..3) {
        val y = canvas.height - brickH * (4 - row) + gap / 2f
        val offset = if (row % 2 == 0) 0f else -brickW / 2f
        var x = offset
        while (x < canvas.width) {
            drawRoundRect(
                color = brickColor,
                topLeft = Offset(x + gap / 2f, y),
                size = Size(brickW - gap, brickH - gap),
                cornerRadius = CornerRadius(3f, 3f),
            )
            x += brickW
        }
    }
}
