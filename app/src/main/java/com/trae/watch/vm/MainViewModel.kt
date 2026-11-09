package com.trae.watch.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trae.watch.data.auth.AppGraph
import com.trae.watch.data.auth.PairPayload
import com.trae.watch.data.db.Message
import com.trae.watch.data.net.ConvItem
import com.trae.watch.data.net.ConvSource
import com.trae.watch.data.net.ModelItem
import com.trae.watch.data.net.SseEvent
import com.trae.watch.data.net.SseReader
import com.trae.watch.data.net.SseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { LIST, CHAT, APPROVAL, INPUT, TASK, PAIR, MODELS }

data class Approval(
    val convId: String,
    val taskId: String,
    val toolName: String,
    val summary: String,
    val fullText: String,
)

data class UiState(
    val screen: Screen = Screen.LIST,
    val conversations: List<ConvItem> = emptyList(),
    val allConversations: List<ConvItem> = emptyList(),
    val messages: List<Message> = emptyList(),
    val currentConvId: String = "",
    val currentTitle: String = "",
    val currentSource: ConvSource = ConvSource.LOCAL,
    val approval: Approval? = null,
    val sseState: SseState = SseState.DISCONNECTED,
    val runningTaskId: String = "",
    val runningSince: Long = 0L,
    val pairing: Boolean = false,
    val pairError: String = "",
    val hasAccount: Boolean = false,
    val screenName: String = "",
    val loading: Boolean = false,
    val tab: Int = 0,                       // 0=本地 1=云端
    val models: List<ModelItem> = emptyList(),
    val selectedModel: String = "",
    val modelsLoading: Boolean = false,
    val modelsError: String = "",
    val modelsUpdatedAt: Long = 0L,
    val error: String = "",
)

class MainViewModel(private val graph: AppGraph) : ViewModel() {

    private val _state = MutableStateFlow(
        UiState(hasAccount = graph.token.hasAccount, screenName = graph.token.screenName)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var sse: SseReader? = null

    init {
        // 先用缓存的模型列表填充，保证冷启动即可显示
        val cachedRaw = graph.modelCache.cachedRaw()
        val cachedModels = cachedRaw?.let { graph.api.parseModels(it) } ?: emptyList()
        val cachedSelected = graph.modelCache.selected()
        _state.value = _state.value.copy(
            models = cachedModels,
            selectedModel = cachedSelected.ifBlank {
                cachedModels.firstOrNull { it.isDefault }?.name
                    ?: cachedModels.firstOrNull()?.name ?: ""
            },
        )

        if (graph.token.hasAccount) {
            viewModelScope.launch(Dispatchers.IO) {
                loadConversations()
                // 缓存过期或为空时，启动即静默刷新模型
                if (graph.modelCache.isStale() || cachedModels.isEmpty()) {
                    refreshModelsInternal()
                }
            }
        } else {
            _state.value = _state.value.copy(screen = Screen.PAIR)
        }
    }

    // ---------------- 列表 ----------------

    fun loadConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            val local = runCatching { graph.api.localConversations() }.getOrDefault(emptyList())
            val cloud = runCatching { graph.api.cloudSessions() }.getOrDefault(emptyList())
            val all = (local + cloud).sortedByDescending { it.updatedAt }
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    conversations = if (_state.value.tab == 0) local else cloud,
                    allConversations = all,
                    loading = false,
                    error = "",
                )
            }
        }
    }

    // ---------------- 模型 ----------------

    /** 进入模型页：立刻刷新，保证拿到最新列表 */
    fun openModels() {
        _state.value = _state.value.copy(screen = Screen.MODELS, modelsLoading = true)
        viewModelScope.launch(Dispatchers.IO) { refreshModelsInternal() }
    }

    /** 模型页手动刷新 */
    fun refreshModels() {
        _state.value = _state.value.copy(modelsLoading = true)
        viewModelScope.launch(Dispatchers.IO) { refreshModelsInternal() }
    }

    private suspend fun refreshModelsInternal() {
        val raw = runCatching { graph.api.modelsRaw() }.getOrNull()
        if (raw == null) {
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    modelsLoading = false,
                    modelsError = "模型列表获取失败",
                )
            }
            return
        }
        val list = graph.api.parseModels(raw)
        if (list.isEmpty()) {
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(modelsLoading = false, modelsError = "返回为空")
            }
            return
        }
        graph.modelCache.saveRaw(raw)
        withContext(Dispatchers.Main) {
            val cur = _state.value.selectedModel
            // 若之前选的模型已下架，回落到默认
            val valid = list.any { it.name == cur }
            val selected = if (valid) cur else {
                list.firstOrNull { it.isDefault }?.name ?: list.first().name
            }
            if (!valid && selected.isNotBlank()) graph.modelCache.saveSelected(selected)
            _state.value = _state.value.copy(
                models = list,
                selectedModel = selected,
                modelsLoading = false,
                modelsError = "",
                modelsUpdatedAt = System.currentTimeMillis(),
            )
        }
    }

    fun switchTab(tab: Int) {
        val all = _state.value.allConversations
        val filtered = if (tab == 0) {
            all.filter { it.source == ConvSource.LOCAL }
        } else {
            all.filter { it.source == ConvSource.CLOUD }
        }
        _state.value = _state.value.copy(tab = tab, conversations = filtered)
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        loadConversations()
    }

    // ---------------- 会话 ----------------

    fun openConversation(c: ConvItem) {
        _state.value = _state.value.copy(
            screen = Screen.CHAT,
            currentConvId = c.id,
            currentTitle = c.title,
            currentSource = c.source,
            messages = emptyList(),
        )
        viewModelScope.launch(Dispatchers.IO) {
            val local = graph.store.messages(c.id)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(messages = local)
            }
            runCatching {
                val body = graph.api.fetchMessages(c.id) ?: return@runCatching
                val parsed = MessageParser.parse(c.id, body)
                if (parsed.isNotEmpty()) {
                    graph.store.upsertMessages(parsed)
                    val all = graph.store.messages(c.id)
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

    fun openInput() { _state.value = _state.value.copy(screen = Screen.INPUT) }
    fun openTask() { _state.value = _state.value.copy(screen = Screen.TASK) }

    fun selectModel(name: String) {
        graph.modelCache.saveSelected(name)
        _state.value = _state.value.copy(selectedModel = name, screen = Screen.INPUT)
    }

    /** 从模型页返回：有当前会话则回聊天，否则回列表 */
    fun returnToListFromModels() {
        val s = _state.value
        _state.value = s.copy(
            screen = if (s.currentConvId.isBlank()) Screen.LIST else Screen.INPUT,
        )
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
        val model = _state.value.selectedModel
        viewModelScope.launch(Dispatchers.IO) {
            graph.store.upsertMessages(listOf(localMsg))
            val r = graph.api.sendMessage(convId, text, model)
            withContext(Dispatchers.Main) {
                val updated = _state.value.messages.map {
                    if (it.id == localMsg.id) it.copy(status = if (r.ok) "sent" else "failed") else it
                }
                _state.value = _state.value.copy(
                    messages = updated,
                    error = if (r.ok) "" else "发送失败 (${r.code})",
                )
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
                "msg" -> if (e.text.isNotBlank()) {
                    appendMsg(e, "text", e.text)
                }
                "tool_call", "tool" -> {
                    val toolName = e.text.ifBlank { "工具调用" }
                    val needConfirm = e.raw.contains("\"require_confirm\"") ||
                        e.raw.contains("\"need_approval\"")
                    if (needConfirm) {
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
                        appendMsg(e, "tool", toolName)
                    }
                }
                "plan_item" -> appendMsg(e, "plan", e.text)
                "done" -> _state.value = _state.value.copy(runningTaskId = "", runningSince = 0L)
                "error" -> appendMsg(e, "error", e.text.ifBlank { "任务出错" })
                "queue" -> _state.value = _state.value.copy(
                    runningTaskId = e.taskId,
                    runningSince = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun appendMsg(e: SseEvent, type: String, text: String) {
        val m = Message(
            id = "sse-${type}-${e.seq}-${System.currentTimeMillis()}",
            convId = _state.value.currentConvId,
            seq = e.seq,
            role = if (type == "error") "system" else "assistant",
            type = type,
            text = text,
            status = if (type == "error") "error" else "streaming",
            createdAt = System.currentTimeMillis(),
        )
        _state.value = _state.value.copy(messages = _state.value.messages + m)
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
