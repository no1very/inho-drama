package com.nasdrama.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nasdrama.app.R
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.data.HistoryItem
import com.nasdrama.app.data.SeriesCard
import com.nasdrama.app.ui.theme.avoidTopInsets
import com.nasdrama.app.util.episodeText
import com.nasdrama.app.util.formatSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 首页：双模式 ——「短视频」全屏竖滑刷剧流 /「海报墙」网格浏览，顶栏图标切换（选择持久化）。
 * 继续观看只显示未看完的记录（看完的剧自动从首页消失，历史页仍可见）。
 */
@Composable
fun HomeScreen(
    onOpenPlayer: (Int, Int?) -> Unit,
    onOpenDetail: (Int) -> Unit
) {
    val repo = AppGraph.repo
    val scope = rememberCoroutineScope()

    // ---- 首页模式：feed=短视频流（默认） / wall=海报墙 ----
    var mode by remember { mutableStateOf("feed") }
    LaunchedEffect(Unit) {
        mode = AppGraph.settings.homeModeFlow.first()
    }

    var searchMode by remember { mutableStateOf(false) }

    // ---- 剧流状态：nextCursor null 表示没有更多 ----
    var feedItems by remember { mutableStateOf<List<SeriesCard>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<Int?>(0) }
    var initialLoaded by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf("") }

    // ---- 继续观看（只取未看完） ----
    var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    // ---- 搜索 ----
    var keyword by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SeriesCard>>(emptyList()) }
    var searchDone by remember { mutableStateOf(false) }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            repo.feed(cursor).onSuccess { resp ->
                feedItems = feedItems + resp.items
                nextCursor = resp.next_cursor
                feedError = ""
            }.onFailure {
                feedError = it.message ?: "加载失败"
            }
            loadingMore = false
        }
    }

    // 首次加载：剧流第一页 + 继续观看
    LaunchedEffect(Unit) {
        repo.feed(0).onSuccess {
            feedItems = it.items
            nextCursor = it.next_cursor
            initialLoaded = true
        }.onFailure {
            feedError = it.message ?: "加载失败"
            initialLoaded = true
        }
        repo.history(unfinishedOnly = true).onSuccess { history = it.items }
    }

    if (searchMode) {
        SearchContent(
            keyword = keyword,
            onKeywordChange = { keyword = it },
            onClose = { searchMode = false },
            results = searchResults,
            searchDone = searchDone,
            onResults = { searchResults = it; searchDone = true },
            onOpenDetail = onOpenDetail
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏：标题 + 模式切换 + 搜索（avoidTopInsets 避让状态栏/挖孔，不写死高度）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .avoidTopInsets()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_inho_monkey),
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Inho短剧",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            // 双模式切换：当前为短视频流时显示海报墙图标，反之显示播放图标
            IconButton(onClick = {
                mode = if (mode == "feed") "wall" else "feed"
                scope.launch { AppGraph.settings.saveHomeMode(mode) }
            }) {
                Icon(
                    imageVector = if (mode == "feed") Icons.Filled.GridView else Icons.Filled.Slideshow,
                    contentDescription = if (mode == "feed") "切换为海报墙" else "切换为短视频"
                )
            }
            IconButton(onClick = { searchMode = true }) {
                Icon(Icons.Filled.Search, contentDescription = "搜索")
            }
        }

        // 继续观看（两种模式共用）
        if (history.isNotEmpty()) {
            Text(
                "继续观看",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.series.id }) { item ->
                    ContinueCard(item = item, onClick = {
                        onOpenPlayer(item.series.id, item.episode.index)
                    })
                }
            }
        }

        when {
            !initialLoaded -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            // ===== 海报墙模式：网格浏览 =====
            mode == "wall" -> {
                if (feedItems.isEmpty()) {
                    EmptyHint(feedError)
                } else {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        items(feedItems, key = { it.id }) { card ->
                            PosterCard(card = card, onClick = { onOpenDetail(card.id) })
                        }
                    }
                    // 滚动接近末尾自动加载下一页
                    LaunchedEffect(gridState) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .distinctUntilChanged()
                            .collect { last ->
                                if (feedItems.isNotEmpty() && last >= feedItems.size - 4) loadMore()
                            }
                    }
                    if (loadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(12.dp)
                                .size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // ===== 短视频模式：全屏竖滑刷剧流 =====
            else -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (feedItems.isEmpty()) {
                        EmptyHint(feedError)
                    } else {
                        // Int.MAX_VALUE 循环分页：向下滑动永不触底
                        val pagerState = rememberPagerState(initialPage = 0) { Int.MAX_VALUE }
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val item = feedItems[page % feedItems.size]
                            FeedCard(
                                item = item,
                                onPlay = { onOpenPlayer(item.id, null) },
                                onDetail = { onOpenDetail(item.id) }
                            )
                        }
                        // 接近末尾时自动追加下一页（loadMore 内部实时读取最新列表，不随数据变化重启）
                        LaunchedEffect(pagerState) {
                            snapshotFlow { pagerState.currentPage }
                                .distinctUntilChanged()
                                .collect { page ->
                                    val size = feedItems.size
                                    if (size > 0 && page % size >= size - 3) loadMore()
                                }
                        }
                        if (loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 空态提示 */
@Composable
private fun EmptyHint(feedError: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (feedError.isNotEmpty()) feedError else "还没有剧集",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "到「我的 → 媒体源管理」添加并扫描媒体库",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ---------------- 组件 ---------------- */

/** 刷剧流单屏：封面铺满 + 底部渐变信息 + 播放/详情按钮 */
@Composable
private fun FeedCard(
    item: SeriesCard,
    onPlay: () -> Unit,
    onDetail: () -> Unit
) {
    val repo = AppGraph.repo
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = repo.coverUrl(item.cover),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // 压暗渐变，保证文字可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x66000000),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xCC000000)
                        )
                    )
                )
        )
        // 左下：剧名 + 简介 + 元信息
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 96.dp, bottom = 24.dp)
        ) {
            Text(
                item.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.intro.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    item.intro,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "共${item.episode_count}集 · ${item.source_name}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        // 右下：详情 + 播放
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = onDetail,
                containerColor = Color(0x66000000),
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Info, contentDescription = "详情")
            }
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "立即播放",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/** 海报墙卡片：竖版封面（底部渐变遮罩 + 品牌集数徽章）+ 剧名 + 分类，点击进详情 */
@Composable
private fun PosterCard(card: SeriesCard, onClick: () -> Unit) {
    val repo = AppGraph.repo
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box {
            AsyncImage(
                model = repo.coverUrl(card.cover),
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            // 底部渐变遮罩：浅色封面上徽章依然可读
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color(0x99000000))
                        )
                    )
            )
            Text(
                "共${card.episode_count}集",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        RoundedCornerShape(7.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                card.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (card.category.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    card.category,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 继续观看卡片：封面 + 剧名 + 第X集 + 进度条 */
@Composable
private fun ContinueCard(item: HistoryItem, onClick: () -> Unit) {
    val repo = AppGraph.repo
    val progress = item.duration?.takeIf { it > 0 }?.let {
        (item.position / it).toFloat().coerceIn(0f, 1f)
    } ?: 0f
    Card(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box {
            AsyncImage(
                model = repo.coverUrl(item.series.cover),
                contentDescription = item.series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                item.series.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (item.completed) "已看完 ${episodeText(item.episode.index)}"
                else "${episodeText(item.episode.index)} · ${formatSeconds(item.position)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 内嵌搜索：防抖 300ms 调 /series，两列网格结果 */
@Composable
private fun SearchContent(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onClose: () -> Unit,
    results: List<SeriesCard>,
    searchDone: Boolean,
    onResults: (List<SeriesCard>) -> Unit,
    onOpenDetail: (Int) -> Unit
) {
    val repo = AppGraph.repo
    // 防抖搜索
    LaunchedEffect(keyword) {
        delay(300)
        repo.search(keyword).onSuccess { onResults(it.items) }
    }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            singleLine = true,
            placeholder = { Text("搜索剧名") },
            leadingIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .avoidTopInsets()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (results.isEmpty() && searchDone) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有找到相关剧集", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results, key = { it.id }) { card ->
                    PosterCard(card = card, onClick = { onOpenDetail(card.id) })
                }
            }
        }
    }
}
