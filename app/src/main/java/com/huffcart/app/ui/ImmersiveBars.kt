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
 * `hideStatusBar` 供横屏全屏游戏屏（game-landscape-fullscreen）同时隐藏状态栏；
 * 横竖屏切换时随参数重启 effect，恢复/再隐藏方向正确。竖屏调用方不传参，行为不变。
 */
@Composable
fun HideSystemNavigationBars(hideStatusBar: Boolean = false) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() } ?: return
    DisposableEffect(activity, hideStatusBar) {
        val controller = WindowInsetsControllerCompat(activity.window, view)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val bars = WindowInsetsCompat.Type.navigationBars() or
            if (hideStatusBar) WindowInsetsCompat.Type.statusBars() else 0
        controller.hide(bars)
        onDispose {
            WindowInsetsControllerCompat(activity.window, view)
                .show(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
        }
    }
}
