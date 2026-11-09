package com.trae.watch.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 手表端顶栏高度。官方 mobile TopAppBar 为 64dp，在 251dp 高的表盘上占掉四分之一。 */
private val CompactTopBarHeight = 48.dp

/**
 * 页面骨架。
 *
 * 结构严格遵循官方 Scaffold：
 *  https://developer.android.com/develop/ui/compose/components/scaffold
 *  1. topBar / bottomBar 由 Scaffold 布局，内容区必须消费 innerPadding
 *  2. containerColor 走 colorScheme.background，区域层级用 surfaceContainer 表达
 *  3. contentWindowInsets 显式置零：手表无系统栏，避免默认 systemBars inset 再叠一层内边距
 *
 * 与官方 mobile 规范的唯一偏离（产品约束所致）：
 *  顶栏用 48dp 紧凑行而不是 64dp TopAppBar。410x502 像素 / 约 205x251dp 的方屏上，
 *  64dp 顶栏 + 56dp 底栏只剩 130dp 给内容，消息流会被压到几乎不可用。
 *  顶栏本身只承载「返回 + 标题 + 至多两个操作」，用官方 IconButton(48dp 触控靶) 保证可达性。
 */
@Composable
fun WatchScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = cs.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = cs.surfaceContainer, contentColor = cs.onSurface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = CompactTopBarHeight)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = cs.onSurface,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (onBack == null) 12.dp else 0.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        content = actions,
                    )
                }
            }
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        content(innerPadding)
    }
}
