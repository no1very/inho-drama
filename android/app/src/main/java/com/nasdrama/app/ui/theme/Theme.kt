package com.nasdrama.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 全局深色主题：背景 #0b0b0f、主色 #EE2B6C（品牌品红）、辅色 #FF7A45（品牌橙），
 * 与 Inho 猴 logo 的 橙→品红 渐变同源。
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFEE2B6C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF431426),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFFF7A45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A1E10),
    onSecondaryContainer = Color(0xFFFFDCCC),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF141419),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF1F1F27),
    onSurfaceVariant = Color(0xFFB9B9C4),
    outline = Color(0xFF3A3A44)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
