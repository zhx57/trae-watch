package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trae.watch.ui.component.WatchScaffold

/**
 * 配对页（首屏，无返回）。
 *
 * M3 组件：
 *  - 两种配对方式 → Segmented button（互斥选择）
 *  - 输入 → OutlinedTextField（每项都有 label）
 *  - 主操作 → Filled Button（唯一高强调动作）
 *
 * 表单规范：错误就地展示在受影响区域上方，并给出明确的恢复动作，
 * 不用一个笼统的 toast 代替 https://m3.material.io/components/text-fields
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    pairing: Boolean,
    error: String,
    onPair: (host: String, port: String, code: String) -> Unit,
    onPastePayload: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("8080") }
    var code by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    val modes = listOf("配对码", "载荷")

    val canSubmit = !pairing && if (mode == 0) code.length == 6 else payload.isNotBlank()

    WatchScaffold(
        title = "连接 TRAE",
        subtitle = if (pairing) "正在连接" else "首次使用需配对一次",
        bottomBar = {
            Surface(color = cs.surfaceContainer) {
                Button(
                    onClick = {
                        if (mode == 0) onPair(host.trim(), port.trim(), code)
                        else onPastePayload(payload)
                    },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text(if (pairing) "连接中…" else "连接")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        onClick = { mode = index },
                        selected = mode == index,
                        icon = {},
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            // 错误就地展示，紧邻受影响的输入区
            if (error.isNotBlank()) {
                Surface(
                    color = cs.errorContainer,
                    contentColor = cs.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "$error\n检查电脑与手表是否在同一 Wi-Fi，或改用载荷方式",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            if (mode == 0) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("电脑 IP") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { if (it.length <= 5) port = it.filter(Char::isDigit) },
                    label = { Text("端口") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                    label = { Text("6 位配对码") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "电脑上运行 pair/pair.py 得到配对码",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("配对载荷") },
                    placeholder = { Text("TRAE1:...") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
