package com.trae.watch.vm

import com.trae.watch.data.db.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 anchor 接口返回的消息。
 *
 * 实测结构：
 * {
 *   "data": { "messages": [ {
 *       "chat_session_id": "...",
 *       "message_id": "...",
 *       "message_type": "general",
 *       "role": "user",
 *       "message_index": 39,
 *       "content": "[{\"type\":\"text\",\"text_content\":\"...\"}]"   <-- 注意是 JSON 字符串
 *   } ] }
 * }
 *
 * 关键差异（与早期反编译推断不同）：
 *  - content 是 **字符串**，里面才是 JSON 数组
 *  - 文本字段名是 text_content，不是 text
 */
object MessageParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(convId: String, body: String): List<Message> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: return emptyList()
            val arr = (data["messages"] ?: data["list"]) as? JsonArray ?: return emptyList()
            arr.mapNotNull { el -> toMessage(convId, el as? JsonObject ?: return@mapNotNull null) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toMessage(convId: String, o: JsonObject): Message? {
        val id = o["message_id"]?.s() ?: o["id"]?.s() ?: return null
        val role = o["role"]?.s() ?: "assistant"
        val rawType = o["message_type"]?.s() ?: "general"
        val text = extractText(o)
        if (text.isBlank()) return null
        return Message(
            id = id,
            convId = o["chat_session_id"]?.s() ?: convId,
            seq = o["message_index"]?.s()?.toIntOrNull() ?: 0,
            role = role,
            type = mapType(rawType, text),
            text = text,
            status = o["status"]?.s() ?: "",
            createdAt = o["created_at_ms"]?.s()?.toLongOrNull()
                ?: o["created_at"]?.s()?.toLongOrNull() ?: 0L,
        )
    }

    private fun mapType(raw: String, text: String): String = when {
        raw.contains("tool") -> "tool"
        raw.contains("plan") -> "plan"
        raw.contains("error") -> "error"
        raw.contains("thought") -> "thought"
        // 工具调用在 general 消息里以特殊 JSON 片段出现
        text.contains("\"tool_name\"") -> "tool"
        else -> "text"
    }

    /**
     * content 两种形态都要兼容：
     *  1. JSON 字符串，如 "[{\"type\":\"text\",\"text_content\":\"hi\"}]"
     *  2. 真正的数组
     * 文本字段优先 text_content，兼容 text。
     */
    private fun extractText(o: JsonObject): String {
        o["query"]?.s()?.let { if (it.isNotBlank()) return it }

        val content = o["content"] ?: return ""
        val raw = runCatching { content.jsonPrimitive.content }.getOrNull()
        if (raw != null) {
            if (!raw.trimStart().startsWith("[")) return raw
            return parseParts(raw)
        }
        val arr = content as? JsonArray ?: return ""
        return joinParts(arr)
    }

    private fun parseParts(raw: String): String = try {
        val arr = json.parseToJsonElement(raw) as? JsonArray ?: return raw
        joinParts(arr)
    } catch (e: Exception) {
        raw
    }

    private fun joinParts(arr: JsonArray): String {
        val sb = StringBuilder()
        for (p in arr) {
            val po = p as? JsonObject ?: continue
            val t = po["text_content"]?.s() ?: po["text"]?.s()
            if (!t.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
            }
            if (sb.length > 900) break
        }
        return sb.toString().trim()
    }

    private fun kotlinx.serialization.json.JsonElement?.s(): String? = try {
        this?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
}
