package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trae.watch.ui.component.WatchScaffold

/**
 * 任务状态页。
 *
 * M3 反馈规范：
 *  - 进度不可测 → 不确定 CircularProgressIndicator（不用假进度条）
 *  - 取消任务 = 破坏性操作 → OutlinedButton + error 色文字，不抢主强调
 *  - 无任务时不显示一个禁用的按钮占位，而是直接说明状态
 *  https://m3.material.io/components/progress-indicators
 */
@Composable
fun TaskScreen(
    running: Boolean,
    taskId: String,
    startedAt: Long,
    stepText: String,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    WatchScaffold(
        title = "任务",
        subtitle = if (running) "运行中" else "已结束",
        onBack = onBack,
        bottomBar = {
            if (running) {
                Surface(color = cs.surfaceContainer) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "取消任务",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.error,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
            }
            Text(
                text = stepText.ifBlank { if (running) "等待 Agent 响应" else "没有进行中的任务" },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (taskId.isNotBlank()) {
                Text(
                    text = "任务 ${taskId.take(12)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.outline,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
