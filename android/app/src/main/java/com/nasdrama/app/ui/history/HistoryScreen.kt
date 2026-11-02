package com.nasdrama.app.ui.history

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.data.HistoryItem
import com.nasdrama.app.ui.theme.avoidTopInsets
import com.nasdrama.app.util.episodeText
import com.nasdrama.app.util.formatSeconds
import kotlinx.coroutines.launch

/**
 * 历史页：继续观看列表（每部剧一条），支持删除单条记录。
 */
@Composable
fun HistoryScreen(onOpenPlayer: (Int, Int?) -> Unit) {
    val repo = AppGraph.repo
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<HistoryItem>?>(null) } // null = 加载中
    var refresh by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<HistoryItem?>(null) }

    LaunchedEffect(refresh) {
        repo.history().onSuccess { items = it.items }
            .onFailure { items = emptyList() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "继续观看",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .avoidTopInsets()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        when (val list = items) {
            null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> if (list.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无观看记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(list, key = { it.series.id }) { item ->
                        HistoryRow(
                            item = item,
                            onContinue = { onOpenPlayer(item.series.id, item.episode.index) },
                            onDelete = { deleting = item }
                        )
                    }
                }
            }
        }
    }

    // 删除确认
    val target = deleting
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除记录") },
            text = { Text("确定删除《${target.series.title}》的观看记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteHistory(target.series.id)
                        deleting = null
                        refresh++
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    onContinue: () -> Unit,
    onDelete: () -> Unit
) {
    val repo = AppGraph.repo
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = repo.coverUrl(item.series.cover),
                contentDescription = item.series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(84.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    item.series.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (item.completed) "已看完 ${episodeText(item.episode.index)}"
                    else "看到${episodeText(item.episode.index)} · 还剩 ${
                        formatSeconds(((item.duration ?: 0.0) - item.position).coerceAtLeast(0.0))
                    }",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                val progress = item.duration?.takeIf { it > 0 }
                    ?.let { (item.position / it).toFloat().coerceIn(0f, 1f) } ?: 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledTonalButton(onClick = onContinue) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("继续")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
