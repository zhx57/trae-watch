package com.trae.watch.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trae.watch.ui.component.WatchScaffold

/**
 * 配对页。
 * 官方组件：OutlinedTextField（IP / 端口 / 配对码）+ Button
 */
@Composable
fun PairScreen(
    pairing: Boolean,
    error: String,
    onPair: (host: String, port: String, code: String) -> Unit,
    onPastePayload: (String) -> Unit,
) {
    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("8080") }
    var code by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(0) }

    WatchScaffold(
        title = "连接 TRAE",
        subtitle = if (mode == 0) "输入配对码" else "粘贴载荷",
        bottomBar = {
            Button(
                onClick = {
                    if (mode == 0) onPair(host.trim(), port.trim(), code)
                    else onPastePayload(payload)
                },
                enabled = !pairing && (if (mode == 0) code.length == 6 else payload.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(if (pairing) "连接中…" else "连接")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (error.isNotBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (mode == 0) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("电脑 IP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { if (it.length <= 5) port = it.filter(Char::isDigit) },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                    label = { Text("6 位配对码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "在电脑上运行 pair/pair.py 获取配对码",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("配对载荷") },
                    placeholder = { Text("TRAE1:...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { mode = if (mode == 0) 1 else 0 },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (mode == 0) "改用载荷" else "改用配对码")
            }
        }
    }
}
