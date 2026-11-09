package com.trae.watch.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共享的 OkHttp 实例。
 *
 * 低端机上的取舍：
 *  - 连接池压到 3 个，避免内存占用
 *  - 关闭自动重试，由业务层指数退避控制
 *  - SSE 读超时设为 0（长连接不超时），其余请求 20 秒
 */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .connectionPool(okhttp3.ConnectionPool(3, 5, TimeUnit.MINUTES))
            .build()
    }

    /** SSE 专用：读超时无限，其余同上 */
    val sseClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    fun headers(token: String): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "X-App-Id" to Api.APP_ID,
        "X-App-Version-Code" to Api.APP_VERSION_CODE,
        "User-Agent" to "okhttp/4.12.0",
        "Authorization" to "Cloud-IDE-JWT $token",
    )
}
