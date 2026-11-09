package com.trae.watch.data.db

/** 会话 */
data class Conversation(
    val id: String,
    val title: String,
    val status: String,
    val updatedAt: Long,
    val unread: Int,
    val lastSnippet: String,
)

/** 消息：手表端只关心文本与工具摘要 */
data class Message(
    val id: String,
    val convId: String,
    val seq: Int,
    val role: String,      // user / assistant / system
    val type: String,      // text / tool / plan / thought / error
    val text: String,
    val status: String,
    val createdAt: Long,
)
