package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.db.Message
import com.trae.watch.ui.component.WatchScaffold

/**
 * 会话详情。
 *
 * 交互参照主流 AI 对话客户端（ChatGPT / Claude / 豆包 / Kimi）的共通形态：
 *  - 消息流占满主体，自动吸底到最新一条
 *  - 输入框常驻底部，不需要跳转到独立页面
 *  - 顶部标题栏显示当前模型，可点击切换
 *
 * 组件全部为官方：LazyColumn + Card + OutlinedTextField + Button。
 */
@Composable
fun ChatScreen(
    title: String,
    messages: List<Message>,
    running: Boolean,
    modelName: String = "",
    error: String = "",
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPickModel: () -> Unit,
    onTask: () -> Unit,
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    WatchScaffold(
        title = title,
        subtitle = when {
            error.isNotBlank() -> error
            running -> "运行中"
            else -> "空闲"
        },
        onBack = onBack,
        actions = {
            TextButton(onClick = onPickModel) {
                Text(
                    text = modelName.ifBlank { "模型" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running) {
                TextButton(onClick = onTask) {
                    Text("任务", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 500) draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入内容") },
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            val t = draft.trim()
                            if (t.isNotEmpty()) {
                                onSend(t)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank(),
                    ) {
                        Text("发送")
                    }
                }
            }
        },
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (error.isNotBlank()) error else "还没有消息，直接输入发第一条",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { m -> MessageCard(m) }
            }
            // 新消息吸底，和主流对话 App 一致
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }
}

/** 官方 Card：ElevatedCard / Card / FilledCard 三选一，这里用 tonal 区分角色 */
@Composable
private fun MessageCard(m: Message) {
    val cs = MaterialTheme.colorScheme
    val isUser = m.role == "user"

    val container = when (m.type) {
        "error" -> cs.errorContainer
        "tool" -> cs.secondaryContainer
        "plan" -> cs.surfaceVariant
        else -> if (isUser) cs.primaryContainer else cs.surfaceVariant
    }
    val content = when (m.type) {
        "error" -> cs.onErrorContainer
        "tool" -> cs.onSecondaryContainer
        "plan" -> cs.onSurfaceVariant
        else -> if (isUser) cs.onPrimaryContainer else cs.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (m.type != "text") {
                Text(
                    text = when (m.type) {
                        "tool" -> "工具调用"
                        "plan" -> "计划"
                        "error" -> "错误"
                        else -> m.type
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f),
                )
            }
            Text(
                text = m.text,
                style = if (isUser) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.bodyMedium,
                color = content,
                maxLines = if (m.type == "tool") 2 else 20,
                overflow = TextOverflow.Ellipsis,
            )
            if (m.status == "failed") {
                Text(
                    text = "发送失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.error,
                )
            }
        }
    }
}
