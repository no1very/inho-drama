package com.nasdrama.app.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.data.Repo
import com.nasdrama.app.data.ServerInfo
import com.nasdrama.app.data.SessionData
import com.nasdrama.app.ui.theme.avoidTopInsets
import kotlinx.coroutines.launch

/**
 * 我的：账号信息、服务器地址修改、版本、媒体源管理入口、退出登录。
 */
@Composable
fun MineScreen(
    onOpenSources: () -> Unit,
    onOpenLogs: () -> Unit = {},
    onLoggedOut: () -> Unit
) {
    val repo = AppGraph.repo
    val settings = AppGraph.settings
    val scope = rememberCoroutineScope()

    val session by settings.sessionFlow.collectAsStateWithLifecycle(initialValue = SessionData())
    var serverInfo by remember { mutableStateOf<ServerInfo?>(null) }
    var showEditServer by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf("") }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repo.serverInfo().onSuccess { serverInfo = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .avoidTopInsets()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("我的", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // 账号卡片
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(session.username.ifBlank { "未登录" }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        serverInfo?.let { "${it.name} v${it.version}" } ?: "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 设置项卡片
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                SettingRow(
                    title = "服务器地址",
                    value = session.serverUrl.ifBlank { "未设置" },
                    onClick = {
                        editServer = session.serverUrl
                        showEditServer = true
                    }
                )
                HorizontalDivider()
                SettingRow(
                    title = "媒体源管理",
                    value = "",
                    onClick = onOpenSources
                )
                HorizontalDivider()
                SettingRow(
                    title = "运行日志",
                    value = "",
                    onClick = onOpenLogs
                )
                HorizontalDivider()
                SettingRow(
                    title = "服务版本",
                    value = serverInfo?.version ?: "-",
                    onClick = null
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { showLogoutConfirm = true },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("退出登录", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(32.dp))
    }

    // 修改服务器地址：保存后要求重新登录（清 token）
    if (showEditServer) {
        AlertDialog(
            onDismissRequest = { showEditServer = false },
            title = { Text("修改服务器地址") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editServer,
                        onValueChange = { editServer = it },
                        singleLine = true,
                        label = { Text("地址") },
                        placeholder = { Text("http://192.168.1.100:8080") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "修改后将退出登录，需要重新登录新服务器",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        settings.save(
                            serverUrl = Repo.normalizeServerUrl(editServer),
                            token = ""
                        )
                        showEditServer = false
                        onLoggedOut()
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEditServer = false }) { Text("取消") }
            }
        )
    }

    // 退出登录确认
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("确定退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.logout()
                        settings.clearToken()
                        showLogoutConfirm = false
                        onLoggedOut()
                    }
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(180.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
