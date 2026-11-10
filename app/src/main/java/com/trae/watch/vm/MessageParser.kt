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
 * 实测真实结构（2026-11，逐条抓取确认）：
 *
 * {
 *   "data": { "messages": [
 *     // 用户消息：content 是 JSON 字符串，里面是数组
 *     {
 *       "message_id": "...", "role": "user", "message_type": "general",
 *       "message_index": 63,
 *       "query": "原始问题",
 *       "content": "[{\"type\":\"text\",\"text_content\":\"...\"}]"
 *     },
 *     // 助手消息：content 是 JSON 字符串，里面是**对象**而不是数组！
 *     {
 *       "message_id": "...", "role": "assistant", "message_type": "task",
 *       "content": "{\"task_id\":\"task_xxx\",\"messages\":[
 *           {\"id\":\"...\",\"type\":\"plan_item\",\"plan_item\":{
 *               \"thought\":\"正文回答\",
 *               \"reasoning_content\":\"思考过程\",
 *               \"tool_call_info\":{\"name\":\"Shell\",\"params\":{...}},
 *               \"agent_status\":\"...\"
 *           }}
 *       ]}"
 *     }
 *   ]}
 * }
 *
 * 之前的 bug：助手 content 以 `{` 开头而不是 `[`，旧代码直接把整个原始 JSON
 * 当正文渲染，单个气泡几万字，导致列表滑动卡死。
 */
object MessageParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 单个气泡最大字符数，超出截断，避免超长文本拖垮渲染 */
    private const val MAX_TEXT = 1200

    fun parse(convId: String, body: String): List<Message> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: return emptyList()
            val arr = (data["messages"] ?: data["list"]) as? JsonArray ?: return emptyList()
            val out = ArrayList<Message>(arr.size)
            for (el in arr) {
                val o = el as? JsonObject ?: continue
                out.addAll(toMessages(convId, o))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 一条接口消息可能展开成多条 UI 消息：
     *  - user/general → 1 条
     *  - assistant/task → 每个 plan_item 一条（正文 + 工具摘要）
     */
    private fun toMessages(convId: String, o: JsonObject): List<Message> {
        val id = o["message_id"]?.s() ?: o["id"]?.s() ?: return emptyList()
        val role = o["role"]?.s() ?: "assistant"
        val rawType = o["message_type"]?.s() ?: "general"
        val idx = o["message_index"]?.s()?.toIntOrNull() ?: 0
        val conv = o["chat_session_id"]?.s() ?: convId
        val createdAt = o["created_at_ms"]?.s()?.toLongOrNull()
            ?: o["created_at"]?.s()?.toLongOrNull() ?: 0L

        // 用户消息：query 或 content 数组
        if (rawType != "task") {
            val text = extractUserText(o)
            if (text.isBlank()) return emptyList()
            return listOf(
                Message(
                    id = id,
                    convId = conv,
                    seq = idx,
                    role = role,
                    type = "text",
                    text = clamp(text),
                    status = o["status"]?.s() ?: "",
                    createdAt = createdAt,
                )
            )
        }

        // 助手消息：content 是 {"task_id","messages":[{type,plan_item}]}
        val content = o["content"]?.jsonPrimitive?.contentOrNull() ?: return emptyList()
        val taskObj = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()
            ?: return emptyList()
        val inner = taskObj["messages"] as? JsonArray ?: return emptyList()

        val out = ArrayList<Message>(inner.size)
        var n = 0
        for (im in inner) {
            val io = im as? JsonObject ?: continue
            val pi = io["plan_item"] as? JsonObject ?: continue
            // 隐藏项（内部步骤）不展示
            if (pi["hide"]?.jsonPrimitive?.contentOrNull() == "true") continue

            val thought = pi["thought"]?.s()?.trim().orEmpty()
            val reasoning = pi["reasoning_content"]?.s()?.trim().orEmpty()
            val toolName = (pi["tool_call_info"] as? JsonObject)?.get("name")?.s().orEmpty()
            val toolParams = (pi["tool_call_info"] as? JsonObject)?.get("params")?.toString().orEmpty()

            // 一条 plan_item 只产出一个气泡，优先正文，其次思考，再次工具
            val type: String
            val text: String
            when {
                thought.isNotBlank() -> {
                    type = "text"; text = thought
                }
                toolName.isNotBlank() -> {
                    type = "tool"
                    text = buildToolSummary(toolName, toolParams)
                }
                reasoning.isNotBlank() -> {
                    type = "thought"; text = reasoning
                }
                else -> continue
            }

            out.add(
                Message(
                    id = "$id-$n",
                    convId = conv,
                    seq = idx * 100 + n,
                    role = "assistant",
                    type = type,
                    text = clamp(text),
                    status = pi["agent_status"]?.s() ?: "",
                    createdAt = createdAt,
                )
            )
            n++
        }
        return out
    }

    /** 用户消息正文：优先 query 字段，其次 content 数组 */
    private fun extractUserText(o: JsonObject): String {
        o["query"]?.s()?.let { if (it.isNotBlank()) return it }
        val content = o["content"] ?: return ""
        val raw = content.jsonPrimitive.contentOrNull()
        if (raw != null) {
            val t = raw.trimStart()
            return when {
                t.startsWith("[") -> parseParts(t)
                t.startsWith("{") -> ""   // 不是用户正文，忽略
                else -> raw
            }
        }
        val arr = content as? JsonArray ?: return ""
        return joinParts(arr)
    }

    private fun parseParts(raw: String): String = try {
        val arr = json.parseToJsonElement(raw) as? JsonArray ?: return ""
        joinParts(arr)
    } catch (e: Exception) {
        ""
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
            if (sb.length > MAX_TEXT) break
        }
        return sb.toString().trim()
    }

    /** 工具调用摘要：只显示工具名和关键参数首行，避免刷屏 */
    private fun buildToolSummary(name: String, params: String): String {
        val brief = params
            .replace("\\\\", "\\")
            .take(180)
            .replace("\n", " ")
        return if (brief.isBlank() || brief == "null") name else "$name  $brief"
    }

    private fun clamp(s: String): String =
        if (s.length <= MAX_TEXT) s else s.take(MAX_TEXT) + "\n…（已截断）"

    private fun kotlinx.serialization.json.JsonElement?.s(): String? = try {
        this?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? = try {
        content
    } catch (_: Exception) {
        null
    }
}
