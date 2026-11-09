package com.trae.watch.ui.screen

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 *
 * 交互参照主流 AI 对话客户端：
 *  - 下拉刷新（官方 PullToRefreshBox）
 *      https://developer.android.com/develop/ui/compose/components/pull-to-refresh
 *  - 点击进入详情；长按弹出上下文菜单（置顶 / 删除）
 *      https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press
 *  - 本地 / 云端用官方 SingleChoiceSegmentedButtonRow
 *  - 列表用 LazyColumn + ListItem，保留 key 与 contentType 以复用
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    conversations: List<ConvItem>,
    tab: Int,
    loading: Boolean,
    error: String,
    modelName: String,
    onSwitchTab: (Int) -> Unit,
    onOpen: (ConvItem) -> Unit,
    onRefresh: () -> Unit,
    onModels: () -> Unit,
    onTogglePin: (ConvItem) -> Unit = {},
    onDelete: (ConvItem) -> Unit = {},
) {
    val options = listOf("本地", "云端")

    WatchScaffold(
        title = "TRAE",
        subtitle = when {
            error.isNotBlank() -> error
            conversations.isEmpty() -> "暂无会话"
            else -> "${conversations.size} 个会话"
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

            // 官方下拉刷新容器
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (conversations.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (loading) "加载中…" else "暂无会话\n下拉可刷新",
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
                            ConvListItem(
                                conv = conv,
                                onClick = { onOpen(conv) },
                                onTogglePin = { onTogglePin(conv) },
                                onDelete = { onDelete(conv) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 官方 ListItem + combinedClickable：短按进入，长按弹出菜单 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConvListItem(
    conv: ConvItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                ),
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
            trailingContent = if (conv.unread || conv.pinned) {
                {
                    if (conv.pinned) {
                        Text(
                            text = "置顶",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.primary,
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = cs.primary,
                            modifier = Modifier.size(8.dp),
                        ) {}
                    }
                }
            } else null,
        )

        // 官方 DropdownMenu：长按弹出的上下文菜单
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(if (conv.pinned) "取消置顶" else "置顶") },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text("删除会话") },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
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
