package com.trae.watch.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 极简 API 客户端。
 *
 * 只解析手表端需要展示的字段，不做完整 DTO 映射，
 * 避免为 41 个字段的消息结构写一堆 serializer。
 */
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

    // ---------- 具体接口 ----------

    /** 会话列表（CLI 列表，含 online 状态） */
    fun listClis(): List<RemoteCli> {
        val r = get(Api.HOST_AI, Api.PATH_CLIS)
        if (!r.ok) return emptyList()
        return try {
            val root = json.parseToJsonElement(r.body).jsonObject
            val data = root["data"]?.jsonObject ?: return emptyList()
            val arr = data["clis"]?.let { it as? JsonObject }?.get("clis") ?: data["clis"]
            parseCliArray(arr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseCliArray(el: JsonElement?): List<RemoteCli> {
        if (el == null) return emptyList()
        val arr = el as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            RemoteCli(
                id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = o["name"]?.jsonPrimitive?.content ?: "",
                status = o["status"]?.jsonPrimitive?.content ?: "",
                deviceId = o["device_id"]?.jsonPrimitive?.content ?: "",
            )
        }
    }

    /** 拉取会话消息（锚点分页） */
    fun fetchMessages(convId: String, limit: Int = 40): String? {
        val r = get(Api.HOST_AI, "${Api.PATH_MSG_ANCHOR}?chat_session_id=$convId&limit=$limit")
        return if (r.ok) r.body else null
    }

    /** 发消息 */
    fun sendMessage(convId: String, text: String): Result {
        val quoted = quote(text)
        val payload = """{"content":[{"type":"text","text":$quoted}],""" +
            """"query":$quoted,"model_name":"","agent_type":"solo_coder","model_selection_strategy":"auto"}"""
        return post(Api.HOST_AI, Api.sendPath(convId), payload)
    }

    /** 最小 JSON 字符串转义，避免为一次拼接引入 serializer */
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

    /** 审批决策 */
    fun sendDecision(convId: String, taskId: String, approved: Boolean): Result {
        val payload = """{"chat_session_id":"$convId","task_id":"$taskId","decision":${if (approved) 1 else 2}}"""
        return post(Api.HOST_AI, Api.PATH_USER_DECISION, payload)
    }

    /** 取消任务 */
    fun cancelTask(convId: String, taskId: String): Result {
        val payload = """{"chat_session_id":"$convId","task_id":"$taskId"}"""
        return post(Api.HOST_AI, Api.PATH_CANCEL_TASK, payload)
    }
}

data class RemoteCli(
    val id: String,
    val name: String,
    val status: String,
    val deviceId: String,
)
