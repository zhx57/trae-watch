package com.trae.watch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.net.ConvItem
import com.trae.watch.data.net.ConvSource
import com.trae.watch.ui.component.WatchScaffold

/**
 * 会话列表。
 * 严格使用官方组件：
 *  - SingleChoiceSegmentedButtonRow + SegmentedButton（本地/云端切换）
 *      https://developer.android.com/develop/ui/compose/components/segmented-button
 *  - LazyColumn + ListItem（列表项）
 *      https://developer.android.com/develop/ui/compose/lists
 *  - Button / FilledTonalButton（底部操作）
 *      https://developer.android.com/develop/ui/compose/components/button
 */
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
    val options = listOf("本地", "云端")

    WatchScaffold(
        title = "TRAE",
        subtitle = if (conversations.isEmpty()) "暂无会话" else "${conversations.size} 个会话",
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onRefresh,
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (loading) "刷新中" else "刷新")
                    }
                    Button(
                        onClick = onModels,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = modelName.ifBlank { "模型" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
            // 官方分段按钮：单选
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                        onClick = { onSwitchTab(index) },
                        selected = index == tab,
                        label = { Text(label) },
                    )
                }
            }

            HorizontalDivider()

            if (conversations.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (loading) "加载中…" else "暂无会话",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(
                        items = conversations,
                        key = { "${it.source}-${it.id}" },
                        contentType = { it.source },
                    ) { conv ->
                        ConvListItem(conv) { onOpen(conv) }
                    }
                }
            }
        }
    }
}

/** 官方 ListItem：点击必须显式加 clickable，否则整行无响应 */
@Composable
private fun ConvListItem(conv: ConvItem, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = cs.surface),
        leadingContent = {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                color = if (conv.source == ConvSource.CLOUD) cs.tertiaryContainer
                else cs.primaryContainer,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (conv.source == ConvSource.CLOUD) "云"
                        else conv.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (conv.source == ConvSource.CLOUD) cs.onTertiaryContainer
                        else cs.onPrimaryContainer,
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = conv.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = statusLabel(conv),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (conv.unread) {
            {
                Surface(
                    shape = CircleShape,
                    color = cs.primary,
                    modifier = Modifier.size(8.dp),
                ) {}
            }
        } else null,
    )
}

private fun statusLabel(c: ConvItem): String {
    val s = when (c.taskStatus) {
        "in_progress", "running" -> "运行中"
        "completed" -> "已完成"
        "interrupted" -> "已中断"
        "failed" -> "失败"
        else -> c.taskStatus
    }
    return if (c.workspace.isNotBlank()) {
        "$s · ${c.workspace.substringAfterLast('\\').ifBlank { c.workspace }}"
    } else s
}
