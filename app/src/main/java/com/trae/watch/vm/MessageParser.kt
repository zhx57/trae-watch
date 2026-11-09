package com.trae.watch.vm

import com.trae.watch.data.db.Message
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 把 conversations/messages/anchor 的响应转成手表端消息列表。
 *
 * 只保留 role / type / text 三类有效信息，其余字段（token 用量、文件 diff、
 * 工具原始参数）在手表上无法展示，直接丢弃以省内存。
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
        val id = o["message_id"]?.jsonPrimitive?.content
            ?: o["id"]?.jsonPrimitive?.content
            ?: return null
        val role = o["role"]?.jsonPrimitive?.content ?: "assistant"
        val rawType = o["message_type"]?.jsonPrimitive?.content ?: "text"
        val type = mapType(rawType)
        val text = extractText(o)
        if (type == "thought") return null // 思考过程在手表上不展示
        if (text.isBlank() && type == "text") return null
        return Message(
            id = id,
            convId = o["chat_session_id"]?.jsonPrimitive?.content ?: convId,
            seq = o["message_index"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            role = role,
            type = type,
            text = text,
            status = o["status"]?.jsonPrimitive?.content ?: "",
            createdAt = o["created_at_ms"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        )
    }

    private fun mapType(raw: String): String = when {
        raw.contains("thought") -> "thought"
        raw.contains("tool") -> "tool"
        raw.contains("plan") -> "plan"
        raw.contains("error") -> "error"
        else -> "text"
    }

    /** 从 content 里抽文本，content 可能是字符串或 parts 数组 */
    private fun extractText(o: JsonObject): String {
        // 优先 query（用户消息）
        o["query"]?.jsonPrimitive?.content?.let { if (it.isNotBlank()) return it }

        val content = o["content"] ?: return ""
        // 字符串形式
        runCatching { content.jsonPrimitive.content }.getOrNull()?.let { return it }
        // 数组形式：拼接 text 字段
        val arr = content as? JsonArray ?: return ""
        val sb = StringBuilder()
        for (p in arr) {
            val po = p as? JsonObject ?: continue
            val t = po["text"]?.jsonPrimitive?.content
            if (!t.isNullOrBlank()) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(t)
            }
            if (sb.length > 800) break // 手表端截断，避免单条消息撑爆内存
        }
        return sb.toString()
    }
}
