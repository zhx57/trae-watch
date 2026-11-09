package com.trae.watch.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * SSE 事件流读取。
 *
 * 服务端格式（来自反编译 ISendMessageService + IMService）：
 *   GET /api/solo_hub/v1/chat_sessions/{id}/events?messageId=
 *   返回 text/event-stream，每行 data: {...}
 *   事件体内 type 字段取值：msg / thought / tool_call / plan_item / done / error / queue
 */
class SseReader(
    private val tokenProvider: () -> String,
    private val onEvent: (SseEvent) -> Unit,
    private val onState: (SseState) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var running = false
    private var call: okhttp3.Call? = null

    fun start(chatSessionId: String, messageId: String = "") {
        running = true
        Thread({
            var backoff = 1000L
            while (running) {
                try {
                    onState(SseState.CONNECTING)
                    val req = Request.Builder()
                        .url(Api.HOST_AI + Api.ssePath(chatSessionId, messageId))
                        .get()
                        .apply {
                            Http.headers(tokenProvider()).forEach { (k, v) -> header(k, v) }
                            header("Accept", "text/event-stream")
                        }
                        .build()

                    call = Http.sseClient.newCall(req)
                    call?.execute()?.use { resp ->
                        if (!resp.isSuccessful) {
                            onState(SseState.DISCONNECTED)
                            sleep(backoff)
                            backoff = (backoff * 2).coerceAtMost(30_000L)
                            return@use
                        }
                        onState(SseState.CONNECTED)
                        backoff = 1000L
                        val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream(), Charsets.UTF_8))
                        readLoop(reader)
                    }
                } catch (e: Exception) {
                    onState(SseState.DISCONNECTED)
                }
                if (running) {
                    sleep(backoff)
                    backoff = (backoff * 2).coerceAtMost(30_000L)
                }
            }
        }, "sse-$chatSessionId").apply { isDaemon = true }.start()
    }

    private fun readLoop(reader: BufferedReader) {
        val dataBuf = StringBuilder()
        while (running) {
            val line = reader.readLine() ?: break
            when {
                line.startsWith("data:") -> {
                    dataBuf.append(line.removePrefix("data:").trim())
                }
                line.isBlank() -> {
                    if (dataBuf.isNotEmpty()) {
                        parseAndDispatch(dataBuf.toString())
                        dataBuf.clear()
                    }
                }
                else -> Unit // 忽略 event:/id:/retry:
            }
        }
    }

    private fun parseAndDispatch(raw: String) {
        if (raw == "[DONE]") {
            onEvent(SseEvent(type = "done", raw = raw))
            return
        }
        try {
            val o = json.parseToJsonElement(raw).jsonObject
            val type = o["type"]?.str() ?: "unknown"
            val seq = o["seq"]?.str()?.toIntOrNull() ?: 0
            val taskId = o["task_id"]?.str() ?: ""
            val convId = o["conversation_id"]?.str() ?: ""
            onEvent(SseEvent(type, seq, taskId, convId, raw, extractText(o)))
        } catch (e: Exception) {
            // 解析失败不中断连接，只跳过
        }
    }

    /** 从 payload 里抽可用于展示的一行文本 */
    private fun extractText(o: JsonObject): String {
        val data = o["data"] as? JsonObject ?: return ""
        for (k in listOf("text", "content", "delta", "message", "tool_name", "title")) {
            val v = data[k]?.str()
            if (!v.isNullOrBlank()) return v
        }
        return ""
    }

    fun stop() {
        running = false
        call?.cancel()
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    private fun JsonElement?.str(): String? = try {
        this?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}

data class SseEvent(
    val type: String,
    val seq: Int = 0,
    val taskId: String = "",
    val convId: String = "",
    val raw: String = "",
    val text: String = "",
)

enum class SseState { CONNECTING, CONNECTED, DISCONNECTED }
