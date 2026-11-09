package com.trae.watch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.trae.watch.data.net.ModelItem
import com.trae.watch.ui.component.EmptyHint
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.M3eButton
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.M3e
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 模型选择页。
 *
 * 行为：进页面自动拉最新（父级负责），这里只渲染状态。
 *  - 有缓存时先显示旧列表，顶部提示「刷新中」
 *  - 失败时保留旧列表并提示，不空白
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
    val c = M3e.color
    val d = M3e.dimen
    val t = M3e.type

    WatchScreen { scale ->
        ScreenHeader(
            title = "选择模型",
            subtitle = when {
                loading -> "刷新中…"
                error.isNotBlank() -> error
                updatedAt > 0 -> "${models.size} 个 · ${timeAgo(updatedAt)}"
                else -> "${models.size} 个可用"
            },
            onBack = onBack,
            scale = scale,
        )

        if (models.isEmpty()) {
            Box(Modifier.weight(1f)) {
                if (loading) {
                    EmptyHint("正在获取模型列表…")
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyHint(error.ifBlank { "模型列表为空" })
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = d.pad),
                verticalArrangement = Arrangement.spacedBy(d.xs),
            ) {
                items(models, key = { it.name }) { m ->
                    val active = m.name == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (50 * scale).dp)
                            .clip(RoundedCornerShape(d.squircle))
                            .background(if (active) c.primary.copy(alpha = 0.16f) else c.surface)
                            .clickable { onSelect(m.name) }
                            .padding(horizontal = d.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width((3 * scale).dp)
                                .heightIn(min = (18 * scale).dp)
                                .clip(CircleShape)
                                .background(if (active) c.primary else c.surfaceContainerHigh),
                        )
                        Spacer(Modifier.width(d.s))
                        Text(
                            text = m.displayName,
                            color = if (active) c.primary else c.onSurface,
                            style = t.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (m.isDefault) {
                            Text("默认", color = c.outline, style = t.caption)
                        }
                        if (active) {
                            Spacer(Modifier.width(d.xs))
                            Text("✓", color = c.primary, style = t.label)
                        }
                    }
                }
                item { Spacer(Modifier.heightIn(min = d.s)) }
            }
        }

        M3eButton(
            text = if (loading) "刷新中" else "刷新列表",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.pad, vertical = d.m),
            enabled = !loading,
            onClick = onRefresh,
        )
    }
}

private fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "刚刚更新"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
}



