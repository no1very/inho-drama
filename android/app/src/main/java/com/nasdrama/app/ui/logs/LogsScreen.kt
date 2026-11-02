package com.nasdrama.app.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nasdrama.app.ui.theme.avoidBottomInsets
import com.nasdrama.app.ui.theme.avoidTopInsets
import com.nasdrama.app.util.AppLog

/**
 * 运行日志页：查看 AppLog 落盘的运行/崩溃日志，支持分享给开发者、清空。
 */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("加载中…") }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        text = AppLog.readText()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .avoidTopInsets()
            // 全屏路由：日志文本底部避让小白条
            .avoidBottomInsets()
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("运行日志", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { refreshKey++ }) { Text("刷新") }
            OutlinedButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("InhoDrama日志", text))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("复制") }
            Spacer(Modifier.height(0.dp))
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Inho短剧运行日志")
                        putExtra(Intent.EXTRA_TEXT, text.take(400_000))
                    }
                    context.startActivity(Intent.createChooser(send, "分享日志"))
                },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("分享") }
            Button(
                onClick = {
                    AppLog.clear()
                    refreshKey++
                },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("清空") }
        }

        Text(
            "闪退会自动记录为 crash_*.txt 文件；遇到播放问题请点「分享」把日志发给开发者",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        )
    }
}
