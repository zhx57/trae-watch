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

enum class ConvSource { LOCAL, CLOUD }

data class ConvItem(
    val id: String,
    val title: String,
    val source: ConvSource,
    val taskStatus: String,
    val updatedAt: Long,
    val workspace: String,
    val unread: Boolean,
    /** tasks/create 必填：cli id 与类型、项目 id */
    val cliId: String = "",
    val cliType: String = "local",
    val projectId: String = "",
)

data class ModelItem(val name: String, val displayName: String, val isDefault: Boolean)

class ApiClient(
    private val tokenProvider: () -> String,
    /** token 失效时回调，用于清缓存并强制重取 */
    private val onTokenExpired: () -> Unit = {},
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class Result(val code: Int, val body: String, val ok: Boolean) {
        /** 业务成功：HTTP 2xx 且 body.code == 200 */
        val bizOk: Boolean get() = ok && (body.contains("\"code\":200") || body.contains("\"code\":0"))
        val bizMessage: String
            get() = runCatching {
                Regex("\"message\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
            }.getOrNull() ?: ""
    }

    fun get(host: String, path: String): Result = exec(Request.Builder().url(host + path).get())

    fun post(host: String, path: String, body: String): Result =
        exec(Request.Builder().url(host + path).post(body.toRequestBody(jsonMedia)))

    private fun exec(b: Request.Builder): Result {
        val token = tokenProvider()
        Http.headers(token).forEach { (k, v) -> b.header(k, v) }
        return try {
            Http.client.newCall(b.build()).execute().use { r ->
                // 401/403 说明 access token 失效，通知上层清缓存
                if (r.code == 401 || r.code == 403) onTokenExpired()
                Result(r.code, r.body?.string() ?: "", r.isSuccessful)
            }
        } catch (e: IOException) {
            Result(-1, e.message ?: "network error", false)
        }
    }

    // ---------- 会话列表 ----------

    fun localConversations(): List<ConvItem> {
        val r = get(Api.HOST_AI, Api.PATH_CONVERSATIONS)
        if (!r.ok) return emptyList()
        return try {
            val data = json.parseToJsonElement(r.body).jsonObject["data"]?.jsonObject ?: return emptyList()
            val arr = data["conversations"] as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val id = o["id"]?.s() ?: return@mapNotNull null
                ConvItem(
                    id = id,
                    title = o["title"]?.s() ?: "未命名",
                    source = ConvSource.LOCAL,
                    taskStatus = o["latest_task_status"]?.s() ?: "",
                    updatedAt = parseIso(o["updated_at"]?.s()),
                    workspace = o["workspace"]?.s() ?: "",
                    unread = false,
                    cliId = o["cli_id"]?.s() ?: "",
                    cliType = o["cli_type"]?.s() ?: "local",
                    projectId = o["owner_project_id"]?.s() ?: "",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cloudSessions(): List<ConvItem> {
        val r = get(Api.HOST_AI, Api.PATH_CHAT_SESSIONS)
        if (!r.ok) return emptyList()
        return try {
            val data = json.parseToJsonElement(r.body).jsonObject["data"]?.jsonObject ?: return emptyList()
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
                        "3" -> "in_progress"; "4" -> "interrupted"; "5" -> "completed"; else -> st
                    },
                    updatedAt = o["updated_at"]?.s()?.toLongOrNull() ?: 0L,
                    workspace = "",
                    unread = o["is_unread"]?.s() == "true",
                    cliType = "remote",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 用 full_detail 补齐 cli_id / project_id / workspace（发消息必需） */
    fun conversationDetail(convId: String): ConvItem? {
        val r = get(Api.HOST_AI, "${Api.PATH_FULL_DETAIL}?conversation_id=$convId")
        if (!r.ok) return null
        return try {
            val conv = json.parseToJsonElement(r.body).jsonObject["data"]?.jsonObject
                ?.get("conversation")?.jsonObject ?: return null
            ConvItem(
                id = conv["id"]?.s() ?: convId,
                title = conv["title"]?.s() ?: "",
                source = ConvSource.LOCAL,
                taskStatus = conv["latest_task_status"]?.s() ?: "",
                updatedAt = parseIso(conv["updated_at"]?.s()),
                workspace = conv["workspace"]?.s() ?: "",
                unread = false,
                cliId = conv["cli_id"]?.s() ?: "",
                cliType = conv["cli_type"]?.s() ?: "local",
                projectId = conv["owner_project_id"]?.s() ?: "",
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------- 模型 ----------

    fun modelsRaw(): String? {
        val r = get(Api.HOST_AI, Api.PATH_MODELS)
        return if (r.ok && r.body.isNotBlank()) r.body else null
    }

    fun parseModels(raw: String): List<ModelItem> = try {
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

    fun models(): List<ModelItem> = modelsRaw()?.let { parseModels(it) } ?: emptyList()

    // ---------- 消息 ----------

    /** 正确参数：conversation_id + before_limit/after_limit（实测） */
    fun fetchMessagesRaw(convId: String, limit: Int = 40): String? {
        val r = get(Api.HOST_AI, "${Api.PATH_MSG_ANCHOR}?conversation_id=$convId&before_limit=$limit&after_limit=0")
        return if (r.ok && r.body.isNotBlank()) r.body else null
    }

    // ---------- 发消息 ----------

    /**
     * 真实发消息入口：tasks/create（不是 /messages）
     * 实测必填：cli_id、cli_type、conversation_id、origin、initial_message
     */
    fun createTask(c: ConvItem, text: String, modelName: String): Result {
        val q = quote(text)
        val m = quote(modelName)
        val payload = buildString {
            append("{")
            if (c.cliId.isNotBlank()) append("\"cli_id\":${quote(c.cliId)},")
            append("\"cli_type\":${quote(c.cliType)},")
            append("\"conversation_id\":${quote(c.id)},")
            append("\"origin\":\"mobile\",")
            append("\"mode\":\"code\",")
            append("\"session_type\":\"normal\",")
            if (c.projectId.isNotBlank()) append("\"project_id\":${quote(c.projectId)},")
            if (c.workspace.isNotBlank()) append("\"workspace_dir\":${quote(c.workspace)},")
            append("\"auto_create_project\":false,")
            append("\"initial_message\":{")
            append("\"query\":$q,")
            append("\"content\":[{\"type\":\"text\",\"text_content\":$q}],")
            append("\"agent_type\":\"solo_coder\",")
            append("\"model_name\":$m,")
            append("\"model_selection_strategy\":\"auto\"")
            append("}}")
        }
        return post(Api.HOST_AI, Api.PATH_SEND_TASK, payload)
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

    private fun parseIso(s: String?): Long {
        if (s.isNullOrBlank()) return 0L
        s.toLongOrNull()?.let { return it }
        for (p in arrayOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")) {
            try {
                java.text.SimpleDateFormat(p, java.util.Locale.US).parse(s)?.let { return it.time }
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
