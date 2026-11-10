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
    val pinned: Boolean = false,
)

data class ModelItem(
    val name: String,
    val displayName: String,
    val isDefault: Boolean,
    /** 非空表示用户自建模型（custom_openai_compatible / deepseek 等） */
    val provider: String = "",
)

/** 模型列表来源：云端账号模型 / 本机 CLI 模型（含本地自建） */
enum class ModelScope { CLOUD, LOCAL_CLI }

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
                    pinned = o["is_pinned"]?.s() == "true",
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

    /**
     * 模型列表。
     *
     * 实测结论（2026-11）：
     *  1. /api/remote/v1/models 只返回账号级云端模型，看不到本机自建模型
     *     （hy4free、deepseek//xxx 等都不会出现）
     *  2. 本机 CLI 模型必须走 /api/solo_hub/v1/clis/models，且必须带
     *     functions=solo_coder，否则返回空 list
     *  3. force_refresh=true 才会拿到最新（Kimi K2.5、GLM-5.1 等）而不是旧缓存
     *  4. 返回结构是 {data:{list:[{function, models:[...]}]}}，按 function 分组
     *
     * @param cliId 在线 CLI 的 id；为空时只能取云端模型
     */
    fun modelsRaw(cliId: String = "", forceRefresh: Boolean = true): String? {
        val r = if (cliId.isNotBlank()) {
            get(
                Api.HOST_AI,
                "${Api.PATH_CLI_MODELS}?cli_id=$cliId" +
                    "&functions=solo_coder&force_refresh=$forceRefresh",
            )
        } else {
            get(Api.HOST_AI, "${Api.PATH_MODELS}?functions=solo_coder")
        }
        return if (r.ok && r.body.isNotBlank()) r.body else null
    }

    /** 解析模型列表；结构为 data.list[].models[]，只取 solo_coder 分组 */
    fun parseModels(raw: String): List<ModelItem> = try {
        val root = json.parseToJsonElement(raw).jsonObject
        val list = root["data"]?.jsonObject?.get("list") as? JsonArray ?: return emptyList()
        val out = ArrayList<ModelItem>()
        val seen = HashSet<String>()
        for (g in list) {
            val go = g as? JsonObject ?: continue
            val fn = go["function"]?.s()
            // 没写 function 时也接受，避免接口不带该字段时整体为空
            if (fn != null && fn != "solo_coder") continue
            val ms = go["models"] as? JsonArray ?: continue
            for (m in ms) {
                val mo = m as? JsonObject ?: continue
                val name = mo["name"]?.s() ?: continue
                if (!seen.add(name)) continue
                out.add(
                    ModelItem(
                        name = name,
                        displayName = mo["display_name"]?.s() ?: name,
                        isDefault = mo["is_default"]?.s() == "true",
                        provider = mo["provider"]?.s() ?: "",
                    )
                )
            }
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    /** 在线 CLI 列表，用于取本机模型。返回 (cliId, cliName) */
    fun onlineClis(): List<Pair<String, String>> {
        val r = get(Api.HOST_AI, Api.PATH_CLIS)
        if (!r.ok) return emptyList()
        return try {
            val arr = json.parseToJsonElement(r.body).jsonObject["data"]?.jsonObject
                ?.get("clis") as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                if (o["status"]?.s() != "online") return@mapNotNull null
                val id = o["id"]?.s() ?: return@mapNotNull null
                id to (o["name"]?.s() ?: "本机")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---------- 消息 ----------

    /**
     * 历史消息。
     *
     * 实测硬约束：
     *  - before_limit 合法上限是 20，传 30/40 会直接 400
     *    （code 4001 "validating: expr_path=BeforeLimit, cause=invalid"）
     *  - after_limit 任意非负值都可，跟 before_limit 组合时同样受上限约束
     *  - 不传 anchor_created_at_ms 时从最新往旧取
     */
    fun fetchMessagesRaw(convId: String, limit: Int = 20): String? {
        val safe = limit.coerceIn(1, 20)
        val r = get(
            Api.HOST_AI,
            "${Api.PATH_MSG_ANCHOR}?conversation_id=$convId" +
                "&before_limit=$safe&after_limit=0&include_anchor=false",
        )
        return if (r.ok && r.body.isNotBlank()) r.body else null
    }

    /** 带诊断信息的历史消息请求，便于把真实错误码展示给用户 */
    fun fetchMessages(convId: String, limit: Int = 20): Result {
        val safe = limit.coerceIn(1, 20)
        return get(
            Api.HOST_AI,
            "${Api.PATH_MSG_ANCHOR}?conversation_id=$convId" +
                "&before_limit=$safe&after_limit=0&include_anchor=false",
        )
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

    /** 置顶/取消置顶（反编译 ConversationMutation.SetPinned） */
    fun setPinned(convId: String, pinned: Boolean): Result {
        val path = if (pinned) Api.PATH_PIN else Api.PATH_UNPIN
        return post(Api.HOST_AI, path, """{"conversationId":"$convId"}""")
    }

    /** 删除会话（DELETE，body 为 conversationId） */
    fun deleteConversation(convId: String): Result =
        exec(
            Request.Builder()
                .url(Api.HOST_AI + Api.PATH_CONVERSATIONS)
                .delete("""{"conversationId":"$convId"}""".toRequestBody(jsonMedia))
        )

    /** 搜索会话（query: keyword + page） */
    fun searchConversations(keyword: String, pageSize: Int = 20, pageIndex: Int = 0): List<ConvItem> {
        val kw = java.net.URLEncoder.encode(keyword, "UTF-8")
        val r = get(
            Api.HOST_AI,
            "${Api.PATH_SEARCH}?keyword=$kw&page_size=$pageSize&page_index=$pageIndex",
        )
        if (!r.ok) return emptyList()
        return try {
            val data = json.parseToJsonElement(r.body).jsonObject["data"]?.jsonObject ?: return emptyList()
            val arr = data["conversations"] as? JsonArray
                ?: data["items"] as? JsonArray
                ?: return emptyList()
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
                    pinned = o["is_pinned"]?.s() == "true",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
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
