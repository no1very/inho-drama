package com.nasdrama.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 展示格式化工具。
 */

/** 秒 → "mm:ss" 或 "h:mm:ss"（负数/异常值按 00:00 处理） */
fun formatSeconds(seconds: Double): String {
    if (seconds.isNaN() || seconds <= 0.0) return "00:00"
    val total = seconds.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", m, s)
    }
}

/** 集数文案 */
fun episodeText(index: Int): String = "第${index}集"

/** Unix 秒 → "yyyy-MM-dd HH:mm"；0 视为从未扫描 */
fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "从未"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(timestamp * 1000))
}
