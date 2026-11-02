package com.nasdrama.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.data.SeriesDetail
import com.nasdrama.app.ui.theme.avoidBottomInsets
import com.nasdrama.app.ui.theme.avoidTopInsets
import com.nasdrama.app.util.episodeText

/**
 * 剧集详情：封面头部 + 简介 + 立即/继续播放 + 4 列选集网格。
 */
@Composable
fun DetailScreen(
    seriesId: Int,
    onBack: () -> Unit,
    onOpenPlayer: (Int, Int?) -> Unit
) {
    val repo = AppGraph.repo
    var detail by remember { mutableStateOf<SeriesDetail?>(null) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(seriesId) {
        repo.seriesDetail(seriesId).onSuccess { detail = it }
            .onFailure { error = it.message ?: "加载失败" }
    }

    val d = detail
    if (d == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack) { Text("返回") }
                }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val states = remember(d) { d.episode_states.associateBy { it.episode_id } }
    val eps = d.episodes
    val continueIndex = eps.firstOrNull { it.id == d.continue_episode_id }?.index
    val firstIndex = eps.firstOrNull()?.index ?: 1
    val continueLabel = if (continueIndex != null && continueIndex != firstIndex) {
        "继续播放 ${episodeText(continueIndex)}"
    } else {
        "立即播放"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 全屏路由没有底部导航栏，滚动内容末尾避让小白条
            .avoidBottomInsets()
            .verticalScroll(rememberScrollState())
    ) {
        // 封面头部
        Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(
                model = repo.coverUrl(d.series.cover),
                contentDescription = d.series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x66000000), Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .avoidTopInsets()
                    .padding(4.dp)
                    .background(Color(0x55000000), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(d.series.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    if (d.series.category.isNotBlank()) append(d.series.category).append(" · ")
                    append("共${d.series.episode_count}集 · ${d.series.source_name}")
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (d.series.intro.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    d.series.intro,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onOpenPlayer(seriesId, null) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(continueLabel, fontSize = 16.sp)
            }

            Spacer(Modifier.height(20.dp))
            Text("选集", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            // 手动分块，避免 LazyVerticalGrid 嵌套滚动冲突
            eps.chunked(4).forEach { rowEps ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowEps.forEach { ep ->
                        val st = states[ep.id]
                        val inProgress = st != null && !st.completed && st.position > 5
                        Button(
                            onClick = { onOpenPlayer(seriesId, ep.index) },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = when {
                                inProgress -> ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                                st?.completed == true -> ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                else -> ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)
                        ) {
                            Text(
                                text = buildString {
                                    append(ep.index)
                                    if (st?.completed == true) append(" ✓")
                                },
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                        }
                    }
                    repeat(4 - rowEps.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
