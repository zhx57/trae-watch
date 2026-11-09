package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.net.ConvItem
import com.trae.watch.data.net.ConvSource
import com.trae.watch.ui.component.EmptyHint
import com.trae.watch.ui.component.M3eButtonGroup
import com.trae.watch.ui.component.M3eCard
import com.trae.watch.ui.component.M3eGroupSegment
import com.trae.watch.ui.component.PressableBox
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.StatusDot
import com.trae.watch.ui.component.TonalBadge
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.M3e
import com.trae.watch.ui.theme.Squircle

@Composable
fun ListScreen(
    conversations: List<ConvItem>,
    tab: Int,
    loading: Boolean,
    modelName: String,
    onSwitchTab: (Int) -> Unit,
    onOpen: (ConvItem) -> Unit,
    onRefresh: () -> Unit,
    onModels: () -> Unit,
) {
    val c = M3e.color
    val d = M3e.dimen
    val t = M3e.type

    WatchScreen { scale ->
        ScreenHeader(
            title = if (tab == 0) "本地会话" else "云端会话",
            subtitle = if (conversations.isEmpty()) "无会话" else "${conversations.size} 个会话",
            scale = scale,
        )

        // M3E 相连按钮组：本地 / 云端
        M3eButtonGroup(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.pad)
                .heightIn(min = (44 * scale).dp),
        ) {
            M3eGroupSegment("本地", tab == 0, first = true, last = false) { onSwitchTab(0) }
            M3eGroupSegment("云端", tab == 1, first = false, last = true) { onSwitchTab(1) }
        }
        Spacer(Modifier.height(d.s))

        if (conversations.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyHint(if (loading) "加载中…" else "这一栏还没有会话")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = d.pad, vertical = d.xs),
                verticalArrangement = Arrangement.spacedBy(d.s),
            ) {
                items(conversations, key = { "${it.source}-${it.id}" }) { conv ->
                    ConvCard(conv, scale) { onOpen(conv) }
                }
                item { Spacer(Modifier.height(d.s)) }
            }
        }

        // 底部操作：刷新 + 模型，也是相连组
        M3eButtonGroup(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.pad, vertical = d.m)
                .heightIn(min = (48 * scale).dp),
        ) {
            M3eGroupSegment(
                if (loading) "刷新中" else "刷新",
                selected = false, first = true, last = false,
            ) { if (!loading) onRefresh() }
            M3eGroupSegment(
                modelName.ifBlank { "模型" },
                selected = true, first = false, last = true,
            ) { onModels() }
        }
    }
}

@Composable
private fun ConvCard(conv: ConvItem, scale: Float, onClick: () -> Unit) {
    val c = M3e.color
    val d = M3e.dimen
    val t = M3e.type

    PressableBox(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (70 * scale).dp),
        onClick = onClick,
        shape = Squircle,
        bg = c.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = d.m, vertical = d.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 音调头像块：本地用 primaryContainer，云端用 tertiaryContainer
            Box(
                modifier = Modifier
                    .size((38 * scale).dp)
                    .clip(RoundedCornerShape((14 * scale).dp))
                    .background(
                        if (conv.source == ConvSource.CLOUD) c.tertiaryContainer
                        else c.primaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (conv.source == ConvSource.CLOUD) "云"
                    else conv.title.take(1).uppercase(),
                    color = if (conv.source == ConvSource.CLOUD) c.onTertiaryContainer
                    else c.onPrimaryContainer,
                    style = t.label,
                )
            }
            Spacer(Modifier.width(d.s))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(conv.taskStatus, (7 * scale).dp)
                    Spacer(Modifier.width(d.xs))
                    Text(
                        text = conv.title,
                        color = c.onSurface,
                        style = t.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildSubtitle(conv),
                    color = c.onSurfaceVariant,
                    style = t.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (conv.unread) {
                Spacer(Modifier.width(d.xs))
                TonalBadge("新")
            }
        }
    }
}

private fun buildSubtitle(c: ConvItem): String {
    val status = when (c.taskStatus) {
        "in_progress", "running" -> "运行中"
        "completed" -> "已完成"
        "interrupted" -> "已中断"
        "failed" -> "失败"
        else -> c.taskStatus
    }
    return if (c.workspace.isNotBlank()) {
        "$status · ${c.workspace.substringAfterLast('\\').ifBlank { c.workspace }}"
    } else status
}
