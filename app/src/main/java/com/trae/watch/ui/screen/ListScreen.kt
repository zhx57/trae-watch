package com.trae.watch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.StatusDot
import com.trae.watch.ui.component.TraeButton
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.TraeTheme

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
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type

    WatchScreen { scale ->
        ScreenHeader(
            title = if (tab == 0) "本地会话" else "云端会话",
            subtitle = if (conversations.isEmpty()) "" else "${conversations.size} 个",
            scale = scale,
        )

        // 本地 / 云端 切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.padScreen),
            horizontalArrangement = Arrangement.spacedBy(d.gapS),
        ) {
            TabChip("本地", tab == 0, Modifier.weight(1f)) { onSwitchTab(0) }
            TabChip("云端", tab == 1, Modifier.weight(1f)) { onSwitchTab(1) }
        }
        Spacer(Modifier.height(d.gapS))

        if (conversations.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyHint(if (loading) "加载中…" else "这一栏还没有会话")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = d.padScreen),
                verticalArrangement = Arrangement.spacedBy(d.gapS),
            ) {
                items(conversations, key = { "${it.source}-${it.id}" }) { conv ->
                    ConvRow(conv, scale) { onOpen(conv) }
                }
                item { Spacer(Modifier.height(d.gapM)) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.padScreen, vertical = d.gapM),
            horizontalArrangement = Arrangement.spacedBy(d.gapS),
        ) {
            TraeButton(
                text = if (loading) "刷新中" else "刷新",
                modifier = Modifier.weight(1f),
                enabled = !loading,
                onClick = onRefresh,
            )
            TraeButton(
                text = modelName.ifBlank { "模型" },
                modifier = Modifier.weight(1f),
                color = c.surfaceHigh,
                contentColor = c.primary,
                onClick = onModels,
            )
        }
    }
}

@Composable
private fun ConvRow(conv: ConvItem, scale: Float, onClick: () -> Unit) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (68 * scale).dp)
            .clip(RoundedCornerShape(d.cardRadius))
            .background(c.surface)
            .clickable { onClick() }
            .padding(horizontal = d.gapM, vertical = d.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size((36 * scale).dp)
                .clip(CircleShape)
                .background(c.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (conv.source == ConvSource.CLOUD) "云" else conv.title.take(1).uppercase(),
                color = if (conv.source == ConvSource.CLOUD) c.warning else c.primary,
                style = t.caption,
            )
        }
        Spacer(Modifier.width(d.gapS))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(conv.taskStatus, size = (6 * scale).dp)
                Spacer(Modifier.width(d.gapXs))
                Text(
                    text = conv.title,
                    color = c.textPrimary,
                    style = t.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildSubtitle(conv),
                color = c.textMuted,
                style = t.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (conv.unread) {
            Spacer(Modifier.width(d.gapXs))
            Box(
                modifier = Modifier.size((7 * scale).dp).clip(CircleShape).background(c.primary),
            )
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

@Composable
private fun TabChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    Box(
        modifier = modifier
            .heightIn(min = d.touchMin)
            .clip(RoundedCornerShape(d.buttonRadius))
            .background(if (active) c.primary.copy(alpha = 0.16f) else c.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) c.primary else c.textSecondary,
            style = TraeTheme.type.label,
        )
    }
}
