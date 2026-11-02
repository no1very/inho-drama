package com.nasdrama.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasdrama.app.R
import com.nasdrama.app.data.ApiSession
import com.nasdrama.app.data.AppGraph
import com.nasdrama.app.data.Repo
import kotlinx.coroutines.launch

/**
 * 登录 / 注册页：
 * 先用 /server-info 探活并获取 allow_register，再执行登录或注册。
 */
@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val repo = AppGraph.repo
    val settings = AppGraph.settings
    val scope = rememberCoroutineScope()

    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(false) }
    var allowRegister by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    // 预填上次使用的服务器地址
    LaunchedEffect(Unit) {
        val last = settings.serverUrlOnce()
        if (last.isNotBlank()) server = last
    }

    fun submit() {
        if (server.isBlank() || username.isBlank() || password.isBlank()) {
            error = "请填写完整"
            return
        }
        if (registerMode && password.length < 6) {
            error = "密码至少 6 位"
            return
        }
        scope.launch {
            busy = true
            error = ""
            val normalized = Repo.normalizeServerUrl(server)
            // 探活前先设置 ApiSession.serverUrl：
            // 拦截器(SessionInterceptor)用该值重建请求 URL，否则会 fallback 到
            // 内置的 http://localhost/（即手机自身），导致探活失败报“无法连接服务器”
            ApiSession.serverUrl = normalized
            // 探活：同时拿到是否开放注册
            val info = repo.serverInfo().getOrNull()
            if (info == null) {
                error = "无法连接服务器，请检查地址与网络"
                busy = false
                return@launch
            }
            allowRegister = info.allow_register
            val result = if (registerMode) {
                repo.register(normalized, username, password)
            } else {
                repo.login(normalized, username, password)
            }
            result.onSuccess { auth ->
                settings.save(
                    serverUrl = normalized,
                    token = auth.token,
                    username = auth.user?.username ?: username
                )
                ApiSession.token = auth.token
                onSuccess()
            }.onFailure {
                error = it.message ?: "操作失败"
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // safeDrawing 一次性避让：状态栏/挖孔、小白条、键盘（登录页有输入框，弹键盘时表单整体上移不被遮挡）
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.ic_inho_monkey),
            contentDescription = null,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Inho短剧",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "自己的 NAS，自己的剧场",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text("服务器地址") },
            placeholder = { Text("http://192.168.1.100:8080") },
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
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { submit() },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (registerMode) "注册并登录" else "登录", fontSize = 16.sp)
            }
        }

        if (allowRegister || registerMode) {
            TextButton(onClick = {
                registerMode = !registerMode
                error = ""
            }) {
                Text(
                    if (registerMode) "已有账号？去登录" else "没有账号？注册一个"
                )
            }
        }
    }
}
