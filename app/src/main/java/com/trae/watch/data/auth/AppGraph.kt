package com.trae.watch.data.auth

import android.content.Context
import com.trae.watch.data.db.Db
import com.trae.watch.data.db.MessageStore
import com.trae.watch.data.net.ApiClient
import com.trae.watch.data.net.ModelCache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通过 6 位配对码从局域网脚本拉取配对载荷。
 *
 * 协议：POST http://<host>:<port>/pair  {"code":"123456"}
 * 返回：{"payload":"TRAE1:<base64>"}
 */
object PairFetcher {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fetch(host: String, port: Int, code: String): PairPayload? {
        return try {
            val media = "application/json; charset=utf-8".toMediaType()
            val body = """{"code":"$code"}""".toRequestBody(media)
            val req = Request.Builder()
                .url("http://$host:$port/pair")
                .post(body)
                .build()
            com.trae.watch.data.net.Http.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val text = r.body?.string() ?: return null
                val payload = json.parseToJsonElement(text).jsonObject["payload"]
                    ?.jsonPrimitive?.content ?: return null
                PairDecoder.decode(payload)
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** 全局单例容器，避免引入 DI 框架的启动开销 */
class AppGraph(context: Context) {
    private val app = context.applicationContext
    val token = TokenManager(app)
    val store = MessageStore(Db(app))
    val api = ApiClient { token.accessToken() }
    val modelCache = ModelCache(app)
}
