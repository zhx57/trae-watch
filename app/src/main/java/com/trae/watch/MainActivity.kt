package com.trae.watch

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trae.watch.ui.screen.ApprovalScreen
import com.trae.watch.ui.screen.ChatScreen
import com.trae.watch.ui.screen.ListScreen
import com.trae.watch.ui.screen.ModelsScreen
import com.trae.watch.ui.screen.PairScreen
import com.trae.watch.ui.screen.TaskScreen
import com.trae.watch.ui.theme.TraeWatchTheme
import com.trae.watch.vm.MainViewModel
import com.trae.watch.vm.Screen
import com.trae.watch.vm.UiState

class MainActivity : ComponentActivity() {

    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as TraeWatchApp).graph

        setContent {
            TraeWatchTheme {
                val vm: MainViewModel = viewModel { MainViewModel(graph) }
                val st by vm.state.collectAsState()

                // 返回键逐级回退：
                // 根页面（列表 / 配对）不拦截，交给系统退出；其余页面拦截并回上一级。
                val isRoot = st.screen == Screen.LIST || st.screen == Screen.PAIR
                BackHandler(enabled = !isRoot) {
                    vm.back()
                }

                Root(vm, st)
            }
        }

        if (graph.token.hasAccount) askBackgroundPermissions()
    }

    private fun askBackgroundPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }
            }
        }
        com.trae.watch.service.StreamService.start(this)
        com.trae.watch.service.PollJobService.schedule(this)
    }

    @Composable
    private fun Root(vm: MainViewModel, st: UiState) {
        val modelLabel = st.models.firstOrNull { it.name == st.selectedModel }?.displayName ?: ""
        when (st.screen) {
            Screen.PAIR -> PairScreen(
                pairing = st.pairing,
                error = st.pairError,
                onPair = vm::pairByCode,
                onPastePayload = vm::pairByPayload,
            )
            Screen.LIST -> ListScreen(
                conversations = st.conversations,
                tab = st.tab,
                loading = st.loading,
                error = st.error,
                modelName = modelLabel,
                onSwitchTab = vm::switchTab,
                onOpen = vm::openConversation,
                onRefresh = vm::refresh,
                onModels = vm::openModels,
                onTogglePin = vm::togglePin,
                onDelete = vm::deleteConversation,
            )
            Screen.CHAT -> ChatScreen(
                title = st.currentTitle,
                messages = st.messages,
                running = st.runningTaskId.isNotEmpty(),
                modelName = modelLabel,
                error = st.error,
                onBack = vm::backToList,
                onSend = vm::send,
                onPickModel = vm::openModels,
                onTask = vm::openTask,
            )
            Screen.MODELS -> ModelsScreen(
                models = st.models,
                selected = st.selectedModel,
                loading = st.modelsLoading,
                error = st.modelsError,
                updatedAt = st.modelsUpdatedAt,
                onSelect = vm::selectModel,
                onRefresh = vm::refreshModels,
                onBack = { vm.returnToListFromModels() },
            )
            Screen.APPROVAL -> ApprovalScreen(
                approval = st.approval,
                onApprove = { vm.decide(true) },
                onReject = { vm.decide(false) },
                onBack = { vm.decide(false) },
            )
            Screen.TASK -> TaskScreen(
                running = st.runningTaskId.isNotEmpty(),
                taskId = st.runningTaskId,
                startedAt = st.runningSince,
                stepText = st.messages.lastOrNull { it.type == "tool" || it.type == "plan" }?.text ?: "",
                onCancel = vm::cancelTask,
                onBack = { vm.back() },
            )
        }
    }
}
