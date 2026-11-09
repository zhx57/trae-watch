package com.trae.watch.data.net

import android.content.Context

/**
 * 模型列表缓存 + 选中项持久化。
 *
 * 设计：
 *  - 原始 JSON 存 SharedPreferences，冷启动直接解析出列表，秒开
 *  - 进入模型页时后台静默拉取，成功后覆盖缓存
 *  - 选中的模型名单独存，重启后保持
 */
class ModelCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("trae_models", Context.MODE_PRIVATE)

    companion object {
        private const val K_RAW = "raw_json"
        private const val K_SELECTED = "selected"
        private const val K_UPDATED_AT = "updated_at"
        /** 缓存超过 10 分钟视为过期，进页面时必须刷新 */
        private const val STALE_MS = 10 * 60 * 1000L
    }

    fun cachedRaw(): String? = prefs.getString(K_RAW, null)

    fun saveRaw(raw: String) {
        prefs.edit()
            .putString(K_RAW, raw)
            .putLong(K_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun isStale(): Boolean {
        val t = prefs.getLong(K_UPDATED_AT, 0L)
        return System.currentTimeMillis() - t > STALE_MS
    }

    fun selected(): String = prefs.getString(K_SELECTED, "") ?: ""

    fun saveSelected(name: String) {
        prefs.edit().putString(K_SELECTED, name).apply()
    }
}
