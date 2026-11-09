package com.trae.watch.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** 会话来源：本地 CLI 会话 / 云端会话 */
enum class ConvSource { LOCAL, CLOUD }

/** 统一的会话模型 */
data class ConvItem(
    val id: String,
    val title: String,
    val source: ConvSource,
    val taskStatus: String,
    val updatedAt: Long,
    val workspace: String,
    val unread: Boolean,
)

data class ModelItem(val name: String, val displayName: String, val isDefault: Boolean)

class ApiClient(private val tokenProvider: () -> String) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class Result(val code: Int, val body: String, val ok: Boolean)

    fun get(host: String, path: String): Result = exec(
        Request.Builder().url(host + path).get()
    )

    fun post(host: String, path: String, body: String): Result = exec(
        Request.Builder().url(host + path).post(body.toRequestBody(jsonMedia))
    )

    private fun exec(b: Request.Builder): Result {
        val token = tokenProvider()
        Http.headers(token).forEach { (k, v) -> b.header(k, v) }
        return try {
            Http.client.newCall(b.build()).execute().use { r ->
                Result(r.code, r.body?.string() ?: "", r.isSuccessful)
            }
        } catch (e: IOException) {
            Result(-1, e.message ?: "network error", false)
        }
    }

    // ---------- 会话列表 ----------

    /**
     * 本地 CLI 会话：GET /api/solo_hub/v1/conversations
     * 返回字段见实测：id / title / latest_task_status / updated_at / workspace / status
     */
    fun localConversations(): List<ConvItem> {
        val r = get(Api.HOST_AI, Api.PATH_CONVERSATIONS)
        if (!r.ok) return emptyList()
        return try {
            val data = json.parseToJsonElement(r.body).jsonObject["data"]?.jsonObject
                ?: return emptyList()
            val arr = data["conversations"] as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                ConvItem(
                    id = o["id"]?.s() ?: return@mapNotNull null,
                    title = o["title"]?.s() ?: "未命名",
                    source = ConvSource.LOCAL,
                    taskStatus = o["latest_task_status"]?.s() ?: "",
                    updatedAt = parseIso(o["updated_at"]?.s()),
                    workspace = o["workspace"]?.s() ?: "",
                    unread = false,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 云端会话：GET /api/remote/v1/chat_sessions
     * 返回 chat_session_id / title / status(数字) / updated_at(毫秒字符串) / is_unread
     */
    fun cloudSessions(): List<ConvItem> {
        val r = get(Api.HOST_AI, Api.PATH_CHAT_SESSIONS)
        if (!r.ok) return emptyList()
        return try {
            val data = json.parseToJsonElement(r.body).jsonObject["data"]?.jsonObject
                ?: return emptyList()
            val arr = data["items"] as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = o["chat_session_id"]?.s() ?: return@mapNotNull null
                val st = o["status"]?.s() ?: ""
                ConvItem(
                    id = id,
                    title = o["title"]?.s() ?: "未命名",
                    source = ConvSource.CLOUD,
                    taskStatus = when (st) {
                        "3" -> "running"
                        "4" -> "interrupted"
                        "5" -> "completed"
                        else -> st
                    },
                    updatedAt = o["updated_at"]?.s()?.toLongOrNull() ?: 0L,
                    workspace = "",
                    unread = o["is_unread"]?.s() == "true",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- 模型 ----------

    /** 拉原始响应体，用于同时缓存与解析 */
    fun modelsRaw(): String? {
        val r = get(Api.HOST_AI, Api.PATH_MODELS)
        return if (r.ok && r.body.isNotBlank()) r.body else null
    }

    /** 解析模型列表，可独立调用（缓存复用时也走这里） */
    fun parseModels(raw: String): List<ModelItem> {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val list = root["data"]?.jsonObject?.get("list") as? JsonArray ?: return emptyList()
            val out = ArrayList<ModelItem>()
            for (g in list) {
                val go = g as? JsonObject ?: continue
                if (go["function"]?.s() != "solo_coder") continue
                val ms = go["models"] as? JsonArray ?: continue
                for (m in ms) {
                    val mo = m as? JsonObject ?: continue
                    val name = mo["name"]?.s() ?: continue
                    out.add(
                        ModelItem(
                            name = name,
                            displayName = mo["display_name"]?.s() ?: name,
                            isDefault = mo["is_default"]?.s() == "true",
                        )
                    )
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun models(): List<ModelItem> = modelsRaw()?.let { parseModels(it) } ?: emptyList()

    // ---------- 消息 ----------

    fun fetchMessages(convId: String, limit: Int = 40): String? {
        val r = get(Api.HOST_AI, "${Api.PATH_MSG_ANCHOR}?chat_session_id=$convId&limit=$limit")
        return if (r.ok) r.body else null
    }

    fun sendMessage(convId: String, text: String, modelName: String = ""): Result {
        val quoted = quote(text)
        val modelQuoted = quote(modelName)
        val payload = """{"content":[{"type":"text","text":$quoted}],""" +
            """"query":$quoted,"model_name":$modelQuoted,"agent_type":"solo_coder","model_selection_strategy":"auto"}"""
        return post(Api.HOST_AI, Api.sendPath(convId), payload)
    }

    fun sendDecision(convId: String, taskId: String, approved: Boolean): Result {
        val payload = """{"chat_session_id":"$convId","task_id":"$taskId","decision":${if (approved) 1 else 2}}"""
        return post(Api.HOST_AI, Api.PATH_USER_DECISION, payload)
    }

    fun cancelTask(convId: String, taskId: String): Result {
        val payload = """{"chat_session_id":"$convId","task_id":"$taskId"}"""
        return post(Api.HOST_AI, Api.PATH_CANCEL_TASK, payload)
    }

    // ---------- 工具 ----------

    private fun kotlinx.serialization.json.JsonElement?.s(): String? = try {
        this?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }

    /** ISO8601 -> 毫秒（API 27 无 java.time，用 SimpleDateFormat） */
    private fun parseIso(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        s.toLongOrNull()?.let { return it }
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        )
        for (p in patterns) {
            try {
                val f = java.text.SimpleDateFormat(p, java.util.Locale.US)
                f.parse(s)?.let { return it.time }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 16)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
