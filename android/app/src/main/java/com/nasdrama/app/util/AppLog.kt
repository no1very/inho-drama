package com.nasdrama.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 轻量文件日志：
 * - 运行日志按天写入 filesDir/logs/app_yyyyMMdd.txt；
 * - 崩溃由默认未捕获异常处理器写入 filesDir/logs/crash_yyyyMMdd_HHmmss.txt 后再交回系统；
 * - 全部异步单线程写入，主线程零阻塞。
 * 「我的 → 运行日志」可查看与分享，排查真机（小米 17/18 等）上的播放闪退。
 */
object AppLog {

    private const val TAG_PREFIX = "InhoDrama"
    private const val MAX_LOG_DAYS = 3          // 日志保留天数
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024  // 单文件超过 2MB 轮转
    private const val MAX_VIEW_CHARS = 64 * 1024         // 日志查看页最多读取的字符数

    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)
    private val fileFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "app-log").apply { isDaemon = true }
    }

    @Volatile
    private var logDir: File? = null

    /** 在 Application/Activity 初始化时调用一次 */
    fun init(context: Context) {
        logDir = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        pruneOldLogs()
        installCrashHandler()
        i("App", "AppLog 初始化完成 dir=$logDir")
    }

    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg, null)

    fun w(tag: String, msg: String, tr: Throwable? = null) = log(Log.WARN, tag, msg, tr)

    fun e(tag: String, msg: String, tr: Throwable? = null) = log(Log.ERROR, tag, msg, tr)

    private fun log(priority: Int, tag: String, msg: String, tr: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(when (priority) {
                Log.ERROR -> 'E'
                Log.WARN -> 'W'
                else -> 'I'
            })
            append('/')
            append(tag)
            append(": ")
            append(msg)
            if (tr != null) {
                append('\n').append(stackTraceOf(tr))
            }
        }
        Log.println(priority, TAG_PREFIX + tag, msg + (tr?.let { Log.getStackTraceString(it) } ?: ""))
        executor.execute {
            try {
                val dir = logDir ?: return@execute
                val f = File(dir, "app_${dayFormat.format(Date())}.txt")
                if (f.exists() && f.length() > MAX_FILE_BYTES) {
                    f.renameTo(File(dir, "app_${fileFormat.format(Date())}_overflow.txt"))
                }
                f.appendText(line + "\n")
            } catch (_: Exception) {
                // 日志失败不影响业务
            }
        }
    }

    private fun stackTraceOf(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    /** 崩溃捕获：落盘后交回系统默认处理器（保持系统崩溃对话框/ANR 行为） */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = logDir
                if (dir != null) {
                    val f = File(dir, "crash_${fileFormat.format(Date())}.txt")
                    f.writeText(buildString {
                        appendLine("thread=${thread.name}")
                        appendLine("time=${timeFormat.format(Date())}")
                        appendLine(stackTraceOf(throwable))
                        // 崩溃前最后一段运行日志一并带上，方便定位闪退时序
                        appendLine("---- 最近的运行日志 ----")
                        appendLine(readText(maxChars = 16 * 1024))
                    })
                }
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 清理超过保留期的日志文件（阻塞调用，仅 init 时执行） */
    private fun pruneOldLogs() {
        val dir = logDir ?: return
        val cutoff = System.currentTimeMillis() - MAX_LOG_DAYS * 24L * 3600 * 1000
        dir.listFiles()?.forEach {
            if (it.isFile && it.lastModified() < cutoff) it.delete()
        }
    }

    /** 读取全部日志（最新在前，限制字符数供 UI 展示/分享） */
    fun readText(maxChars: Int = MAX_VIEW_CHARS): String {
        val dir = logDir ?: return "（日志目录未初始化）"
        val files = dir.listFiles { f -> f.isFile }?.sortedByDescending { it.name } ?: return "（暂无日志）"
        val sb = StringBuilder()
        for (f in files) {
            if (sb.length >= maxChars) break
            sb.appendLine("======== ${f.name} ========")
            sb.appendLine(runCatching { f.readText() }.getOrDefault("（读取失败）"))
        }
        return if (sb.isEmpty()) "（暂无日志）" else sb.take(maxChars).toString()
    }

    /** 清空全部日志 */
    fun clear() {
        executor.execute {
            logDir?.listFiles()?.forEach { it.delete() }
        }
    }
}
