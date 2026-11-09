package com.trae.watch.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trae.watch.ui.theme.TraeTheme

/**
 * 410x502 方屏适配容器。
 *
 * 所有子内容按基准宽度 410dp 等比缩放，保证换到 320 / 466 等分辨率时不裁切。
 * 只做一维缩放（宽度），高度用权重分配，避免纵向拉伸变形。
 */
@Composable
fun WatchScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(scale: Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(TraeTheme.colors.bg),
    ) {
        val baseline = 410f
        val scale = (maxWidth.value / baseline).coerceIn(0.7f, 1.3f)
        Column(modifier = Modifier.fillMaxSize()) {
            content(scale)
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scale: Float = 1f,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.padScreen, vertical = d.gapM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size((36 * scale).dp)
                    .clip(CircleShape)
                    .background(c.surfaceHigh)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Text("<", color = c.textPrimary, style = t.label)
            }
            Spacer(Modifier.width(d.gapM))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.textPrimary,
                style = t.title.copy(fontSize = (19 * scale).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.textSecondary,
                    style = t.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** M3E 大圆角卡片，方屏上的主要容器形态 */
@Composable
fun TraeCard(
    modifier: Modifier = Modifier,
    color: Color = TraeTheme.colors.surface,
    radius: Dp = TraeTheme.dimens.cardRadius,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(color)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(TraeTheme.dimens.gapM),
        content = content,
    )
}

/** 主操作按钮：胶囊形，最小触控 48dp */
@Composable
fun TraeButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TraeTheme.colors.primary,
    contentColor: Color = TraeTheme.colors.onPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val d = TraeTheme.dimens
    val t = TraeTheme.type
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = d.touchMin)
            .clip(RoundedCornerShape(d.buttonRadius))
            .background(if (enabled) color else TraeTheme.colors.surfaceHigh)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) contentColor else TraeTheme.colors.textMuted,
            style = t.label,
            maxLines = 1,
        )
    }
}

/** 状态点：运行中 / 完成 / 失败 */
@Composable
fun StatusDot(status: String, size: Dp = 8.dp) {
    val c = TraeTheme.colors
    val color = when (status) {
        "running", "streaming", "queued" -> c.primary
        "done", "success" -> c.textMuted
        "error", "failed" -> c.danger
        "confirm", "pending" -> c.warning
        else -> c.textMuted
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** 空状态占位，避免复杂插画 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(TraeTheme.dimens.gapL),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = TraeTheme.colors.textMuted,
            style = TraeTheme.type.bodySmall,
        )
    }
}
