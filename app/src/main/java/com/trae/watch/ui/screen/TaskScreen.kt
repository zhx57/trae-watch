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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.TraeTheme
import kotlinx.coroutines.delay

/** 任务状态：运行时长 + 当前步骤 + 取消 */
@Composable
fun TaskScreen(
    running: Boolean,
    taskId: String,
    startedAt: Long,
    stepText: String,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(startedAt, running) {
        while (running) {
            elapsed = if (startedAt > 0) System.currentTimeMillis() - startedAt else 0L
            delay(1000)
        }
    }

    WatchScreen { scale ->
        ScreenHeader(title = "任务", subtitle = if (running) "运行中" else "已结束", onBack = onBack, scale = scale)

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = d.padScreen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(d.gapL))
            Box(
                modifier = Modifier
                    .size((84 * scale).dp)
                    .clip(CircleShape)
                    .background(if (running) c.primary.copy(alpha = 0.15f) else c.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatDuration(elapsed),
                    color = if (running) c.primary else c.textMuted,
                    style = t.title,
                )
            }
            Spacer(Modifier.height(d.gapM))
            Text(
                text = stepText.ifBlank { if (running) "等待 Agent 响应" else "无进行中的任务" },
                color = c.textSecondary,
                style = t.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
            if (taskId.isNotBlank()) {
                Spacer(Modifier.height(d.gapXs))
                Text("ID " + taskId.take(12), color = c.textMuted, style = t.caption)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.padScreen, vertical = d.gapM)
                .heightIn(min = d.touchMin)
                .clip(RoundedCornerShape(d.buttonRadius))
                .background(if (running) c.danger.copy(alpha = 0.18f) else c.surfaceHigh)
                .clickable(enabled = running) { onCancel() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (running) "取消任务" else "已结束",
                color = if (running) c.danger else c.textMuted,
                style = t.label,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
