package com.trae.watch.xposed

import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 让 TRAE 的自定义模型也能选思考强度。
 *
 * 逆向结论（com.bytedance.trae.cn v0.0.25）：
 *
 *  1) 能否显示入口由 RemoteModelItem.getHasThinkingCapability() 决定：
 *        return reasoningEffortConfig != null
 *            && reasoningEffortConfig.supportThinking
 *            && reasoningEffortConfig.options.isNotEmpty()
 *     自定义/第三方模型服务端不下发 reasoning_effort_config，于是返回 false，UI 不显示入口。
 *
 *  2) 发送时 ConversationViewModel 里：
 *        if (model.getHasThinkingCapability()) {
 *            ReasoningEffortConfig cfg = model.getReasoningEffortConfig();
 *            Intrinsics.checkNotNull(cfg);          // ← 只改判定会在这里崩
 *            String level = repo.getReasoningEffortLevel(name);
 *            if (level == null) level = cfg.getDefaultLevel();
 *            if (level == null) level = firstOrNull(cfg.getOptions());
 *            if (level != null) json.addProperty("reasoning_effort_level", level);
 *        }
 *
 *  所以必须成对处理：
 *    A. getHasThinkingCapability() 恒为 true（已支持的原样返回，不干扰）
 *    B. getReasoningEffortConfig() 在原来为 null 时构造一个 {true, ["high","light"], "high"}
 *
 *  合法取值从 ModelCapabilityBottomSheet.effortLevelDisplayName 里读到，只有 high / light 两个。
 *
 *  另外 ModelSelectBottomSheet 用 getHasCapabilityEntry() 决定是否显示能力入口箭头，
 *  它内部调用上面两个方法，所以自动生效，不用单独 hook。
 */
class TraeModelPatch : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "TraeModelPatch"

        /** 目标包名，来自 AndroidManifest 反编译结果 */
        private const val PKG = "com.bytedance.trae.cn"

        private const val CLS_MODEL_ITEM =
            "com.bytedance.trae.conversation.network.RemoteModelItem"
        private const val CLS_EFFORT_CONFIG =
            "com.bytedance.trae.conversation.network.ReasoningEffortConfig"
        private const val CLS_CONTEXT_TOKENS =
            "com.bytedance.trae.conversation.network.ContextWindowTokens"

        private const val CLS_FEATURE_CAPABILITY =
            "com.bytedance.trae.settings.api.feature.FeatureCapability"
        private const val CLS_FEATURE =
            "com.bytedance.trae.settings.api.feature.Feature"
        private const val CLS_ROLLOUT_POLICY =
            "com.bytedance.trae.conversation.inputcontext.InputContextSourceRolloutPolicy"
        private const val CLS_CONTEXT_SOURCE =
            "com.bytedance.trae.conversation.inputcontext.ContextSource"
        private const val CLS_ATTACHMENT_CONFIG =
            "com.bytedance.trae.conversation.widget.ChatAttachmentConfig"
        private const val CLS_CHAT_MODE =
            "com.bytedance.trae.conversation.widget.ChatMode"

        /**
         * 档位取值。
         *
         * 注意：这个字段最终透传给上游 OpenAI 兼容接口。
         * 截图实测的报错（workbuddy/deepseek-v4.1-flash）：
         *   invalid_reasoning_control: reasoning_effort must be one of
         *   none, minimal, low, medium, high, xhigh, max, ultra
         * 所以必须用这套枚举，不能用 TRAE UI 里的 light。
         */
        private val OPTIONS = listOf("low", "medium", "high", "xhigh", "max")
        private const val DEFAULT_LEVEL = "medium"

        /** max 模式开启后使用的更大上下文窗口 */
        private const val MAX_CONTEXT_TOKENS = 1_000_000L
        private const val BASE_CONTEXT_TOKENS = 200_000L
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PKG) return

        // 热重载/多进程场景下可能被加载多次，用标记防重复
        if (lpparam.classLoader == null) return

        try {
            hookHasThinking(lpparam.classLoader)
            hookEffortConfig(lpparam.classLoader)
            hookMaxCapability(lpparam.classLoader)
            hookRemoteInputCapabilities(lpparam.classLoader)
            XposedBridge.log("$TAG: hooks installed on ${lpparam.processName}")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: hook failed -> ${t.message}")
        }
    }

    /**
     * D. 放开远程（REMOTE）会话的输入区能力。
     *
     * 目标：远程 PC 会话里也要能「上传文件」和「看到插件/技能入口」。
     *
     * 逆向发现这几处判定全部对 REMOTE 放行：
     *   - InputFragment.isCodeRemoteFileAttachmentSupported() → REMOTE 直接 true
     *   - ProjectFilesCapability.isSupported() → REMOTE 直接 true
     *   - PluginCapability.isSupported() → 非 local 直接 true
     *   - ChatAttachmentConfig 的 FILES/PLUGINS/SKILLS isVisible → 不看 cliType
     *
     * 也就是说代码逻辑本身是允许的，实际不显示只可能来自两个动态配置：
     *   1. Feature.PLUGIN 开关（FeatureCapability.isEnabled）未对当前账号开启
     *   2. InputContextSourceRolloutPolicy.getEnabledSources() 没下发 PLUGINS/SKILLS
     *
     * 所以这里不猜测具体哪层，直接把这三个「闸门」都改成放行，
     * 并且对已有值的场景不覆盖，避免影响本来正常的本地会话。
     */
    private fun hookRemoteInputCapabilities(cl: ClassLoader) {
        // 1) Feature 能力开关：PLUGIN 恒为开启
        runCatching {
            val featureCls = XposedHelpers.findClass(CLS_FEATURE_CAPABILITY, cl)
            XposedHelpers.findAndHookMethod(
                featureCls,
                "isEnabled",
                XposedHelpers.findClass(CLS_FEATURE, cl),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val feature = param.args.getOrNull(0)?.toString() ?: return
                        if (feature.contains("PLUGIN")) param.result = true
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: feature hook skipped -> ${it.message}") }

        // 2) 可用输入源：确保包含 PLUGINS / SKILLS
        runCatching {
            val policyCls = XposedHelpers.findClass(CLS_ROLLOUT_POLICY, cl)
            XposedHelpers.findAndHookMethod(
                policyCls,
                "getEnabledSources",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // 返回值可能是不可变集合，不能原地 add，必须另建一个
                        val origin = param.result as? Set<*> ?: return
                        val merged = LinkedHashSet<Any?>(origin)
                        for (name in listOf("PLUGINS", "SKILLS")) {
                            addSourceByName(cl, merged, name)
                        }
                        param.result = merged
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: rollout hook skipped -> ${it.message}") }

        // 3) 兜底：技能入口恒可见
        runCatching {
            val cfgCls = XposedHelpers.findClass(CLS_ATTACHMENT_CONFIG, cl)
            XposedHelpers.findAndHookMethod(
                cfgCls,
                "isPluginVisible",
                XposedHelpers.findClass(CLS_CHAT_MODE, cl),
                java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: plugin visible hook skipped -> ${it.message}") }
    }

    /** 按枚举名往 set 里补一个 ContextSource 元素 */
    private fun addSourceByName(cl: ClassLoader, set: MutableSet<Any?>, name: String) {
        runCatching {
            val sourceCls = XposedHelpers.findClass(CLS_CONTEXT_SOURCE, cl)
            val entry = XposedHelpers.callStaticMethod(sourceCls, "valueOf", name)
            if (entry != null) set.add(entry)
        }
    }

    /**
     * A. 能力判定恒真。
     * 原本已支持思考的模型，其 config 本来就非 null，返回 true 与原逻辑一致；
     * 自定义模型原本 false，这里改判 true，UI 才会显示思考强度行。
     */
    private fun hookHasThinking(cl: ClassLoader) {
        val cls = XposedHelpers.findClass(CLS_MODEL_ITEM, cl)
        XposedHelpers.findAndHookMethod(
            cls,
            "getHasThinkingCapability",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            },
        )
    }

    /**
     * B. 为没有 config 的模型补一个默认配置。
     * 注意不能无条件覆盖：已下发的配置里有服务端真实的 options（可能多于两档），
     * 覆盖会丢失服务端语义。
     */
    private fun hookEffortConfig(cl: ClassLoader) {
        val modelCls = XposedHelpers.findClass(CLS_MODEL_ITEM, cl)
        val configCls = XposedHelpers.findClass(CLS_EFFORT_CONFIG, cl)

        XposedHelpers.findAndHookMethod(
            modelCls,
            "getReasoningEffortConfig",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result != null) return
                    param.result = newConfig(configCls)
                }
            },
        )
    }

    private fun newConfig(configCls: Class<*>): Any {
        // ReasoningEffortConfig(boolean supportThinking, List<String> options, String defaultLevel)
        // Kotlin data class 会额外生成 (boolean, List, String, int, DefaultConstructorMarker)
        // 的 synthetic 构造器，这里精确匹配三参数那个。
        val ctor = configCls.declaredConstructors.first {
            val p = it.parameterTypes
            p.size == 3 &&
                p[0] == java.lang.Boolean.TYPE &&
                java.util.List::class.java.isAssignableFrom(p[1]) &&
                p[2] == String::class.java
        }
        ctor.isAccessible = true
        return ctor.newInstance(true, ArrayList(OPTIONS), DEFAULT_LEVEL)
    }

    /**
     * C. max 模式。
     *
     * 判定条件（RemoteModelItem.getHasMaxCapability）：
     *   maxMode == true && contextWindowTokens?.max != null && max != 0
     *
     * 自定义模型这两个字段都是 null / false，所以开关不显示。
     * 这里伪造：
     *   - getHasMaxCapability() 恒 true，让 UI 显示 max 开关
     *   - getContextWindowTokens() 在为空时补一个 {dev=BASE, max=MAX}
     *
     * 注意：开关本身由 ModelCapabilityRepository.isMaxEnabled 存 Keva，
     * 打开后 ConversationViewModel 会删掉请求体里的 context_window_tokens，
     * 交给服务端用该模型的最大窗口，所以这里补的数字只影响 UI 展示，不影响实际行为。
     */
    private fun hookMaxCapability(cl: ClassLoader) {
        val modelCls = XposedHelpers.findClass(CLS_MODEL_ITEM, cl)
        val ctxCls = XposedHelpers.findClass(CLS_CONTEXT_TOKENS, cl)

        XposedHelpers.findAndHookMethod(
            modelCls,
            "getHasMaxCapability",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            },
        )

        XposedHelpers.findAndHookMethod(
            modelCls,
            "getContextWindowTokens",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.result != null) return
                    // ContextWindowTokens(Long dev, Long max)
                    val ctor = ctxCls.declaredConstructors.first {
                        val p = it.parameterTypes
                        p.size == 2 &&
                            p[0] == java.lang.Long::class.java &&
                            p[1] == java.lang.Long::class.java
                    }
                    ctor.isAccessible = true
                    param.result = ctor.newInstance(BASE_CONTEXT_TOKENS, MAX_CONTEXT_TOKENS)
                }
            },
        )
    }
}
