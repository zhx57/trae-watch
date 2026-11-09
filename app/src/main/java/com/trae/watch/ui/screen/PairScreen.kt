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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.trae.watch.ui.component.WatchScreen
import com.trae.watch.ui.theme.TraeTheme

/**
 * 配对页：6 位码（主）/ 粘贴载荷（备）。
 *
 * 6 位码走局域网 POST http://host:port/pair，由 pair/pair.py 提供。
 */
@Composable
fun PairScreen(
    pairing: Boolean,
    error: String,
    onPair: (host: String, port: String, code: String) -> Unit,
    onPastePayload: (String) -> Unit,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    val t = TraeTheme.type

    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("8080") }
    var code by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(0) } // 0=code, 1=payload

    WatchScreen { scale ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = d.padScreen, vertical = d.gapL)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("连接 Trae", color = c.textPrimary, style = t.title)
            Spacer(Modifier.height(d.gapXs))
            Text(
                text = if (mode == 0) "在电脑上运行 pair.py 获取配对码" else "粘贴配对载荷",
                color = c.textSecondary,
                style = t.caption,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(d.gapM))

            Row(horizontalArrangement = Arrangement.spacedBy(d.gapS)) {
                TabChip("配对码", mode == 0, Modifier.weight(1f)) { mode = 0 }
                TabChip("载荷", mode == 1, Modifier.weight(1f)) { mode = 1 }
            }
            Spacer(Modifier.height(d.gapM))

            if (mode == 0) {
                Field(host, { host = it }, "电脑 IP", 15, KeyboardType.Text, scale)
                Spacer(Modifier.height(d.gapS))
                Field(port, { port = it }, "端口", 5, KeyboardType.Number, scale)
                Spacer(Modifier.height(d.gapS))
                Field(code, { if (it.length <= 6) code = it.filter { ch -> ch.isDigit() } }, "6 位码", 6, KeyboardType.Number, scale)
            } else {
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("TRAE1:...", style = t.caption, color = c.textMuted) },
                    textStyle = t.caption.copy(color = c.textPrimary),
                    maxLines = 3,
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

            if (error.isNotBlank()) {
                Spacer(Modifier.height(d.gapS))
                Text(error, color = c.danger, style = t.caption)
            }

            Spacer(Modifier.height(d.gapM))

            val enabled = !pairing && (if (mode == 0) code.length == 6 else payload.isNotBlank())
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = d.touchMin)
                    .clip(RoundedCornerShape(d.buttonRadius))
                    .background(if (enabled) c.primary else c.surfaceHigh)
                    .clickable(enabled = enabled) {
                        if (mode == 0) onPair(host.trim(), port.trim(), code) else onPastePayload(payload)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (pairing) "连接中…" else "连接",
                    color = if (enabled) c.onPrimary else c.textMuted,
                    style = t.label,
                )
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    maxLen: Int,
    kb: KeyboardType,
    scale: Float,
) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLen) onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, style = TraeTheme.type.caption, color = c.textMuted) },
        textStyle = TraeTheme.type.body.copy(color = c.textPrimary),
        singleLine = true,
        shape = RoundedCornerShape(d.cardRadius),
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedIndicatorColor = c.primary,
            unfocusedIndicatorColor = c.divider,
            cursorColor = c.primary,
        ),
    )
}

@Composable
private fun TabChip(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = TraeTheme.colors
    val d = TraeTheme.dimens
    Box(
        modifier = modifier
            .heightIn(min = (40 * 1).dp)
            .clip(RoundedCornerShape(d.buttonRadius))
            .background(if (active) c.primary.copy(alpha = 0.18f) else c.surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) c.primary else c.textSecondary,
            style = TraeTheme.type.caption,
        )
    }
}
