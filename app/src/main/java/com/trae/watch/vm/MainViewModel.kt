package com.trae.watch.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trae.watch.data.auth.AppGraph
import com.trae.watch.data.auth.PairPayload
import com.trae.watch.data.db.Conversation
import com.trae.watch.data.db.Message
import com.trae.watch.data.net.SseEvent
import com.trae.watch.data.net.SseReader
import com.trae.watch.data.net.SseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 当前屏幕 */
enum class Screen { LIST, CHAT, APPROVAL, INPUT, TASK, PAIR }

/** 待审批的请求 */
data class Approval(
    val convId: String,
    val taskId: String,
    val toolName: String,
    val summary: String,
    val fullText: String,
)

data class UiState(
    val screen: Screen = Screen.LIST,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<Message> = emptyList(),
    val currentConvId: String = "",
    val currentTitle: String = "",
    val approval: Approval? = null,
    val sseState: SseState = SseState.DISCONNECTED,
    val runningTaskId: String = "",
    val runningSince: Long = 0L,
    val pairing: Boolean = false,
    val pairError: String = "",
    val hasAccount: Boolean = false,
    val screenName: String = "",
    val loading: Boolean = false,
)

class MainViewModel(private val graph: AppGraph) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(hasAccount = graph.token.hasAccount, screenName = graph.token.screenName)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var sse: SseReader? = null

    init {
        if (graph.token.hasAccount) {
            // 先进列表，数据在后台补
            viewModelScope.launch(Dispatchers.IO) { loadConversations() }
        } else {
            _state.value = _state.value.copy(screen = Screen.PAIR)
        }
    }

    // ---------------- 列表 ----------------

    fun loadConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            val local = graph.store.conversations()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(conversations = local, loading = false)
            }
            // 远端 CLI 状态作为补充
            runCatching {
                val remote = graph.api.listClis()
                if (remote.isEmpty()) return@runCatching
                val now = System.currentTimeMillis()
                val mapped = remote.map { c ->
                    Conversation(
                        id = c.id,
                        title = c.name.ifBlank { "设备 ${c.id.take(6)}" },
                        status = c.status,
                        updatedAt = now,
                        unread = 0,
                        lastSnippet = if (c.status == "online") "设备在线" else "设备离线",
                    )
                }
                graph.store.upsertConversations(mapped)
                val all = graph.store.conversations()
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(conversations = all)
                }
            }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        loadConversations()
    }

    // ---------------- 会话 ----------------

    fun openConversation(id: String, title: String) {
        _state.value = _state.value.copy(
            screen = Screen.CHAT,
            currentConvId = id,
            currentTitle = title,
            messages = emptyList(),
        )
        viewModelScope.launch(Dispatchers.IO) {
            graph.store.markRead(id)
            val local = graph.store.messages(id)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(messages = local)
            }
            // 远端拉取补充
            runCatching {
                val body = graph.api.fetchMessages(id) ?: return@runCatching
                val parsed = MessageParser.parse(id, body)
                if (parsed.isNotEmpty()) {
                    graph.store.upsertMessages(parsed)
                    val all = graph.store.messages(id)
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(messages = all)
                    }
                }
            }
        }
    }

    fun backToList() {
        stopSse()
        _state.value = _state.value.copy(screen = Screen.LIST, currentConvId = "")
        loadConversations()
    }

    fun openInput() {
        _state.value = _state.value.copy(screen = Screen.INPUT)
    }

    fun openTask() {
        _state.value = _state.value.copy(screen = Screen.TASK)
    }

    fun openApproval(a: Approval) {
        _state.value = _state.value.copy(screen = Screen.APPROVAL, approval = a)
    }

    // ---------------- 发送 ----------------

    fun send(text: String) {
        val convId = _state.value.currentConvId
        if (convId.isEmpty() || text.isBlank()) return
        val localMsg = Message(
            id = "local-${System.currentTimeMillis()}",
            convId = convId,
            seq = Int.MAX_VALUE,
            role = "user",
            type = "text",
            text = text,
            status = "sending",
            createdAt = System.currentTimeMillis(),
        )
        _state.value = _state.value.copy(
            screen = Screen.CHAT,
            messages = _state.value.messages + localMsg,
        )
        viewModelScope.launch(Dispatchers.IO) {
            graph.store.upsertMessages(listOf(localMsg.copy(id = localMsg.id)))
            val r = graph.api.sendMessage(convId, text)
            withContext(Dispatchers.Main) {
                val updated = _state.value.messages.map {
                    if (it.id == localMsg.id) it.copy(status = if (r.ok) "sent" else "failed") else it
                }
                _state.value = _state.value.copy(messages = updated)
            }
            if (r.ok) startSse(convId)
        }
    }

    // ---------------- SSE ----------------

    fun startSse(convId: String) {
        if (convId.isEmpty()) return
        stopSse()
        val reader = SseReader(
            tokenProvider = { graph.token.accessToken() },
            onEvent = { e -> handleEvent(e) },
            onState = { s ->
                viewModelScope.launch(Dispatchers.Main) {
                    _state.value = _state.value.copy(sseState = s)
                }
            },
        )
        sse = reader
        reader.start(convId)
    }

    fun stopSse() {
        sse?.stop()
        sse = null
        _state.value = _state.value.copy(sseState = SseState.DISCONNECTED)
    }

    private fun handleEvent(e: SseEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            when (e.type) {
                "msg" -> {
                    if (e.text.isNotBlank()) {
                        val m = Message(
                            id = "sse-${e.seq}-${System.currentTimeMillis()}",
                            convId = _state.value.currentConvId,
                            seq = e.seq,
                            role = "assistant",
                            type = "text",
                            text = e.text,
                            status = "streaming",
                            createdAt = System.currentTimeMillis(),
                        )
                        _state.value = _state.value.copy(messages = _state.value.messages + m)
                    }
                }
                "tool_call", "tool" -> {
                    val toolName = e.text.ifBlank { "工具调用" }
                    // 需要审批的工具先弹审批页
                    if (e.raw.contains("\"require_confirm\"") || e.raw.contains("\"confirm\"") ||
                        e.raw.contains("\"need_approval\"")
                    ) {
                        _state.value = _state.value.copy(
                            approval = Approval(
                                convId = _state.value.currentConvId,
                                taskId = e.taskId,
                                toolName = toolName,
                                summary = toolName,
                                fullText = e.raw,
                            ),
                            screen = Screen.APPROVAL,
                        )
                    } else {
                        val m = Message(
                            id = "tool-${e.seq}-${System.currentTimeMillis()}",
                            convId = _state.value.currentConvId,
                            seq = e.seq,
                            role = "assistant",
                            type = "tool",
                            text = toolName,
                            status = "running",
                            createdAt = System.currentTimeMillis(),
                        )
                        _state.value = _state.value.copy(messages = _state.value.messages + m)
                    }
                }
                "plan_item" -> {
                    val m = Message(
                        id = "plan-${e.seq}-${System.currentTimeMillis()}",
                        convId = _state.value.currentConvId,
                        seq = e.seq,
                        role = "assistant",
                        type = "plan",
                        text = e.text,
                        status = "",
                        createdAt = System.currentTimeMillis(),
                    )
                    _state.value = _state.value.copy(messages = _state.value.messages + m)
                }
                "done" -> {
                    _state.value = _state.value.copy(runningTaskId = "", runningSince = 0L)
                }
                "error" -> {
                    val m = Message(
                        id = "err-${System.currentTimeMillis()}",
                        convId = _state.value.currentConvId,
                        seq = e.seq,
                        role = "system",
                        type = "error",
                        text = e.text.ifBlank { "任务出错" },
                        status = "error",
                        createdAt = System.currentTimeMillis(),
                    )
                    _state.value = _state.value.copy(messages = _state.value.messages + m)
                }
                "queue" -> {
                    _state.value = _state.value.copy(runningTaskId = e.taskId)
                }
            }
        }
    }

    // ---------------- 审批 ----------------

    fun decide(approved: Boolean) {
        val a = _state.value.approval ?: return
        _state.value = _state.value.copy(screen = Screen.CHAT, approval = null)
        viewModelScope.launch(Dispatchers.IO) {
            graph.api.sendDecision(a.convId, a.taskId, approved)
        }
    }

    fun cancelTask() {
        val convId = _state.value.currentConvId
        val taskId = _state.value.runningTaskId
        if (convId.isEmpty() || taskId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { graph.api.cancelTask(convId, taskId) }
        _state.value = _state.value.copy(runningTaskId = "", runningSince = 0L)
    }

    // ---------------- 配对 ----------------

    fun pairByCode(host: String, port: String, code: String) {
        _state.value = _state.value.copy(pairing = true, pairError = "")
        viewModelScope.launch(Dispatchers.IO) {
            val p: PairPayload? = runCatching {
                com.trae.watch.data.auth.PairFetcher.fetch(host, port.toIntOrNull() ?: 8080, code)
            }.getOrNull()
            val ok = p != null && graph.token.importFromPair(p)
            withContext(Dispatchers.Main) {
                _state.value = if (ok) {
                    _state.value.copy(
                        pairing = false, hasAccount = true,
                        screen = Screen.LIST, screenName = graph.token.screenName,
                    )
                } else {
                    _state.value.copy(pairing = false, pairError = "配对失败，请检查地址与配对码")
                }
            }
            if (ok) loadConversations()
        }
    }

    fun pairByPayload(raw: String) {
        val p = com.trae.watch.data.auth.PairDecoder.decode(raw)
        if (p == null) {
            _state.value = _state.value.copy(pairError = "载荷格式不正确")
            return
        }
        _state.value = _state.value.copy(pairing = true, pairError = "")
        viewModelScope.launch(Dispatchers.IO) {
            val ok = graph.token.importFromPair(p)
            withContext(Dispatchers.Main) {
                _state.value = if (ok) {
                    _state.value.copy(
                        pairing = false, hasAccount = true,
                        screen = Screen.LIST, screenName = graph.token.screenName,
                    )
                } else {
                    _state.value.copy(pairing = false, pairError = "凭证无效")
                }
            }
            if (ok) loadConversations()
        }
    }

    override fun onCleared() {
        stopSse()
        super.onCleared()
    }
}
