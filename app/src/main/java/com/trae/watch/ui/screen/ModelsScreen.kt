package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.net.ModelItem
import com.trae.watch.ui.component.WatchScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模型选择。
 * 官方组件：ListItem + RadioButton（单选列表）+ Button（刷新）
 */
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
    WatchScaffold(
        title = "选择模型",
        subtitle = when {
            loading -> "刷新中…"
            error.isNotBlank() -> error
            updatedAt > 0 -> "${models.size} 个 · ${timeAgo(updatedAt)}"
            else -> "${models.size} 个可用"
        },
        onBack = onBack,
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Button(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(if (loading) "刷新中" else "刷新列表")
                }
            }
        },
    ) { innerPadding ->
        if (models.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (loading) "正在获取模型列表…" else error.ifBlank { "模型列表为空" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                items(models, key = { it.name }) { m ->
                    val active = m.name == selected
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ListItemDefaults.colors(
                            containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ),
                        leadingContent = {
                            RadioButton(
                                selected = active,
                                onClick = { onSelect(m.name) },
                            )
                        },
                        headlineContent = {
                            Text(
                                text = m.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = if (m.isDefault) {
                            {
                                Text(
                                    text = "默认",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else null,
                        trailingContent = {
                            Surface(
                                onClick = { onSelect(m.name) },
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(1.dp),
                            ) {}
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

private fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
}
