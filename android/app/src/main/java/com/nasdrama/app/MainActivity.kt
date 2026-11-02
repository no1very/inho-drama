package com.nasdrama.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.ui.theme.AppTheme
import com.nasdrama.app.util.AppLog

/**
 * 单 Activity 入口：全 Compose 实现，无任何 WebView。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 边到边绘制（内容延伸进状态栏/挖孔区与导航栏后面，播放页沉浸也依赖它）。
        // App 为纯深色 UI，固定 dark 样式让系统栏图标恒为浅色——
        // 默认 auto 跟随系统深浅色，系统浅色模式下会给出黑色图标，深色背景上看不见
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // 日志与崩溃捕获最先初始化，保证后续任何异常都能落盘
        AppLog.init(this)
        // 初始化全局依赖（DataStore / Retrofit / Repo）
        AppGraph.init(this)
        setContent {
            AppTheme {
                AppNavHost()
            }
        }
    }
}
