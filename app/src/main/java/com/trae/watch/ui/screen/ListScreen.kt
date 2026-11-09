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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * 组件选择全部依据 M3 官方文档，不自创控件：
 *  - 会话集合 → List（LazyColumn + ListItem）
 *      https://m3.material.io/components/lists
 *  - 本地/云端互斥切换 → Segmented button
 *      https://m3.material.io/components/segmented-buttons
 *  - 下拉刷新 → PullToRefreshBox
 *      https://developer.android.com/develop/ui/compose/components/pull-to-refresh
 *  - 长按上下文菜单 → DropdownMenu
 *      https://developer.android.com/develop/ui/compose/touch-input/pointer-input/tap-and-press
 *
 * 手表密度取舍：
 *  251dp 高的表盘上，双行 ListItem（72dp）只能露出 2 条。这里改用单行
 *  ListItem（56dp）+ trailing 状态文字，能在同一屏露出 3 条并保持 48dp 触控靶。
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
    val cs = MaterialTheme.colorScheme
    val tabs = listOf("本地" to ConvSource.LOCAL, "云端" to ConvSource.CLOUD)

    WatchScaffold(
        title = "会话",
        subtitle = when {
            error.isNotBlank() -> error
            conversations.isEmpty() -> "暂无会话"
            else -> "${conversations.size} 个"
        },
        actions = {
            TextButton(onClick = onModels) {
                Text(
                    text = modelName.ifBlank { "模型" },
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // 官方单选分段按钮：互斥、紧凑、带选中态
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                tabs.forEachIndexed { index, (label, source) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        onClick = { onSwitchTab(index) },
                        selected = index == tab,
                        icon = {},
                        label = {
                            Text(
                                text = "$label ${conversations.count { it.source == source }}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                    )
                }
            }

            HorizontalDivider(color = cs.outlineVariant)

            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (conversations.isEmpty()) {
                    EmptyState(
                        title = if (loading) "加载中" else "暂无会话",
                        hint = if (loading) "正在同步" else "下拉刷新",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(
                            items = conversations,
                            key = { "${it.source}-${it.id}" },
                            contentType = { it.source },
                        ) { conv ->
                            ConvRow(
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

/** 单行列表项：标题为主，状态/置顶在 trailing，符合 M3 List 规范 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConvRow(
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
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
            colors = ListItemDefaults.colors(
                containerColor = if (conv.pinned) cs.surfaceContainerHigh else cs.surface,
            ),
            leadingContent = {
                Surface(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    color = when (conv.source) {
                        ConvSource.CLOUD -> cs.tertiaryContainer
                        else -> cs.primaryContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (conv.source == ConvSource.CLOUD) "云" else "本",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (conv.source) {
                                ConvSource.CLOUD -> cs.onTertiaryContainer
                                else -> cs.onPrimaryContainer
                            },
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = conv.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // 状态同时用颜色 + 文字双通道编码，不依赖颜色单通道
                    StatusDot(status = conv.taskStatus)
                    Text(
                        text = if (conv.pinned) "置顶" else shortStatus(conv.taskStatus),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (conv.pinned) cs.primary else cs.onSurfaceVariant,
                    )
                }
            },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (conv.pinned) "取消置顶" else "置顶") },
                onClick = {
                    menuOpen = false
                    onTogglePin()
                },
            )
            DropdownMenuItem(
                text = { Text("删除会话", color = cs.error) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/** 状态点：颜色 + 实心/空心形状双编码 */
@Composable
private fun StatusDot(status: String) {
    val cs = MaterialTheme.colorScheme
    val (color, filled) = when (status) {
        "in_progress", "running" -> cs.primary to true
        "completed" -> cs.tertiary to true
        "failed", "interrupted" -> cs.error to false
        else -> cs.outline to false
    }
    Surface(
        shape = CircleShape,
        color = if (filled) color else cs.surface,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, color),
        modifier = Modifier.size(6.dp),
    ) {}
}

/** 空态：说明现状 + 下一步（M3 empty state 规范） */
@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun shortStatus(taskStatus: String): String = when (taskStatus) {
    "in_progress", "running" -> "运行中"
    "completed" -> "完成"
    "interrupted" -> "中断"
    "failed" -> "失败"
    else -> ""
}
