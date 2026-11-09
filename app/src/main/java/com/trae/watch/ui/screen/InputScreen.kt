package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.ui.component.WatchScaffold

private val QUICK_PHRASES = listOf(
    "继续", "停止", "重试", "详细点", "换个方案",
    "继续执行", "跳过这一步", "解释一下", "完成了吗",
)

private val RECENT_PHRASES = listOf(
    "把这个函数改成异步的",
    "跑一下测试",
    "检查有没有编译错误",
    "提交这次改动",
)

/**
 * 输入页。
 * 官方组件：OutlinedTextField（文本输入）+ LazyVerticalGrid（快捷短语）+ Button
 */
@Composable
fun InputScreen(
    modelName: String = "",
    onSend: (String) -> Unit,
    onPickModel: () -> Unit = {},
    onBack: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val phrases = if (showHistory) RECENT_PHRASES else QUICK_PHRASES

    WatchScaffold(
        title = "输入",
        subtitle = modelName.ifBlank { "未选模型" },
        onBack = onBack,
        actions = {
            androidx.compose.material3.TextButton(onClick = onPickModel) {
                Text("模型", style = MaterialTheme.typography.labelMedium)
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { showHistory = !showHistory },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (showHistory) "快捷" else "历史")
                    }
                    Button(
                        onClick = {
                            onSend(custom.trim())
                            custom = ""
                        },
                        enabled = custom.isNotBlank(),
                        modifier = Modifier.weight(2f),
                    ) {
                        Text("发送")
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // 官方 OutlinedTextField
            OutlinedTextField(
                value = custom,
                onValueChange = { if (it.length <= 500) custom = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                label = { Text("输入文字") },
                placeholder = { Text("要发给 Agent 的内容") },
                maxLines = 3,
                singleLine = false,
            )

            Text(
                text = if (showHistory) "历史消息" else "快捷短语",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(phrases) { p ->
                    FilledTonalButton(
                        onClick = { onSend(p) },
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(
                            text = p,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
