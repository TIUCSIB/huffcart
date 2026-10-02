package com.huffcart.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.huffcart.app.netplay.NetplayManager
import com.huffcart.app.ui.HuffcartApp
import com.huffcart.app.ui.theme.HuffcartTheme

class MainActivity : ComponentActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 窗口焦点丢失超过宽限期 ⇒ 视为用户已离开应用，关闭大厅房间并停播。
     * 必须有这条：部分模拟器（MuMu 多虚拟屏）按 HOME 后 Activity 仍处于
     * resumed 态、onStop 永不触发，房间会"永远在线"。宽限期内回到应用
     * 则取消关闭；对局（gameActive）不受此逻辑影响。
     */
    private val closeLobbyOnFocusLoss = Runnable {
        NetplayManager.onHostActivityStopped()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内置游戏一次性播种（netplay-lan）；先于首帧组合完成，游戏库首刷即可见
        BundledRomSeeder.seedIfNeeded(this)
        enableEdgeToEdge()
        setContent {
            HuffcartTheme {
                HuffcartApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            mainHandler.removeCallbacks(closeLobbyOnFocusLoss)
        } else {
            mainHandler.postDelayed(closeLobbyOnFocusLoss, FOCUS_LOSS_GRACE_MS)
        }
    }

    override fun onStop() {
        super.onStop()
        // 应用确定离开前台：立即关闭大厅房间并停播（含上条的宽限等待）
        mainHandler.removeCallbacks(closeLobbyOnFocusLoss)
        NetplayManager.onHostActivityStopped()
    }

    private companion object {
        /** 焦点丢失宽限期：容纳通知横幅、短暂弹窗等瞬态失焦。 */
        const val FOCUS_LOSS_GRACE_MS = 10_000L
    }
}
