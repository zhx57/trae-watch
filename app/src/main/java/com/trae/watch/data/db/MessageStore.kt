package com.trae.watch.data.db

import android.content.ContentValues
import android.database.Cursor
import android.util.Log

/**
 * 会话与消息的读写。全部操作在 IO 线程调用。
 *
 * 关键约束：本地库是缓存，不是事实来源。
 * 任何 SQLite 异常（典型是 Android 8.1 手表上的
 * SQLiteCantOpenDatabaseException code 14「Could not open database」）
 * 都必须被吞掉并降级，否则点开对话会直接崩。
 *
 * 降级策略：
 *  1. 第一次失败 -> 删库重建，重试一次
 *  2. 仍失败 -> 标记 broken，此后所有读写直接返回空，不再访问磁盘
 *  3. 写失败不影响内存里的消息列表，UI 仍可显示远端拉取的内容
 */
class MessageStore(private val db: Db) {

    companion object {
        private const val TAG = "TraeStore"
    }

    /** 统一的执行包装：失败自愈一次，再失败就降级 */
    private fun <T> guard(fallback: T, block: () -> T): T {
        if (db.isBroken) return fallback
        return try {
            block()
        } catch (e: Throwable) {
            Log.w(TAG, "db access failed: ${e.javaClass.simpleName} ${e.message}")
            if (db.recover()) {
                try {
                    return block()
                } catch (e2: Throwable) {
                    Log.w(TAG, "retry after recover failed: ${e2.message}")
                }
            }
            db.markBroken(e)
            fallback
        }
    }

    fun upsertConversations(list: List<Conversation>) {
        if (list.isEmpty()) return
        guard(Unit) {
            val w = db.writableDatabase
            w.beginTransaction()
            try {
                for (c in list) {
                    val v = ContentValues().apply {
                        put("id", c.id)
                        put("title", c.title)
                        put("status", c.status)
                        put("updated_at", c.updatedAt)
                        put("unread", c.unread)
                        put("last_snippet", c.lastSnippet)
                    }
                    w.insertWithOnConflict(
                        Db.T_CONV, null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                w.setTransactionSuccessful()
            } finally {
                runCatching { w.endTransaction() }
            }
        }
    }

    fun conversations(limit: Int = 30): List<Conversation> = guard(emptyList()) {
        val out = ArrayList<Conversation>(limit)
        db.readableDatabase.rawQuery(
            "SELECT id,title,status,updated_at,unread,last_snippet FROM ${Db.T_CONV} ORDER BY updated_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Conversation(
                        id = c.str(0) ?: "",
                        title = c.str(1) ?: "未命名",
                        status = c.str(2) ?: "",
                        updatedAt = c.long(3),
                        unread = c.int(4),
                        lastSnippet = c.str(5) ?: "",
                    )
                )
            }
        }
        out
    }

    fun conversation(id: String): Conversation? = guard<Conversation?>(null) {
        var result: Conversation? = null
        db.readableDatabase.rawQuery(
            "SELECT id,title,status,updated_at,unread,last_snippet FROM ${Db.T_CONV} WHERE id=?",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) {
                result = Conversation(
                    id = c.str(0) ?: id,
                    title = c.str(1) ?: "未命名",
                    status = c.str(2) ?: "",
                    updatedAt = c.long(3),
                    unread = c.int(4),
                    lastSnippet = c.str(5) ?: "",
                )
            }
        }
        result
    }

    fun upsertMessages(list: List<Message>) {
        if (list.isEmpty()) return
        guard(Unit) {
            val w = db.writableDatabase
            w.beginTransaction()
            try {
                for (m in list) {
                    val v = ContentValues().apply {
                        put("id", m.id)
                        put("conv_id", m.convId)
                        put("seq", m.seq)
                        put("role", m.role)
                        put("type", m.type)
                        put("text", m.text)
                        put("status", m.status)
                        put("created_at", m.createdAt)
                    }
                    w.insertWithOnConflict(
                        Db.T_MSG, null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
                w.setTransactionSuccessful()
            } finally {
                runCatching { w.endTransaction() }
            }
        }
    }

    /** 只取最近 N 条，避免低端机一次性加载全量 */
    fun messages(convId: String, limit: Int = 40): List<Message> = guard(emptyList()) {
        val out = ArrayList<Message>(limit)
        db.readableDatabase.rawQuery(
            "SELECT id,conv_id,seq,role,type,text,status,created_at FROM ${Db.T_MSG} " +
                "WHERE conv_id=? ORDER BY seq DESC LIMIT ?",
            arrayOf(convId, limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Message(
                        id = c.str(0) ?: "",
                        convId = c.str(1) ?: convId,
                        seq = c.int(2),
                        role = c.str(3) ?: "assistant",
                        type = c.str(4) ?: "text",
                        text = c.str(5) ?: "",
                        status = c.str(6) ?: "",
                        createdAt = c.long(7),
                    )
                )
            }
        }
        out.reverse()
        out
    }

    fun markRead(convId: String) {
        guard(Unit) {
            val v = ContentValues().apply { put("unread", 0) }
            db.writableDatabase.update(Db.T_CONV, v, "id=?", arrayOf(convId))
        }
    }

    fun deleteConversation(convId: String) {
        guard(Unit) {
            val w = db.writableDatabase
            w.beginTransaction()
            try {
                w.delete(Db.T_MSG, "conv_id=?", arrayOf(convId))
                w.delete(Db.T_CONV, "id=?", arrayOf(convId))
                w.setTransactionSuccessful()
            } finally {
                runCatching { w.endTransaction() }
            }
        }
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
    private fun Cursor.int(i: Int): Int = if (isNull(i)) 0 else getInt(i)
    private fun Cursor.long(i: Int): Long = if (isNull(i)) 0L else getLong(i)
}
