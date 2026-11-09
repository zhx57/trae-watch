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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.trae.watch.vm.Approval
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.TraeTheme

/**
 * 审批页：工具名 + 一行摘要 + 同意/拒绝大按钮。
 * 长按摘要展开全文（Q10 选 A 的落地）。
 */
@Composable
fun ApprovalScreen(
    approval: Approval?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBack: () -> Unit,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type
    var expanded by remember { mutableStateOf(false) }

    WatchScreen { scale ->
        ScreenHeader(title = "需要确认", onBack = onBack, scale = scale)

        if (approval == null) {
            Box(Modifier.weight(1f)) { }
            return@WatchScreen
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = d.padScreen)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = approval.toolName,
                color = c.primary,
                style = t.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(d.gapS))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(d.cardRadius))
                    .background(c.surface)
                    .clickable { expanded = !expanded }
                    .padding(d.gapM),
            ) {
                Text(
                    text = if (expanded) approval.fullText else approval.summary,
                    color = c.textSecondary,
                    style = t.bodySmall,
                    maxLines = if (expanded) 40 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!expanded) {
                Spacer(Modifier.height(d.gapXs))
                Text("点击展开全文", color = c.textMuted, style = t.caption)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.padScreen, vertical = d.gapM),
            horizontalArrangement = Arrangement.spacedBy(d.gapS),
        ) {
            BigAction(
                text = "拒绝",
                modifier = Modifier.weight(1f),
                bg = c.surfaceHigh,
                fg = c.danger,
                onClick = onReject,
            )
            BigAction(
                text = "同意",
                modifier = Modifier.weight(1f),
                bg = c.primary,
                fg = c.onPrimary,
                onClick = onApprove,
            )
        }
    }
}

@Composable
private fun BigAction(
    text: String,
    modifier: Modifier,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val d = TraeTheme.dimens
    Box(
        modifier = modifier
            .heightIn(min = (56 * 1).dp)
            .clip(RoundedCornerShape(d.buttonRadius))
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = fg, style = TraeTheme.type.label)
    }
}
