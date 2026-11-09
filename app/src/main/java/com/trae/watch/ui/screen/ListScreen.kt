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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.data.db.Conversation
import com.trae.watch.ui.component.EmptyHint
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.StatusDot
import com.trae.watch.ui.component.TraeButton
import com.trae.watch.ui.component.TraeCard
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.TraeTheme

/** 会话列表：竖向列表，充分利用 502 的高度 */
@Composable
fun ListScreen(
    conversations: List<Conversation>,
    loading: Boolean,
    sseState: String,
    onOpen: (Conversation) -> Unit,
    onRefresh: () -> Unit,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type

    WatchScreen { scale ->
        ScreenHeader(
            title = "会话",
            subtitle = if (sseState == "CONNECTED") "已连接" else "未连接",
            scale = scale,
        )

        if (conversations.isEmpty()) {
            Box(Modifier.weight(1f)) {
                EmptyHint(if (loading) "加载中…" else "暂无会话\n下拉或点下方刷新")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = d.padScreen),
                verticalArrangement = Arrangement.spacedBy(d.gapS),
            ) {
                items(conversations, key = { it.id }) { conv ->
                    ConversationRow(conv, scale) { onOpen(conv) }
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
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, scale: Float, onClick: () -> Unit) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (72 * scale).dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(d.cardRadius))
            .background(c.surface)
            .clickable { onClick() }
            .padding(horizontal = d.gapM, vertical = d.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 纯色圆 + 首字母，代替头像图片
        Box(
            modifier = Modifier
                .size((40 * scale).dp)
                .clip(CircleShape)
                .background(c.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = conv.title.take(1).uppercase(),
                color = c.primary,
                style = t.label,
            )
        }
        Spacer(Modifier.width(d.gapM))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(conv.status, size = (7 * scale).dp)
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
                text = conv.lastSnippet.ifBlank { conv.status },
                color = c.textSecondary,
                style = t.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (conv.unread > 0) {
            Spacer(Modifier.width(d.gapS))
            Box(
                modifier = Modifier
                    .size((20 * scale).dp)
                    .clip(CircleShape)
                    .background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (conv.unread > 9) "9+" else conv.unread.toString(),
                    color = c.onPrimary,
                    style = t.caption,
                )
            }
        }
    }
}
