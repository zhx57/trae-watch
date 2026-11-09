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
    val currentConv: ConvItem? = null,
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
            currentConv = c,
            messages = emptyList(),
            error = "",
        )
        viewModelScope.launch(Dispatchers.IO) {
            // 补齐 cli_id / project_id / workspace（发消息必需）
            var conv = c
            if (c.source == ConvSource.LOCAL && (c.cliId.isBlank() || c.projectId.isBlank())) {
                graph.api.conversationDetail(c.id)?.let { conv = it.copy(title = c.title) }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(currentConv = conv)
                }
            }
            val local = graph.store.messages(c.id)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(messages = local)
            }
            // 拉远端历史
            val raw = runCatching { graph.api.fetchMessagesRaw(c.id) }.getOrNull()
            if (raw != null) {
                val parsed = MessageParser.parse(c.id, raw)
                if (parsed.isNotEmpty()) {
                    graph.store.upsertMessages(parsed)
                    val all = graph.store.messages(c.id)
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(messages = all, error = "")
                    }
                    return@launch
                }
            }
            withContext(Dispatchers.Main) {
                if (graph.store.messages(c.id).isEmpty()) {
                    _state.value = _state.value.copy(error = "历史消息加载失败")
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
        val conv = _state.value.currentConv
        if (conv == null && _state.value.currentConvId.isEmpty()) return
        if (text.isBlank()) return
        val convId = conv?.id ?: _state.value.currentConvId
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
            error = "",
        )
        val model = _state.value.selectedModel
        viewModelScope.launch(Dispatchers.IO) {
            graph.store.upsertMessages(listOf(localMsg))
            // 云端会话先走 remote 详情补齐，本地会话直接 createTask
            val target = conv ?: graph.api.conversationDetail(convId)
            if (target == null) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(error = "会话信息缺失，无法发送")
                }
                return@launch
            }
            val r = graph.api.createTask(target, text, model)
            withContext(Dispatchers.Main) {
                val updated = _state.value.messages.map {
                    if (it.id == localMsg.id) it.copy(status = if (r.bizOk) "sent" else "failed") else it
                }
                _state.value = _state.value.copy(
                    messages = updated,
                    error = if (r.bizOk) "" else "发送失败: ${r.bizMessage.ifBlank { "HTTP ${r.code}" }}",
                )
            }
            if (r.bizOk) startSse(convId)
        }
    }

    // ---------------- 实时更新（轮询） ----------------

    private var pollJob: kotlinx.coroutines.Job? = null

    /**
     * 发完消息后启动轮询刷新历史。
     *
     * SSE 事件端点参数尚未确认（实测 400），暂用 3 秒轮询拉取 anchor，
     * 拿到新消息就增量入库。功能正确性优先，后续确认 SSE 参数再切换。
     */
    fun startSse(convId: String) {
        if (convId.isEmpty()) return
        stopSse()
        _state.value = _state.value.copy(sseState = SseState.CONNECTED)
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            var idle = 0
            while (true) {
                kotlinx.coroutines.delay(3000)
                val raw = runCatching { graph.api.fetchMessagesRaw(convId) }.getOrNull()
                if (raw == null) {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(sseState = SseState.DISCONNECTED)
                    }
                    idle++
                    if (idle > 5) break
                    continue
                }
                val parsed = MessageParser.parse(convId, raw)
                val before = _state.value.messages.size
                if (parsed.isNotEmpty()) {
                    graph.store.upsertMessages(parsed)
                    val all = graph.store.messages(convId)
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(messages = all, sseState = SseState.CONNECTED)
                    }
                    if (all.size != before) idle = 0 else idle++
                } else {
                    idle++
                }
                // 连续 20 次（约 1 分钟）无变化就停
                if (idle > 20) break
            }
        }
    }

    fun stopSse() {
        pollJob?.cancel()
        pollJob = null
        _state.value = _state.value.copy(sseState = SseState.DISCONNECTED)
    }

    private fun handleEvent(e: SseEvent) {
        // 保留：SSE 端点参数确认后启用
    }

    private fun appendMsg(e: SseEvent, type: String, text: String) {
        // 保留：SSE 端点参数确认后启用
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
