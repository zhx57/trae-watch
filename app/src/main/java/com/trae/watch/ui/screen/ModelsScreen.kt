package com.trae.watch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.net.ModelItem
import com.trae.watch.ui.component.WatchScaffold

/**
 * 模型选择。
 *
 * M3 组件语义：单选 → Radio button（互斥选择），整行点击而不是只点小圆点。
 *  https://m3.material.io/components/radio-button
 * 列表 → List（LazyColumn + ListItem）
 * 刷新 → PullToRefreshBox（官方组件，替代底部刷新按钮，给内容区腾高度）
 *
 * 选中态三重编码：RadioButton 图形 + 容器色 + 文字色，不依赖颜色单通道。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    models: List<ModelItem>,
    selected: String,
    loading: Boolean,
    error: String,
    updatedAt: Long,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    WatchScaffold(
        title = "选择模型",
        subtitle = when {
            loading -> "刷新中"
            error.isNotBlank() -> error
            else -> "${models.size} 个可用"
        },
        onBack = onBack,
        actions = {
            TextButton(onClick = onRefresh, enabled = !loading) {
                Text(
                    text = "刷新",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (loading) cs.outline else cs.primary,
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = onRefresh,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (models.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    }
                    Text(
                        text = when {
                            loading -> "正在获取模型"
                            error.isNotBlank() -> error
                            else -> "模型列表为空"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(models, key = { it.name }) { m ->
                        val active = m.name == selected
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(m.name) },
                            colors = ListItemDefaults.colors(
                                containerColor = if (active) cs.secondaryContainer else cs.surface,
                            ),
                            leadingContent = {
                                RadioButton(selected = active, onClick = { onSelect(m.name) })
                            },
                            headlineContent = {
                                Text(
                                    text = m.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (active) cs.onSecondaryContainer else cs.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = if (m.isDefault) {
                                {
                                    Text(
                                        text = "默认",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            } else null,
                        )
                        if (m != models.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 48.dp),
                                color = cs.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
