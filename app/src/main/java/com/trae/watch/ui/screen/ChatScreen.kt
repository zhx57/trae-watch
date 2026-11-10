package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
 * 结构：消息流（LazyColumn）+ 常驻底部输入区，与主流 AI 对话客户端一致。
 *
 * 组件全部为官方 M3：
 *  - 消息容器 → Surface（每个气泡是独立的语义容器，用颜色角色区分角色）
 *  - 输入 → OutlinedTextField（带 label，不用 placeholder 代替标签）
 *  - 发送 → FilledIconButton（48dp 触控靶，icon 有 contentDescription）
 *  - 运行中 → CircularProgressIndicator（不确定进度）
 *
 * 角色区分不使用「纯色卡 + 阴影」，而是用容器色层次：
 *  用户 → primaryContainer（自己说的话是界面里最需要一眼分出的）
 *  助手 → surfaceContainerHigh（中性、可长时间阅读）
 *  工具 → secondaryContainer（过程信息，次级强调）
 *  错误 → errorContainer
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
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    WatchScaffold(
        title = title,
        subtitle = when {
            error.isNotBlank() -> error
            running -> "运行中"
            else -> modelName.ifBlank { "空闲" }
        },
        onBack = onBack,
        actions = {
            if (running) {
                TextButton(onClick = onTask) {
                    Text("任务", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                }
            }
        },
        bottomBar = {
            Surface(color = cs.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 500) draft = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("发消息", style = MaterialTheme.typography.labelSmall) },
                        textStyle = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                    FilledIconButton(
                        onClick = {
                            val t = draft.trim()
                            if (t.isNotEmpty()) {
                                onSend(t)
                                draft = ""
                            }
                        },
                        enabled = draft.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text(
                        text = "等待响应",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    EmptyState(
                        title = if (error.isNotBlank()) error else "还没有消息",
                        hint = "在下方输入第一条",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { it.type },
                ) { m -> Bubble(m) }
            }
            // 新消息吸底。低端机上动画滚长列表会明显掉帧，用瞬时滚动
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
            }
        }
    }
}

/** 消息气泡：Surface + 语义容器色，不使用阴影堆层次 */
@Composable
private fun Bubble(m: Message) {
    val cs = MaterialTheme.colorScheme
    val isUser = m.role == "user"

    val container = when (m.type) {
        "error" -> cs.errorContainer
        "tool" -> cs.secondaryContainer
        "plan" -> cs.surfaceContainerHighest
        else -> if (isUser) cs.primaryContainer else cs.surfaceContainerHigh
    }
    val content = when (m.type) {
        "error" -> cs.onErrorContainer
        "tool" -> cs.onSecondaryContainer
        "plan" -> cs.onSurfaceVariant
        else -> if (isUser) cs.onPrimaryContainer else cs.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = MaterialTheme.shapes.medium,
            color = container,
            contentColor = content,
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                val kind = when (m.type) {
                    "tool" -> "工具"
                    "plan" -> "计划"
                    "error" -> "错误"
                    else -> null
                }
                if (kind != null) {
                    Text(
                        text = kind,
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.75f),
                    )
                }
                Text(
                    text = m.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = content,
                    // 手表屏幕小：工具只显示 2 行，正文最多 12 行，避免单个气泡占满整屏
                    maxLines = if (m.type == "tool") 2 else 12,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m.status == "sending" || m.status == "failed") {
                    Text(
                        text = if (m.status == "failed") "发送失败" else "发送中",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (m.status == "failed") cs.error else cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
