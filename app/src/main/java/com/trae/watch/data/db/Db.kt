package com.trae.watch.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * 手写 SQLite，不用 Room。
 * 理由：API 27 低端机上 Room 的注解处理与反射初始化成本明显，而这里只有两张表。
 *
 * 容错：本地库只是缓存，任何打开/读写失败都不允许影响主流程。
 * 实测在部分 Android 8.1 手表 ROM 上会抛
 *   SQLiteCantOpenDatabaseException: unknown error (code 14): Could not open database
 * 常见成因是 db 文件损坏或数据库目录不可写。这里提供一次性自愈：
 * 关闭连接 -> 删除残留文件 -> 下次访问重新建库。
 */
class Db(private val ctx: Context) : SQLiteOpenHelper(ctx, NAME, null, VERSION) {

    companion object {
        private const val TAG = "TraeDb"
        private const val NAME = "trae_watch.db"
        private const val VERSION = 1

        const val T_CONV = "conversation"
        const val T_MSG = "message"
    }

    /** 自愈后仍失败则置位，后续所有访问直接走降级路径，不再反复尝试 */
    @Volatile
    private var broken = false

    val isBroken: Boolean get() = broken

    fun markBroken(reason: Throwable?) {
        Log.w(TAG, "database unavailable, falling back to no-cache: ${reason?.message}")
        broken = true
    }

    /** 删除损坏的库文件，允许下一次访问重建 */
    fun recover(): Boolean {
        return try {
            runCatching { close() }
            val ok = ctx.deleteDatabase(NAME)
            broken = false
            Log.i(TAG, "database recovered, deleted=$ok")
            ok
        } catch (e: Throwable) {
            Log.w(TAG, "database recover failed: ${e.message}")
            false
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_CONV (
              id            TEXT PRIMARY KEY NOT NULL,
              title         TEXT,
              status        TEXT,
              updated_at    INTEGER NOT NULL DEFAULT 0,
              unread        INTEGER NOT NULL DEFAULT 0,
              last_snippet  TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conv_updated ON $T_CONV(updated_at DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $T_MSG (
              id          TEXT PRIMARY KEY NOT NULL,
              conv_id     TEXT NOT NULL,
              seq         INTEGER NOT NULL DEFAULT 0,
              role        TEXT NOT NULL,
              type        TEXT NOT NULL,
              text        TEXT,
              status      TEXT,
              created_at  INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_conv ON $T_MSG(conv_id, seq ASC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 手表端数据可丢，直接重建
        db.execSQL("DROP TABLE IF EXISTS $T_MSG")
        db.execSQL("DROP TABLE IF EXISTS $T_CONV")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }
}
