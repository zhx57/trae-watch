package com.trae.watch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.db.Message
import com.trae.watch.ui.component.EmptyHint
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.M3eButton
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.M3e

/** 会话详情：只渲染文本块与工具摘要 */
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
    val c = M3e.color
    val d = M3e.dimen
    val t = M3e.type
    val listState = rememberLazyListState()

    WatchScreen { scale ->
        ScreenHeader(
            title = title,
            subtitle = when {
                error.isNotBlank() -> error
                running -> "运行中"
                else -> "空闲"
            },
            onBack = onBack,
            scale = scale,
        )

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyHint("还没有消息\n点下方输入开始") }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = d.pad),
                verticalArrangement = Arrangement.spacedBy(d.s),
            ) {
                items(messages, key = { it.id }) { m -> MessageBubble(m, scale) }
            }
            // 新消息自动滚到底
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.pad, vertical = d.m),
            horizontalArrangement = Arrangement.spacedBy(d.s),
        ) {
            M3eButton(text = "输入", modifier = Modifier.weight(1f), onClick = onInput)
            if (running) {
                M3eButton(
                    text = "任务",
                    modifier = Modifier.weight(1f),
                    container = c.surfaceContainerHigh,
                    contentColor = c.onSurface,
                    onClick = onTask,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(m: Message, scale: Float) {
    val c = M3e.color
    val d = M3e.dimen
    val t = M3e.type
    val isUser = m.role == "user"

    val bg = when (m.type) {
        "tool" -> c.surfaceContainerHigh
        "error" -> c.danger.copy(alpha = 0.15f)
        "plan" -> c.surface
        else -> if (isUser) c.primary.copy(alpha = 0.14f) else c.surface
    }
    val fg = when (m.type) {
        "error" -> c.danger
        "tool" -> c.primary
        else -> c.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (isUser) (18 * scale).dp else (16 * scale).dp))
            .background(bg)
            .padding(horizontal = d.m, vertical = d.s),
    ) {
        when (m.type) {
            "tool" -> {
                Text("工具", color = c.outline, style = t.caption)
                Text(
                    text = m.text,
                    color = fg,
                    style = t.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            "plan" -> {
                Text("计划", color = c.outline, style = t.caption)
                Text(
                    text = m.text,
                    color = c.onSurfaceVariant,
                    style = t.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> {
                Text(
                    text = m.text,
                    color = fg,
                    style = if (isUser) t.body else t.bodySmall,
                )
                if (m.status == "failed") {
                    Spacer(Modifier.height(2.dp))
                    Text("发送失败", color = c.danger, style = t.caption)
                }
            }
        }
    }
}



