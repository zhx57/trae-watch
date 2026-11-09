package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trae.watch.ui.component.WatchScaffold
import com.trae.watch.vm.Approval

/**
 * 审批页（阻断式决策）。
 *
 * M3 强调层级：
 *  同意 = Filled Button（主操作，界面唯一高强调）
 *  拒绝 = Outlined Button（次级，有分离但不抢主强调）
 * 而不是两个实心按钮并排 —— 官方规范禁止同一动作组出现多个视觉主导动作。
 *  https://m3.material.io/components/buttons/overview
 *
 * 内容容器用 Surface + surfaceContainer，不用 Card：这里是页面唯一内容块，
 * 再加卡片属于无意义的嵌套containment。
 */
@Composable
fun ApprovalScreen(
    approval: Approval?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    WatchScaffold(
        title = "需要确认",
        subtitle = approval?.toolName ?: "无待确认操作",
        onBack = onBack,
        bottomBar = {
            Surface(color = cs.surfaceContainer) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("拒绝", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("同意", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
    ) { innerPadding ->
        if (approval == null) {
            EmptyState(
                title = "没有待确认的操作",
                hint = "返回会话继续",
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (approval.summary.isNotBlank()) {
                    Text(
                        text = approval.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                    )
                }
                Surface(
                    color = cs.surfaceContainerHigh,
                    contentColor = cs.onSurfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "请求内容",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                        Text(
                            text = approval.fullText.take(1200),
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurface,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
