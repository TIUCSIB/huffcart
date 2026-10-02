package com.huffcart.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 全屏品牌页隐藏手势导航条（真机手势导航下，红底启动页/游戏屏底部会浮系统手势条，
 * 与品牌全屏设计冲突）：进入隐藏、上滑临时呼出、离开本屏恢复。
 */
@Composable
fun HideSystemNavigationBars() {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() } ?: return
    DisposableEffect(activity) {
        val controller = WindowInsetsControllerCompat(activity.window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        onDispose {
            WindowInsetsControllerCompat(activity.window, view)
                .show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
