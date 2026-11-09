package com.trae.watch.data.auth

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 配对载荷解析。
 *
 * 载荷格式（由 pair/pair.py 生成）：
 *   base64url(json)
 *   json = {"v":1,"rt":"...","did":"...","uid":"...","name":"...","tid":"...","exp":0}
 *
 * 不含签名校验：载荷只在本地局域网点对点传输，且不落盘明文的场景下，
 * 引入 HMAC 需要额外分发密钥，收益不足以抵消复杂度。
 */
object PairDecoder {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decode(raw: String): PairPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // 支持两种输入：纯 base64 载荷，或 pair.py 输出的紧凑格式 TRAE1:<base64>
        val payloadPart = trimmed.removePrefix("TRAE1:")
        return try {
            val bytes = Base64.decode(payloadPart, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val o = json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
            val rt = o["rt"]?.jsonPrimitive?.content ?: return null
            PairPayload(
                refreshToken = rt,
                boundDeviceId = o["did"]?.jsonPrimitive?.content ?: "",
                userId = o["uid"]?.jsonPrimitive?.content ?: "",
                screenName = o["name"]?.jsonPrimitive?.content ?: "",
                tenantId = o["tid"]?.jsonPrimitive?.content ?: "",
                expiresAt = o["exp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            null
        }
    }
}
