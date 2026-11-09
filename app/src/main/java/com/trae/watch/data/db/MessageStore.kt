package com.trae.watch.data.db

import android.content.ContentValues
import android.database.Cursor

/** 会话与消息的读写。全部操作在 IO 线程调用。 */
class MessageStore(private val db: Db) {

    fun upsertConversations(list: List<Conversation>) {
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
                w.insertWithOnConflict(Db.T_CONV, null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    fun conversations(limit: Int = 30): List<Conversation> {
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
        return out
    }

    fun conversation(id: String): Conversation? {
        db.readableDatabase.rawQuery(
            "SELECT id,title,status,updated_at,unread,last_snippet FROM ${Db.T_CONV} WHERE id=?",
            arrayOf(id),
        ).use { c ->
            if (c.moveToFirst()) {
                return Conversation(
                    id = c.str(0) ?: id,
                    title = c.str(1) ?: "未命名",
                    status = c.str(2) ?: "",
                    updatedAt = c.long(3),
                    unread = c.int(4),
                    lastSnippet = c.str(5) ?: "",
                )
            }
        }
        return null
    }

    fun upsertMessages(list: List<Message>) {
        if (list.isEmpty()) return
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
                w.insertWithOnConflict(Db.T_MSG, null, v, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** 只取最近 N 条，避免低端机一次性加载全量 */
    fun messages(convId: String, limit: Int = 40): List<Message> {
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
        return out
    }

    fun markRead(convId: String) {
        val v = ContentValues().apply { put("unread", 0) }
        db.writableDatabase.update(Db.T_CONV, v, "id=?", arrayOf(convId))
    }

    fun deleteConversation(convId: String) {
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.delete(Db.T_MSG, "conv_id=?", arrayOf(convId))
            w.delete(Db.T_CONV, "id=?", arrayOf(convId))
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    private fun Cursor.str(i: Int): String? = if (isNull(i)) null else getString(i)
    private fun Cursor.int(i: Int): Int = if (isNull(i)) 0 else getInt(i)
    private fun Cursor.long(i: Int): Long = if (isNull(i)) 0L else getLong(i)
}
