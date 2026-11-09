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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.db.Message
import com.trae.watch.ui.component.WatchScaffold

/**
 * 会话详情。
 * 官方组件：LazyColumn + Card（消息气泡）+ Button（底部操作）
 */
@Composable
fun ChatScreen(
    title: String,
    messages: List<Message>,
    running: Boolean,
    error: String = "",
    onBack: () -> Unit,
    onInput: () -> Unit,
    onTask: () -> Unit,
) {
    val listState = rememberLazyListState()

    WatchScaffold(
        title = title,
        subtitle = when {
            error.isNotBlank() -> error
            running -> "运行中"
            else -> "空闲"
        },
        onBack = onBack,
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onInput, modifier = Modifier.weight(1f)) {
                        Text("输入")
                    }
                    if (running) {
                        FilledTonalButton(onClick = onTask, modifier = Modifier.weight(1f)) {
                            Text("任务")
                        }
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
                    text = if (error.isNotBlank()) error else "还没有消息",
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
