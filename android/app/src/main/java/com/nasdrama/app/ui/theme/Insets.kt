package com.nasdrama.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier

/**
 * 全面屏 / 挖孔屏（澎湃OS4、小米 17/18 等中置挖孔）系统栏避让。
 * inset 全部由系统实时下发：切换「隐藏挖孔」「手势 / 三键导航」或旋转屏幕时
 * Compose 会自动重组重排，任何布局都不要写死状态栏 / 导航栏高度。
 */

/**
 * 顶部 + 左右避让：状态栏（挖孔屏上已含挖孔高度）∪ 刘海 / 挖孔 ∪ 左右手势区。
 * 用于页面顶栏 / 覆盖在封面上的返回按钮；横屏时自动改为避让左右挖孔侧。
 */
@Composable
fun Modifier.avoidTopInsets(): Modifier =
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )

/** 底部避让：小白条（手势导航条）/ 三键导航栏高度 */
@Composable
fun Modifier.avoidBottomInsets(): Modifier =
    windowInsetsPadding(WindowInsets.navigationBars)
