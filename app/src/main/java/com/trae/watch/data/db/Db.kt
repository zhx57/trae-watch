package com.trae.watch.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 手写 SQLite，不用 Room。
 * 理由：API 27 低端机上 Room 的注解处理与反射初始化成本明显，而这里只有两张表。
 */
class Db(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    companion object {
        private const val NAME = "trae_watch.db"
        private const val VERSION = 1

        const val T_CONV = "conversation"
        const val T_MSG = "message"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $T_CONV (
              id            TEXT PRIMARY KEY NOT NULL,
              title         TEXT,
              status        TEXT,
              updated_at    INTEGER NOT NULL DEFAULT 0,
              unread        INTEGER NOT NULL DEFAULT 0,
              last_snippet  TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_conv_updated ON $T_CONV(updated_at DESC)")

        db.execSQL(
            """
            CREATE TABLE $T_MSG (
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
        db.execSQL("CREATE INDEX idx_msg_conv ON $T_MSG(conv_id, seq ASC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 手表端数据可丢，直接重建
        db.execSQL("DROP TABLE IF EXISTS $T_MSG")
        db.execSQL("DROP TABLE IF EXISTS $T_CONV")
        onCreate(db)
    }
}
