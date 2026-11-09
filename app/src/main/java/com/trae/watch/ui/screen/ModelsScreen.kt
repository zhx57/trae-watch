package com.trae.watch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.TraeTheme

/** 模型选择：一屏可滚动的胶囊列表 */
@Composable
fun ModelsScreen(
    models: List<ModelItem>,
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type

    WatchScreen { scale ->
        ScreenHeader(title = "选择模型", subtitle = "${models.size} 个可用", onBack = onBack, scale = scale)

        if (models.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyHint("模型列表为空\n请检查网络后重试") }
            return@WatchScreen
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = d.padScreen),
            verticalArrangement = Arrangement.spacedBy(d.gapXs),
        ) {
            items(models, key = { it.name }) { m ->
                val active = m.name == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = (52 * scale).dp)
                        .clip(RoundedCornerShape(d.cardRadius))
                        .background(if (active) c.primary.copy(alpha = 0.16f) else c.surface)
                        .clickable { onSelect(m.name) }
                        .padding(horizontal = d.gapM),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width((3 * scale).dp)
                            .heightIn(min = (18 * scale).dp)
                            .clip(CircleShape)
                            .background(if (active) c.primary else c.surfaceHigh),
                    )
                    Spacer(Modifier.width(d.gapS))
                    Text(
                        text = m.displayName,
                        color = if (active) c.primary else c.textPrimary,
                        style = t.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (m.isDefault) {
                        Text("默认", color = c.textMuted, style = t.caption)
                    }
                    if (active) {
                        Spacer(Modifier.width(d.gapXs))
                        Text("✓", color = c.primary, style = t.label)
                    }
                }
            }
            item { Spacer(Modifier.heightIn(min = d.gapM)) }
        }
    }
}
