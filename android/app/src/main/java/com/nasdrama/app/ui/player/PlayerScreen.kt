package com.nasdrama.app.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.data.Episode
import com.nasdrama.app.data.SeriesDetail
import com.nasdrama.app.util.AppLog
import com.nasdrama.app.util.episodeText
import com.nasdrama.app.util.formatSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 网页/播放器友好容器之外的格式需要 HLS 兜底 */
private val WEB_UNFRIENDLY = setOf("mkv", "avi", "flv", "ts", "wmv", "mpg", "mpeg", "rmvb", "vob")

/**
 * 容器 → MIME 显式映射。流地址 /api/v1/stream/{id} 不带扩展名，
 * Media3 无法从 URI 推断容器类型，部分机型上嗅探失败会直接报错无法播放。
 */
private fun containerMime(container: String): String? = when (container.lowercase()) {
    "mp4", "m4v", "mov" -> MimeTypes.VIDEO_MP4
    "mkv" -> "video/x-matroska"
    "webm" -> MimeTypes.VIDEO_WEBM
    "ts", "mpg", "mpeg" -> MimeTypes.VIDEO_MP2T
    "flv" -> "video/x-flv"
    "avi" -> "video/x-msvideo"
    "3gp" -> "video/3gpp"
    else -> null
}

/**
 * 播放页：单视频画面 + 上一集/下一集按钮/选集抽屉切集 + 单实例 ExoPlayer。
 * - 进度上报：每 5 秒 / 暂停 / 切集前 / 退出 / 播完（completed=true）
 * - 续播：进入时按服务端 episode_states 定位（position>5s）
 * - 自动连播：STATE_ENDED 上报完成后切下一集
 * - HLS 兜底：非网页友好容器播放出错时改用 &hls=1 地址重试一次
 * - 全屏播放：按钮切横屏（manifest configChanges 含 orientation，切换不重建不中断）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    seriesId: Int,
    initialEp: Int,
    onBack: () -> Unit
) {
    val repo = AppGraph.repo
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<SeriesDetail?>(null) }
    var loadError by remember { mutableStateOf("") }
    LaunchedEffect(seriesId) {
        repo.seriesDetail(seriesId).onSuccess { detail = it }
            .onFailure { loadError = it.message ?: "加载失败" }
    }

    val d = detail
    if (d == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (loadError.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onBack) { Text("返回", color = Color.White) }
                }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val eps = d.episodes
    if (eps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("该剧没有可播放的单集", color = Color.White)
        }
        return
    }
    val states = remember(d) { d.episode_states.associateBy { it.episode_id } }

    // 初始页：initialEp>0 按集序号；否则用服务端续播建议；兜底第 0 页
    val startPage = remember(d, initialEp) {
        val idx = if (initialEp > 0) eps.indexOfFirst { it.index == initialEp }
        else eps.indexOfFirst { it.id == d.continue_episode_id }
        if (idx >= 0) idx else 0
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    // 解码器兜底：小米 17/18 等旗舰常见 10bit HEVC/HDR 片源，硬解不支持时自动切换，
    // 默认配置硬解失败即报渲染错误 → 表现为一直转圈"无法播放"
    val player = remember {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers).build().apply {
            playWhenReady = true
        }
    }
    // 当前集在 eps 中的下标。旧实现遗留了一个孤儿 PagerState（UI 里没有任何 VerticalPager
    // 挂载它），currentPage 永不变化 → 上一集/下一集/自动连播的 animateScrollToPage 全是空转。
    var currentPage by remember { mutableIntStateOf(startPage.coerceIn(0, eps.size - 1)) }

    var currentEp by remember { mutableStateOf<Episode?>(null) }
    var hlsFallback by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showSheet by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playError by remember { mutableStateOf("") }
    var transcoding by remember { mutableStateOf(false) }
    var posMs by remember { mutableStateOf(0L) }
    var durMs by remember { mutableStateOf(0L) }
    var lastEpisodeToast by remember { mutableStateOf(false) }
    // 切集提示浮层（「第X集」），1.2 秒后自动消失
    var switchHint by remember { mutableStateOf("") }

    /** 上报当前播放位置（completed=false 时小于 2 秒的位置不上报，防止覆盖已存进度） */
    fun report(ep: Episode?, completed: Boolean) {
        if (ep == null) return
        val pos = player.currentPosition / 1000.0
        val dur = if (player.duration != C.TIME_UNSET && player.duration > 0) {
            player.duration / 1000.0
        } else {
            ep.duration ?: 0.0
        }
        if (!completed && pos < 2) return
        AppGraph.appScope.launch { repo.reportProgress(seriesId, ep.id, pos, dur, completed) }
    }

    /**
     * 装载某一集：直连地址 + srt 字幕 + 续播起点。
     * 续播位置钳制：超过时长（如服务端时长元数据不准）会导致 ExoPlayer 定位越界直接失败。
     */
    fun loadEpisode(page: Int) {
        val ep = eps.getOrNull(page) ?: return
        currentEp = ep
        currentPage = page
        hlsFallback = false
        playError = ""
        transcoding = false
        posMs = 0L
        durMs = 0L
        AppLog.i(TAG, "装载第${ep.index}集 id=${ep.id} container=${ep.container} stream=${repo.streamUrl(ep.id)}")
        var startMs = states[ep.id]?.takeIf { it.position > 5 }
            ?.let { (it.position * 1000).toLong() } ?: 0L
        if (ep.duration != null && ep.duration > 0 && startMs > (ep.duration * 1000 - 3000).toLong()) {
            startMs = 0L
        }
        val media = MediaItem.Builder()
            .setUri(Uri.parse(repo.streamUrl(ep.id, hls = false)))
            .apply { containerMime(ep.container)?.let { setMimeType(it) } }
            .setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(repo.subtitleUrl(ep.id)))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage("zh")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
            .build()
        player.setMediaItem(media, startMs)
        player.prepare()
        player.play()
    }

    /** 切集统一入口（上一集/下一集按钮、上下滑手势、自动连播、选集抽屉共用）。上报时机由调用方决定 */
    fun switchEpisode(page: Int) {
        val clamped = page.coerceIn(0, eps.size - 1)
        val target = eps.getOrNull(clamped) ?: return
        if (target.id == currentEp?.id) return
        AppLog.i(TAG, "切集 → 第${target.index}集（page=$clamped，from=$currentPage）")
        loadEpisode(clamped)
        switchHint = "第${target.index}集"
    }

    /**
     * HLS 兜底装载：&hls=1 的 302 落点可能是 plist 包装而非真正的 m3u8
     * （直接喂 ExoPlayer 报 PARSING_MANIFEST_MALFORMED）。
     * 先解析 plist 拿真实 url，再探测内容：#EXTM3U → HlsMediaSource；二进制 mp4 → ProgressiveMediaSource。
     */
    fun loadEpisodeHls(ep: Episode, startMs: Long) {
        transcoding = true
        scope.launch {
            try {
                val resolved = resolveStream(repo.streamUrl(ep.id, hls = true))
                AppLog.i(TAG, "HLS 兜底解析结果 isHls=${resolved.isHls} url=${resolved.url}")
                val item = MediaItem.Builder()
                    .setUri(Uri.parse(resolved.url))
                    .setMimeType(if (resolved.isHls) MimeTypes.APPLICATION_M3U8 else null)
                    .build()
                val dataSourceFactory = DefaultDataSource.Factory(context)
                val source = if (resolved.isHls) {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
                } else {
                    // 探测为二进制媒体（如 mp4）：补上容器 MIME 后按渐进式流装载
                    val progressiveItem = containerMime(ep.container)
                        ?.let { item.buildUpon().setMimeType(it).build() } ?: item
                    ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(progressiveItem)
                }
                player.setMediaSource(source, startMs)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                AppLog.e(TAG, "HLS 兜底解析失败", e)
                playError = "转码流获取失败，请重试或换一集"
            }
        }
    }

    // 播放器事件：播完连播 / 出错 HLS 兜底 / 暂停即上报
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                AppLog.i(TAG, "播放状态变化 state=$playbackState ep=${currentEp?.index}")
                buffering = playbackState == Player.STATE_BUFFERING
                transcoding = false
                if (playbackState == Player.STATE_ENDED) {
                    val ep = currentEp
                    if (ep != null) {
                        val pos = player.currentPosition / 1000.0
                        val dur = ep.duration ?: 0.0
                        AppGraph.appScope.launch {
                            repo.reportProgress(seriesId, ep.id, pos, dur, true)
                        }
                    }
                    // 已按 completed=true 上报，直接切下一集；switchEpisode 不再补报
                    // completed=false，避免覆盖刚写入的「看完」状态
                    if (currentPage < eps.size - 1) {
                        switchEpisode(currentPage + 1)
                    } else {
                        lastEpisodeToast = true
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLog.e(TAG, "播放出错 ep=${currentEp?.index} code=${error.errorCodeName}", error)
                val ep = currentEp ?: return
                // 首次出错：改用 HLS（转码）地址重试一次，直连流解不动时（编码不兼容/嗅探失败）可救回
                if (!hlsFallback) {
                    hlsFallback = true
                    playError = ""
                    // 从出错位置继续，避免跳回片头
                    val resumeMs = if (player.currentPosition > 0) player.currentPosition else 0L
                    loadEpisodeHls(ep, resumeMs)
                    return
                }
                // 重试仍失败：把错误暴露给界面（重试按钮），避免无限转圈
                playError = "播放出错（${error.errorCodeName}），请重试或换一集"
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (!isPlaying && player.playbackState != Player.STATE_ENDED) {
                    report(currentEp, completed = false)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 首次装载起始集（续播定位在 loadEpisode 内按服务端进度处理）
    LaunchedEffect(eps, startPage) {
        if (currentEp == null) loadEpisode(startPage)
    }

    // 切集提示 1.2 秒后自动消失
    LaunchedEffect(switchHint) {
        if (switchHint.isNotEmpty()) {
            delay(1200)
            switchHint = ""
        }
    }

    // 500ms 刷新进度条，每 10 拍（约 5 秒）上报一次进度
    LaunchedEffect(player) {
        var tick = 0
        while (isActive) {
            delay(500)
            val pd = player.duration
            if (pd != C.TIME_UNSET && pd > 0) durMs = pd
            if (player.isPlaying) posMs = player.currentPosition
            tick++
            if (tick % 10 == 0 && player.isPlaying) report(currentEp, completed = false)
        }
    }

    // 退出：上报最后进度并释放播放器
    DisposableEffect(Unit) {
        onDispose {
            report(currentEp, completed = false)
            player.release()
        }
    }

    // 系统返回键
    BackHandler(onBack = onBack)

    // 全面屏沉浸式：隐藏状态栏/导航栏（挖孔区域也由画面延伸填充），屏幕常亮
    val view = LocalView.current
    DisposableEffect(Unit) {
        // LocalView.context 是 ContextThemeWrapper 而非 Activity，需沿包装链向上找
        val activity = view.context.findActivity()
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        AppLog.i(TAG, "播放页进入沉浸式 activity=${activity != null} controller=${controller != null}")
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 全屏播放：按钮切横屏。manifest 的 configChanges 含 orientation，
    // 旋转只触发布局不重建 Activity，播放状态与进度不受影响
    var fullscreen by remember { mutableStateOf(false) }
    val playerActivity = remember { view.context.findActivity() }
    fun toggleFullscreen() {
        fullscreen = !fullscreen
        playerActivity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 离开播放页把方向交还系统（回竖屏），避免横屏状态泄漏到其他页面
            playerActivity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 控制层 5 秒无操作自动隐藏；点按屏幕重新显示（controlsVisible 变 true 时重新计时）。
    // 隐藏期间手势层不受影响：上下滑切集、点按呼出控制层都照常工作
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    // 上下滑切集的最小滑动距离
    val swipeThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 视频画面（原生 PlayerView，非 WebView）
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 缓冲/转码等待：HLS 兜底（转码）时明确提示正在等服务端转码，避免误以为卡死
        if ((buffering || transcoding) && !lastEpisodeToast && playError.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Color.White
                )
                if (hlsFallback) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "转码中，请稍候…",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (lastEpisodeToast) {
            Text(
                "已是最后一集",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x88000000), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }

        // 切集提示浮层（按钮/手势/自动连播都会触发）
        if (switchHint.isNotEmpty()) {
            Text(
                switchHint,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0x88000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }

        // 手势层：点按切换控制层；上滑下一集 / 下滑上一集（与网页端手势一致）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { controlsVisible = !controlsVisible }
                }
                .pointerInput(Unit) {
                    var accY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { accY = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            accY += dragAmount
                            change.consume()
                        },
                        onDragEnd = {
                            if (accY < -swipeThresholdPx) {
                                AppLog.i(TAG, "上滑切下一集（dy=${accY.toInt()}）")
                                report(currentEp, completed = false)
                                switchEpisode(currentPage + 1)
                            } else if (accY > swipeThresholdPx) {
                                AppLog.i(TAG, "下滑切上一集（dy=${accY.toInt()}）")
                                report(currentEp, completed = false)
                                switchEpisode(currentPage - 1)
                            }
                        }
                    )
                }
        )

        // 播放失败提示：置于点击层之上保证重试按钮可点击
        if (playError.isNotEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(playError, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { loadEpisode(currentPage) }) {
                    Text("重试")
                }
            }
        }

        // 控制层
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x99000000), Color.Transparent, Color(0x99000000))
                        )
                    )
            ) {
                // 顶部：返回 + 标题 + 集数
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // safeDrawing 覆盖状态栏 + 左右刘海避让；沉浸式时系统栏隐藏则自动归零，
                        // 但挖孔侧边避让仍然生效（小米 17/18 中置挖孔不压返回键）
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Column {
                        Text(
                            d.series.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            episodeText(currentEp?.index ?: 1) + if (hlsFallback) " · 转码中" else "",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 中央：上一集 / 播放暂停 / 下一集
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(
                        onClick = {
                            AppLog.i(TAG, "点击上一集（page=$currentPage）")
                            report(currentEp, completed = false)
                            switchEpisode(currentPage - 1)
                        },
                        enabled = currentPage > 0,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "上一集",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            AppLog.i(TAG, "点击下一集（page=$currentPage）")
                            report(currentEp, completed = false)
                            switchEpisode(currentPage + 1)
                        },
                        enabled = currentPage < eps.size - 1,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "下一集",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // 底部：进度条 + 时长 + 选集
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatSeconds(posMs / 1000.0),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            if (durMs > 0) {
                                val target = (fraction * durMs).toLong()
                                player.seekTo(target)
                                posMs = target
                            }
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    Text(
                        formatSeconds(durMs / 1000.0),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = { toggleFullscreen() }) {
                        Icon(
                            imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (fullscreen) "退出全屏" else "全屏播放",
                            tint = Color.White
                        )
                    }
                    TextButton(onClick = { showSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color.White)
                        Text("选集", color = Color.White)
                    }
                }
            }
        }

        // 选集抽屉
        if (showSheet) {
            ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "选集 · 共${eps.size}集",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.height(360.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(eps, key = { it.id }) { ep ->
                            val isCurrent = ep.id == currentEp?.id
                            val st = states[ep.id]
                            Button(
                                onClick = {
                                    showSheet = false
                                    report(currentEp, completed = false)
                                    switchEpisode(eps.indexOf(ep))
                                },
                                colors = if (isCurrent) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            ) {
                                Text(
                                    text = buildString {
                                        append(ep.index)
                                        if (st?.completed == true) append(" ✓")
                                    },
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private const val TAG = "Player"

/** HLS 兜底地址的解析结果：最终 url + 是否为 m3u8 清单 */
private data class ResolvedStream(val url: String, val isHls: Boolean)

/**
 * 探测转码流真实内容：
 * 1. 请求 hls 地址（自动跟随 302），读响应头若干字节；
 * 2. 若是 plist 包装（XML `<?xml`/`<plist` 或二进制 `bplist00`），提取其中第一个 http(s) URL 再请求一次；
 * 3. 按 `#EXTM3U` → HLS、`ftyp`（偏移 4）→ mp4 渐进式判定，兜底按渐进式处理。
 */
private suspend fun resolveStream(rawUrl: String): ResolvedStream = withContext(Dispatchers.IO) {
    fun probe(url: String, maxBytes: Int): Pair<String, ByteArray> {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("探测失败 HTTP $code: $url")
            val buf = ByteArray(maxBytes)
            var off = 0
            conn.inputStream.use { ins ->
                while (off < buf.size) {
                    val n = ins.read(buf, off, buf.size - off)
                    if (n < 0) break
                    off += n
                }
            }
            conn.url.toString() to buf.copyOf(off)
        } finally {
            conn.disconnect()
        }
    }

    val (finalUrl, head) = probe(rawUrl, 64 * 1024)
    val headStr = String(head, Charsets.ISO_8859_1)

    // plist 包装：XML 或 binary plist，提取内部真实地址
    val isPlist = headStr.startsWith("<?xml") || headStr.contains("<plist") ||
        headStr.startsWith("bplist00")
    if (!isPlist) {
        // 直接是媒体内容：#EXTM3U 即 HLS，否则渐进式
        return@withContext ResolvedStream(finalUrl, isHls = headStr.contains("#EXTM3U"))
    }
    AppLog.i(TAG, "转码地址返回 plist 包装，解析内部 URL（前缀=${headStr.take(8)}）")
    val innerUrl = Regex("<string>(https?://[^<]+)</string>").find(headStr)?.groupValues?.get(1)
        ?: Regex("https?://[\\x20-\\x7E]+").find(headStr)?.value
        ?: throw IllegalStateException("plist 中未找到内部 URL")
    AppLog.i(TAG, "plist 内部 URL=$innerUrl")

    // 二次探测真实地址的内容类型：#EXTM3U → HLS；否则（含二进制 mp4 的 ftyp 头）按渐进式处理
    val (realUrl, realHead) = probe(innerUrl, 64)
    val realStr = String(realHead, Charsets.ISO_8859_1)
    ResolvedStream(realUrl, isHls = realStr.contains("#EXTM3U"))
}

/** LocalView.context 通常是 ContextThemeWrapper，需沿包装链向上找 Activity */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
