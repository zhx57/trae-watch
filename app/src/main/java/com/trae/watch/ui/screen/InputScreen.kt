package com.trae.watch.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import com.trae.watch.ui.component.ScreenHeader
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.TraeTheme

/** 三档输入：预设短语 / 系统输入法 / 历史复用 */
private val QUICK_PHRASES = listOf(
    "继续", "停止", "重试", "详细点", "换个方案",
    "继续执行", "跳过这一步", "解释一下", "完成了吗",
)

private val RECENT_PHRASES = listOf(
    "把这个函数改成异步的",
    "跑一下测试",
    "检查有没有编译错误",
    "提交这次改动",
)

@Composable
fun InputScreen(
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type
    var custom by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }

    WatchScreen { scale ->
        ScreenHeader(
            title = "输入",
            subtitle = if (showHistory) "历史" else "快捷",
            onBack = onBack,
            scale = scale,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = d.padScreen),
        ) {
            if (!showHistory) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(vertical = d.gapXs),
                    horizontalArrangement = Arrangement.spacedBy(d.gapS),
                    verticalArrangement = Arrangement.spacedBy(d.gapS),
                ) {
                    items(QUICK_PHRASES) { p ->
                        PhraseChip(p, scale) { onSend(p) }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(d.gapS)) {
                    RECENT_PHRASES.forEach { p ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = (48 * scale).dp)
                                .clip(RoundedCornerShape(d.cardRadius))
                                .background(c.surface)
                                .clickable { onSend(p) }
                                .padding(horizontal = d.gapM, vertical = d.gapS),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = p,
                                color = c.textPrimary,
                                style = t.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(d.gapS))

            // 手输兜底
            OutlinedTextField(
                value = custom,
                onValueChange = { if (it.length <= 500) custom = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入文字…", style = t.bodySmall, color = c.textMuted) },
                textStyle = t.bodySmall.copy(color = c.textPrimary),
                maxLines = 2,
                shape = RoundedCornerShape(d.cardRadius),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.surface,
                    unfocusedContainerColor = c.surface,
                    focusedIndicatorColor = c.primary,
                    unfocusedIndicatorColor = c.divider,
                    cursorColor = c.primary,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.padScreen, vertical = d.gapM),
            horizontalArrangement = Arrangement.spacedBy(d.gapS),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = d.touchMin)
                    .clip(RoundedCornerShape(d.buttonRadius))
                    .background(c.surfaceHigh)
                    .clickable { showHistory = !showHistory },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (showHistory) "快捷" else "历史",
                    color = c.textPrimary,
                    style = t.label,
                )
            }
            Box(
                modifier = Modifier
                    .weight(2f)
                    .heightIn(min = d.touchMin)
                    .clip(RoundedCornerShape(d.buttonRadius))
                    .background(if (custom.isBlank()) c.surfaceHigh else c.primary)
                    .clickable(enabled = custom.isNotBlank()) {
                        onSend(custom.trim())
                        custom = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "发送",
                    color = if (custom.isBlank()) c.textMuted else c.onPrimary,
                    style = t.label,
                )
            }
        }
    }
}

@Composable
private fun PhraseChip(text: String, scale: Float, onClick: () -> Unit) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (52 * scale).dp)
            .clip(RoundedCornerShape(d.cardRadius))
            .background(c.surface)
            .clickable { onClick() }
            .padding(horizontal = d.gapXs, vertical = d.gapXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = c.textPrimary,
            style = TraeTheme.type.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
