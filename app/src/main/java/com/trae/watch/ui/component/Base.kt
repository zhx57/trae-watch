package com.trae.watch.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trae.watch.ui.theme.M3e
import com.trae.watch.ui.theme.Squircle
import com.trae.watch.ui.theme.m3eSpring

/** 410x502 基准等比缩放容器 */
@Composable
fun WatchScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(scale: Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(M3e.color.surfaceContainerLowest),
    ) {
        val scale = (maxWidth.value / 410f).coerceIn(0.7f, 1.3f)
        Column(Modifier.fillMaxSize()) { content(scale) }
    }
}

/**
 * M3E 页头：大字号 display + 副标题 label。
 * 返回按钮用 tonal 圆形（M3E 的 filled tonal icon button）。
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scale: Float = 1f,
) {
    val c = M3e.color
    val d = M3e.dimen
    val t = M3e.type
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = d.pad, vertical = d.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size((38 * scale).dp)
                    .clip(CircleShape)
                    .background(c.secondaryContainer)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = c.onSecondaryContainer, style = t.headline)
            }
            Spacer(Modifier.width(d.m))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = c.onSurface,
                style = t.headline.copy(fontSize = (22 * scale).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = c.onSurfaceVariant,
                    style = t.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * M3E 卡片：surface container 音调层 + squircle 形状。
 * 可选按压回弹（M3E 弹簧），仅用于可点击元素。
 */
@Composable
fun M3eCard(
    modifier: Modifier = Modifier,
    level: Int = 1,          // 1=low 2=container 3=high
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = M3e.color
    val d = M3e.dimen
    val bg = when (level) {
        1 -> c.surfaceContainerLow
        2 -> c.surfaceContainer
        else -> c.surfaceContainerHigh
    }
    PressableBox(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = Squircle,
        bg = bg,
    ) {
        Column(Modifier.padding(d.m), content = content)
    }
}

/** 带 M3E 弹簧按压缩放的容器 */
@Composable
fun PressableBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = Squircle,
    bg: Color = M3e.color.surfaceContainer,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val d = M3e.dimen
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) d.pressScale else 1f,
        animationSpec = m3eSpring(),
        label = "m3e-press",
    )
    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                } else Modifier
            ),
        content = content,
    )
}

/** M3E 主按钮：filled，胶囊，按压回弹 */
@Composable
fun M3eButton(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = M3e.color.primary,
    contentColor: Color = M3e.color.onPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val d = M3e.dimen
    val t = M3e.type
    PressableBox(
        modifier = modifier.heightIn(min = d.touch),
        onClick = if (enabled) onClick else null,
        shape = RoundedCornerShape(d.touch / 2),
        bg = if (enabled) container else M3e.color.surfaceContainerHigh,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (enabled) contentColor else M3e.color.outline,
                style = t.label,
                maxLines = 1,
            )
        }
    }
}

/**
 * M3E 相连按钮组：两端全胶囊，内侧小圆角。
 * 这是 M3E 最具辨识度的控件之一。
 */
@Composable
fun M3eButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun RowScope.M3eGroupSegment(
    text: String,
    selected: Boolean,
    first: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    val c = M3e.color
    val d = M3e.dimen
    val t = M3e.type
    val shape = when {
        first && last -> RoundedCornerShape(d.touch / 2)
        first -> RoundedCornerShape(topStart = d.touch / 2, bottomStart = d.touch / 2, topEnd = d.groupInner, bottomEnd = d.groupInner)
        last -> RoundedCornerShape(topEnd = d.touch / 2, bottomEnd = d.touch / 2, topStart = d.groupInner, bottomStart = d.groupInner)
        else -> RoundedCornerShape(d.groupInner)
    }
    PressableBox(
        modifier = Modifier.weight(1f).heightIn(min = d.touch),
        onClick = onClick,
        shape = shape,
        bg = if (selected) c.secondaryContainer else c.surfaceContainerHigh,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
                style = t.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** 状态指示点，M3E 用 tertiary 做强调 */
@Composable
fun StatusDot(status: String, size: Dp = 8.dp) {
    val c = M3e.color
    val color = when (status) {
        "in_progress", "running", "queued" -> c.primary
        "completed", "done", "success" -> c.secondary
        "error", "failed" -> c.error
        "interrupted", "pending" -> c.tertiary
        else -> c.outline
    }
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** 音调徽标 */
@Composable
fun TonalBadge(text: String, color: Color = M3e.color.tertiary, onColor: Color = M3e.color.onTertiary) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = onColor, style = M3e.type.labelSmall)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(M3e.dimen.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = M3e.color.outline, style = M3e.type.bodySmall)
    }
}
