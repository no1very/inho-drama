package com.nasdrama.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.data.ScanStatus
import com.nasdrama.app.data.Source
import com.nasdrama.app.data.SourceConfig
import com.nasdrama.app.data.SourceSaveRequest
import com.nasdrama.app.util.formatDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TYPE_LABELS = mapOf(
    "local" to "本地目录",
    "webdav" to "WebDAV",
    "jellyfin" to "Jellyfin",
    "emby" to "Emby"
)

/** 媒体源类型对应的图标 */
private fun typeIcon(type: String) = when (type) {
    "local" -> Icons.Outlined.Folder
    "webdav" -> Icons.Outlined.Cloud
    "jellyfin" -> Icons.Outlined.PlayCircle
    else -> Icons.Outlined.Dns
}

/**
 * 媒体源管理：列表 / 添加 / 编辑 / 删除 / 单源扫描 / 全部扫描 / 扫描状态轮询。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit) {
    val repo = AppGraph.repo
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<Source>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<ScanStatus?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    var editing by remember { mutableStateOf<Source?>(null) } // null 且 dialogOpen=false 表示新增
    var dialogOpen by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Source?>(null) }

    fun reload() {
        scope.launch {
            repo.sources().onSuccess { sources = it.items }
            loaded = true
        }
    }

    LaunchedEffect(refresh) { reload() }

    // 每 2 秒轮询扫描状态
    LaunchedEffect(Unit) {
        while (true) {
            repo.scanStatus().onSuccess { status = it }
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("媒体源管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            repo.scanAll().onSuccess {
                                // 触发状态轮询立即刷新一次
                                repo.scanStatus().onSuccess { status = it }
                            }
                        }
                    }) { Text("扫描全部") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 扫描状态卡片
            val st = status
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (st?.running == true) {
                        Text(
                            "正在扫描：${st.source_name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(
                            st?.let {
                                "上次扫描：${formatDate(it.finished_at)} · ${it.series_count} 部 / ${it.episode_count} 集"
                            } ?: "尚未扫描",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!st?.last_error.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "最近错误：${st?.last_error}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!loaded) {
                Spacer(Modifier.weight(1f))
                Text(
                    "加载中…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sources, key = { it.id }) { src ->
                        SourceCard(
                            src = src,
                            onScan = {
                                scope.launch {
                                    repo.scanSource(src.id)
                                    repo.scanStatus().onSuccess { status = it }
                                }
                            },
                            onEdit = {
                                editing = src
                                formError = ""
                                dialogOpen = true
                            },
                            onDelete = { deleting = src }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                editing = null
                                formError = ""
                                dialogOpen = true
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("＋ 添加媒体源") }
                    }
                }
            }
        }
    }

    // 添加 / 编辑表单
    if (dialogOpen) {
        SourceFormDialog(
            initial = editing,
            error = formError,
            onDismiss = { dialogOpen = false },
            onSave = { req ->
                scope.launch {
                    repo.saveSource(editing?.id, req)
                        .onSuccess {
                            dialogOpen = false
                            refresh++
                        }
                        .onFailure { formError = it.message ?: "保存失败" }
                }
            }
        )
    }

    // 删除确认
    val target = deleting
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除媒体源") },
            text = { Text("确定删除「${target.name}」吗？其缓存的剧集数据与观看记录将一并移除（不影响原始文件）。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteSource(target.id)
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
private fun SourceCard(
    src: Source,
    onScan: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    typeIcon(src.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(TYPE_LABELS[src.type] ?: src.type, fontSize = 11.sp) }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    src.name,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (src.enabled) "已启用" else "已停用",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${src.series_count} 部剧 · 最近扫描 ${formatDate(src.last_scan_at)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (src.last_error.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    src.last_error,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }
            Row {
                TextButton(onClick = onScan) { Text("扫描") }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** 添加/编辑弹窗：按类型切换字段；编辑时密码/ApiKey 留空=保持原值 */
@Composable
private fun SourceFormDialog(
    initial: Source?,
    error: String,
    onDismiss: () -> Unit,
    onSave: (SourceSaveRequest) -> Unit
) {
    val isEdit = initial != null
    var type by remember { mutableStateOf(initial?.type ?: "local") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var path by remember { mutableStateOf(initial?.config?.path ?: "/media") }
    var url by remember { mutableStateOf(initial?.config?.url ?: "") }
    var username by remember { mutableStateOf(initial?.config?.username ?: "") }
    var password by remember { mutableStateOf("") } // 编辑时留空=保持原值
    var basePath by remember { mutableStateOf(initial?.config?.base_path ?: "/") }
    var insecure by remember { mutableStateOf(initial?.config?.insecure ?: false) }
    var apiKey by remember { mutableStateOf("") } // 编辑时留空=保持原值
    var userId by remember { mutableStateOf(initial?.config?.user_id ?: "") }
    var transcode by remember { mutableStateOf(initial?.config?.transcode ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑媒体源" else "添加媒体源") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 类型选择（编辑时不允许换类型，避免配置错乱）
                if (!isEdit) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TYPE_LABELS.toList().chunked(2).forEach { rowTypes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowTypes.forEach { (key, label) ->
                                    FilterChip(
                                        selected = type == key,
                                        onClick = { type = key },
                                        label = { Text(label, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                when (type) {
                    "local" -> {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = path,
                            onValueChange = { path = it },
                            label = { Text("目录（容器内路径）") },
                            placeholder = { Text("/media") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    "webdav" -> {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("WebDAV 地址") },
                            placeholder = { Text("http://192.168.1.10:5244/dav") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (isEdit) "密码（留空保持原值）" else "密码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = basePath,
                            onValueChange = { basePath = it },
                            label = { Text("起始路径（相对 dav 根目录）") },
                            placeholder = { Text("/短剧") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = insecure, onCheckedChange = { insecure = it })
                            Text("跳过 HTTPS 证书校验（自签名）", fontSize = 13.sp)
                        }
                    }

                    "jellyfin", "emby" -> {
                        val isJellyfin = type == "jellyfin"
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(if (isJellyfin) "Jellyfin 地址" else "Emby 地址") },
                            placeholder = { Text(if (isJellyfin) "http://192.168.1.10:8096" else "http://192.168.1.10:8095") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        // 用户名 + 密码优先：保存时服务端自动换取 AccessToken，无需手填 API Key
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名（与密码一起填，免 API Key）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (isEdit) "密码（留空保持原值）" else "密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(if (isEdit) "API Key（可选，留空保持原值）" else "API Key（可选）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = userId,
                            onValueChange = { userId = it },
                            label = { Text("User ID（可选，限定可见库）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = transcode, onCheckedChange = { transcode = it })
                            Text("网页端优先 HLS 转码", fontSize = 13.sp)
                        }
                    }
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val config = when (type) {
                    "local" -> SourceConfig(path = path)
                    "webdav" -> SourceConfig(
                        url = url, username = username, password = password,
                        base_path = basePath, insecure = insecure
                    )
                    else -> SourceConfig(
                        url = url, username = username, password = password,
                        api_key = apiKey, user_id = userId, transcode = transcode
                    )
                }
                onSave(SourceSaveRequest(type = type, name = name, enabled = true, config = config))
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
