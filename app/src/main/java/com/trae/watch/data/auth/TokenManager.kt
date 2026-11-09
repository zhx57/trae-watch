package com.trae.watch.data.auth

import android.content.Context
import com.trae.watch.data.net.Api
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.atomic.AtomicLong

/**
 * token 生命周期管理。
 *
 * 关键约束（实测得出）：
 *  1. refresh_token 是轮换制，每次 ExchangeToken 都会返回新值，必须持久化
 *  2. 三个入口（SSE 重连、前台服务、定时 Job）都可能触发刷新，
 *     用 Mutex 串行化 + 时间窗去重，避免并发刷新导致一个 token 被覆盖失效
 *  3. access token 有效期 15 天，提前 1 小时刷新
 */
class TokenManager(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val lastRefreshAt = AtomicLong(0)

    companion object {
        private const val PREF = "trae_auth"
        private const val K_RT = "refresh_token"
        private const val K_AT = "access_token"
        private const val K_EXP = "token_expire_at"
        private const val K_DEV = "bound_device_id"
        private const val K_UID = "user_id"
        private const val K_NAME = "screen_name"
        private const val K_TENANT = "tenant_id"

        private const val REFRESH_AHEAD_MS = 60L * 60 * 1000
        private const val MIN_REFRESH_INTERVAL_MS = 60L * 1000
        /** 明文兜底前缀（Keystore 不可用时） */
        private const val PLAIN_PREFIX = "plain:"
    }

    /** 纯内存缓存，避免每次请求都解密 */
    @Volatile private var cachedAccess: String? = null

    val boundDeviceId: String get() = prefs.getString(K_DEV, "") ?: ""
    val userId: String get() = prefs.getString(K_UID, "") ?: ""
    val screenName: String get() = prefs.getString(K_NAME, "") ?: ""
    val tenantId: String get() = prefs.getString(K_TENANT, "") ?: ""

    /**
     * 是否已配对。
     *
     * 关键：不能只看能否解密 —— Android 8.1 部分国产 ROM 的 Keystore 会在
     * 重启/升级后失效，导致解密返回 null，用户被迫每次重新配对。
     * 这里改为「有密文就算已配对」，解密失败时走 refresh 重试，仍失败才视为需重配。
     */
    val hasAccount: Boolean
        get() = !prefs.getString(K_RT, null).isNullOrEmpty()

    private fun refreshTokenRaw(): String? {
        val enc = prefs.getString(K_RT, null) ?: return null
        // 兼容明文存储（Keystore 不可用时的兜底）
        if (enc.startsWith(PLAIN_PREFIX)) return enc.removePrefix(PLAIN_PREFIX)
        return Crypto.decrypt(enc)
    }

    private fun storeSecret(key: String, value: String) {
        val encrypted = try {
            Crypto.encrypt(value)
        } catch (e: Throwable) {
            // Keystore 不可用，降级明文并打标记
            PLAIN_PREFIX + value
        }
        prefs.edit().putString(key, encrypted).apply()
    }

    /** 当前可用 access token，必要时自动刷新。失败返回空串。 */
    fun accessToken(): String {
        val now = System.currentTimeMillis()
        val cached = cachedAccess
        val exp = prefs.getLong(K_EXP, 0L)
        if (!cached.isNullOrEmpty() && exp - now > REFRESH_AHEAD_MS) return cached

        val stored = readSecret(K_AT)
        if (!stored.isNullOrEmpty() && exp - now > REFRESH_AHEAD_MS) {
            cachedAccess = stored
            return stored
        }
        return refreshBlocking() ?: stored.orEmpty()
    }

    private fun readSecret(key: String): String? {
        val enc = prefs.getString(key, null) ?: return null
        if (enc.startsWith(PLAIN_PREFIX)) return enc.removePrefix(PLAIN_PREFIX)
        return Crypto.decrypt(enc)
    }

    /** 阻塞式刷新，内部串行 */
    fun refreshBlocking(): String? {
        val rt = refreshTokenRaw() ?: return null
        synchronized(this) {
            val now = System.currentTimeMillis()
            // 时间窗去重：1 分钟内刚刷过，直接复用
            if (now - lastRefreshAt.get() < MIN_REFRESH_INTERVAL_MS) {
                cachedAccess?.let { return it }
                prefs.getString(K_AT, null)?.let { Crypto.decrypt(it) }?.let { return it }
            }
            val result = exchange(rt) ?: return null
            persist(result)
            lastRefreshAt.set(now)
            return result.accessToken
        }
    }

    private data class ExchangeResult(
        val accessToken: String,
        val refreshToken: String,
        val expireAt: Long,
        val boundDeviceId: String,
    )

    private fun exchange(refreshToken: String): ExchangeResult? {
        val media = "application/json; charset=utf-8".toMediaType()
        val body = """{"RefreshToken":"$refreshToken","ClientID":"${Api.CLIENT_ID}","ClientSecret":"${Api.CLIENT_SECRET}"}"""
            .toRequestBody(media)
        val req = Request.Builder()
            .url(Api.HOST_LOGIN + Api.PATH_EXCHANGE_TOKEN)
            .post(body)
            .header("Content-Type", "application/json")
            .header("X-App-Id", Api.APP_ID)
            .header("X-App-Version-Code", Api.APP_VERSION_CODE)
            .header("User-Agent", "okhttp/4.12.0")
            .build()
        return try {
            com.trae.watch.data.net.Http.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val text = r.body?.string() ?: return null
                val root = json.parseToJsonElement(text).jsonObject
                val res = root["Result"]?.jsonObject ?: return null
                val token = res["Token"]?.jsonPrimitive?.content ?: return null
                val newRt = res["RefreshToken"]?.jsonPrimitive?.content ?: refreshToken
                val exp = res["TokenExpireAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                val dev = res["BoundDeviceID"]?.jsonPrimitive?.content ?: boundDeviceId
                ExchangeResult(token, newRt, exp, dev)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun persist(r: ExchangeResult) {
        cachedAccess = r.accessToken
        storeSecret(K_AT, r.accessToken)
        storeSecret(K_RT, r.refreshToken)
        prefs.edit()
            .putLong(K_EXP, r.expireAt)
            .putString(K_DEV, r.boundDeviceId)
            .apply()
    }

    /** 从配对载荷导入（首次登录） */
    fun importFromPair(p: PairPayload): Boolean {
        if (p.refreshToken.isBlank()) return false
        storeSecret(K_RT, p.refreshToken)
        prefs.edit()
            .putString(K_DEV, p.boundDeviceId)
            .putString(K_UID, p.userId)
            .putString(K_NAME, p.screenName)
            .putString(K_TENANT, p.tenantId)
            .putLong(K_EXP, 0L)
            .apply()
        cachedAccess = null
        // 立即验证一次；失败也不清配对状态，避免用户被迫重复配对
        val ok = refreshBlocking() != null
        return ok
    }

    /** 仅清除 access token，保留 refresh token（给「重新连接」用） */
    fun clearAccessOnly() {
        cachedAccess = null
        prefs.edit().remove(K_AT).remove(K_EXP).apply()
    }

    fun logout() {
        cachedAccess = null
        prefs.edit().clear().apply()
    }
}

/** 配对载荷结构，由 pair.py 生成 */
data class PairPayload(
    val refreshToken: String,
    val boundDeviceId: String,
    val userId: String,
    val screenName: String,
    val tenantId: String,
    val expiresAt: Long = 0L,
)
