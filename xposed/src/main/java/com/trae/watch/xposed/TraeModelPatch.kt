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

        private const val CLS_ROOT_POLICY =
            "com.bytedance.trae.conversation.inputcontext.InputContextRootPolicy"
        private const val CLS_ROOT_ROW =
            "com.bytedance.trae.conversation.inputcontext.InputContextRootRow"
        private const val CLS_CONTEXT_RUNTIME =
            "com.bytedance.trae.conversation.inputcontext.ContextRuntime"
        private const val CLS_DIRECT_ACTION =
            "com.bytedance.trae.conversation.inputcontext.InputContextDirectAction"

        private const val CLS_THEME_MANAGER =
            "com.bytedance.trae.common.theme.ThemeManager"
        private const val CLS_APPCOMPAT_DELEGATE =
            "androidx.appcompat.app.AppCompatDelegate"

        /** Compose 主题入口，TRAE 所有 Compose 界面都经由它取色 */
        private const val CLS_COMPOSE_TOKENS =
            "com.bytedance.trae.kmp.ui.theme.TraeComposeTokensKt"

        /**
         * AppCompatDelegate 的夜间模式常量。
         * 注意：MODE_NIGHT_NO = 1、MODE_NIGHT_YES = 2，别写反。
         */
        private const val MODE_NIGHT_YES = 2

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
            hookInputRows(lpparam.classLoader)
            hookDarkMode(lpparam.classLoader)
            hookComposeDarkTheme(lpparam.classLoader)
            hookComposeContextWrapper(lpparam.classLoader)
            // init() 可能在 hook 装上前就跑过了，这里主动补一刀
            applyAppCompatNight(lpparam.classLoader)
            XposedBridge.log("$TAG: hooks installed on ${lpparam.processName}")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: hook failed -> ${t.message}")
        }
    }

    /**
     * E. 直接改「加号面板」最终渲染的行列表。
     *
     * 上一版 hook 的是各种配置闸门（Feature 开关、enabledSources、isPluginVisible），
     * 实测没有效果，说明真正把 LOCAL_FILE / PLUGINS / SKILLS 过滤掉的不是这几处。
     *
     * 这里换成最直接的手段：不关心上游怎么算，直接对 rows() 的返回值补行。
     * 无论前面多少层判断，最终 UI 渲染的就是这个 List，补进去就一定会显示。
     *
     * 两个重载都要 hook：
     *   rows(runtime, availableSources, enabledSources)
     *   rows(runtime, availableSources, enabledSources, allowLocalFile,
     *        allowProjectFiles, allowArtifacts, allowLarkContacts)
     */
    private fun hookInputRows(cl: ClassLoader) {
        val policyCls = XposedHelpers.findClass(CLS_ROOT_POLICY, cl)
        val runtimeCls = XposedHelpers.findClass(CLS_CONTEXT_RUNTIME, cl)
        val sourcesCls = XposedHelpers.findClass(CLS_CONTEXT_SOURCE, cl)
        val actionsCls = XposedHelpers.findClass(CLS_DIRECT_ACTION, cl)

        val rowDirectCls = XposedHelpers.findClass("$CLS_ROOT_ROW\$Direct", cl)
        val rowSourceCls = XposedHelpers.findClass("$CLS_ROOT_ROW\$Source", cl)

        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val origin = param.result as? List<*> ?: return
                param.result = enrichRows(origin, rowDirectCls, rowSourceCls, actionsCls, sourcesCls)
            }
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                policyCls, "rows", runtimeCls, Set::class.java, Set::class.java, hook,
            )
        }.onFailure { XposedBridge.log("$TAG: rows(3) hook skipped -> ${it.message}") }

        runCatching {
            XposedHelpers.findAndHookMethod(
                policyCls, "rows",
                runtimeCls, Set::class.java, Set::class.java,
                java.lang.Boolean.TYPE, java.lang.Boolean.TYPE,
                java.lang.Boolean.TYPE, java.lang.Boolean.TYPE,
                hook,
            )
        }.onFailure { XposedBridge.log("$TAG: rows(7) hook skipped -> ${it.message}") }
    }

    /**
     * 在已有行基础上补齐：本地文件、项目文件、插件、技能。
     * 用 equals 去重，避免与上游已存在的行重复。
     */
    private fun enrichRows(
        origin: List<*>,
        rowDirectCls: Class<*>,
        rowSourceCls: Class<*>,
        actionsCls: Class<*>,
        sourcesCls: Class<*>,
    ): List<Any?> {
        val out = ArrayList<Any?>(origin)

        // 需要的 Direct 行（目前只有本地文件会被 allowLocalFile 过滤掉）
        for (action in listOf("LOCAL_FILE")) {
            runCatching {
                val enumVal = XposedHelpers.callStaticMethod(actionsCls, "valueOf", action)
                val row = rowDirectCls.getDeclaredConstructor(actionsCls).newInstance(enumVal)
                if (out.none { it == row }) out.add(row)
            }
        }

        // 需要的 Source 行
        for (source in listOf("PROJECT_FILES", "PLUGINS", "SKILLS")) {
            runCatching {
                val enumVal = XposedHelpers.callStaticMethod(sourcesCls, "valueOf", source)
                val row = rowSourceCls.getDeclaredConstructor(sourcesCls).newInstance(enumVal)
                if (out.none { it == row }) out.add(row)
            }
        }
        return out
    }

    /**
     * F. 全局深色模式。
     *
     * 逆向确认 TRAE 主题完全由 ThemeManager 控制：
     *   static volatile boolean isDarkMode      // UI 实际读的开关
     *   static volatile int darkMode = -1       // 透传给 AppCompatDelegate
     *
     *   updateDarkModeFlag(cfg) { isDarkMode = (cfg.uiMode & 48) == 32 }
     *   init()                  { darkMode = 1; AppCompatDelegate.setDefaultNightMode(1) }
     *   applyDarkMode(mode)     { darkMode = mode; ...; setDefaultNightMode(mode) }
     *
     * 【上一版为什么没生效】
     * AppCompatDelegate 的常量是 MODE_NIGHT_NO = 1、MODE_NIGHT_YES = 2，
     * 我上一版把 darkMode 强制成 1，等于强制了浅色模式，所以界面毫无变化。
     *
     * 【这一版的做法】
     * 不再依赖字段时序，直接改方法返回值：
     *   1. isDarkMode()      -> 恒 true          （UI / Compose 读这个）
     *   2. getCurrentMode()  -> 恒 2 (NIGHT_YES) （设置页显示的当前模式）
     *   3. applyDarkMode(m)  -> 参数改成 2        （用户手动切浅色也压回深色）
     *   4. init() 之后主动调 AppCompatDelegate.setDefaultNightMode(2)
     *      —— 这一条决定 XML 资源走 -night 目录，是纯字段 hook 覆盖不到的
     *   5. attachBaseContext 改 uiMode 兜底      （WebView / Lynx 读 Configuration）
     */
    private fun hookDarkMode(cl: ClassLoader) {
        // 1) UI 读的开关恒为 true
        runCatching {
            XposedHelpers.findAndHookMethod(
                CLS_THEME_MANAGER, cl, "isDarkMode",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: isDarkMode hook skipped -> ${it.message}") }

        // 2) 当前模式恒为 MODE_NIGHT_YES
        runCatching {
            XposedHelpers.findAndHookMethod(
                CLS_THEME_MANAGER, cl, "getCurrentMode",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = MODE_NIGHT_YES
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: getCurrentMode hook skipped -> ${it.message}") }

        // 3) 用户/系统切主题时压回深色
        runCatching {
            XposedHelpers.findAndHookMethod(
                CLS_THEME_MANAGER, cl, "applyDarkMode",
                java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = MODE_NIGHT_YES
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: applyDarkMode hook skipped -> ${it.message}") }

        // 4) init 执行完，主动把 AppCompat 切成夜间，让 XML 走 -night 资源
        runCatching {
            XposedHelpers.findAndHookMethod(
                CLS_THEME_MANAGER, cl, "init",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        forceNight(param.method.declaringClass)
                        applyAppCompatNight(cl)
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: dark init hook skipped -> ${it.message}") }

        // 5) 兜底：Configuration 置为夜间（WebView / Lynx 等第三方组件）
        val cfgHook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val ctx = param.result as? android.content.Context ?: return
                val cfg = android.content.res.Configuration(ctx.resources.configuration)
                cfg.uiMode = (cfg.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                param.result = ctx.createConfigurationContext(cfg)
            }
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.app.Activity::class.java, "attachBaseContext",
                android.content.Context::class.java, cfgHook,
            )
        }.onFailure { XposedBridge.log("$TAG: cfg dark hook skipped -> ${it.message}") }
    }

    /** 同步静态字段，避免别处直接读字段时拿到旧值 */
    private fun forceNight(themeCls: Class<*>) {
        runCatching {
            XposedHelpers.setStaticIntField(themeCls, "darkMode", MODE_NIGHT_YES)
            XposedHelpers.setStaticBooleanField(themeCls, "isDarkMode", true)
        }
    }

    /** 反射调 AppCompatDelegate.setDefaultNightMode，让资源走 -night 目录 */
    private fun applyAppCompatNight(cl: ClassLoader) {
        runCatching {
            val cls = XposedHelpers.findClass(CLS_APPCOMPAT_DELEGATE, cl)
            XposedHelpers.callStaticMethod(cls, "setDefaultNightMode", MODE_NIGHT_YES)
        }.onFailure { XposedBridge.log("$TAG: appcompat night skipped -> ${it.message}") }
    }

    /**
     * G. 强制 Compose 主题为深色（真正的关键点）。
     *
     * 前两版都打偏了。实测截图里 Compose 界面一直是白的，
     * 反编译 TraeComposeTokensKt 才看清原因：
     *
     *   public static final void TraeDesignTheme(boolean z, Function2 content, ...) {
     *       if (默认参数) z = isSystemInDarkTheme(composer);   // 只有不传参才读系统
     *       CompositionLocalProvider(
     *           LocalTraeColors.provides(z ? TraeColorTokens.dark : TraeColorTokens.light)
     *       )
     *   }
     *
     * 而所有调用点都**显式传了 false**：
     *   TraeComposeContentFragment:  TraeDesignTheme(false, ...)
     *   TraeNativeMenuPopup:         TraeDesignTheme(false, ...)
     *   VideoArtifactPreviewActivity: TraeDesignTheme(false, ...)
     *   ImagePreviewActivity:         TraeDesignTheme(false, ...)
     *   HtmlArtifactPreviewActivity:  TraeDesignTheme(false, ...)
     *
     * 所以无论 ThemeManager、Configuration、AppCompat 怎么设置，
     * Compose 界面永远拿 light 色板。必须直接改这个函数的入参。
     *
     * 签名：(Z, Lkotlin/jvm/functions/Function2;, Landroidx/compose/runtime/Composer;, II)V
     * args 里只有前两个是业务参数，后三个是编译器注入的 composer/flags，
     * 所以只改 args[0]。
     */
    private fun hookComposeDarkTheme(cl: ClassLoader) {
        val composerCls = runCatching {
            XposedHelpers.findClass("androidx.compose.runtime.Composer", cl)
        }.getOrNull()
        if (composerCls == null) {
            XposedBridge.log("$TAG: composer class not found, skip compose dark hook")
            return
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                CLS_COMPOSE_TOKENS, cl, "TraeDesignTheme",
                java.lang.Boolean.TYPE,
                kotlin.jvm.functions.Function2::class.java,
                composerCls,
                java.lang.Integer.TYPE,
                java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                },
            )
            XposedBridge.log("$TAG: compose dark theme hook installed")
        }.onFailure { XposedBridge.log("$TAG: compose dark hook skipped -> ${it.message}") }
    }

    /**
     * H. isSystemInDarkTheme 兜底。
     *
     * 部分 Compose 组件不走 TraeDesignTheme，直接调用 isSystemInDarkTheme()。
     * 它是 @Composable，编译后是 DarkThemeKt.isSystemInDarkTheme(Composer, int)。
     * 这个方法内部读 LocalConfiguration，我们不能改它的读法，
     * 但可以让 Activity 的 Configuration 是夜间（已在 hookDarkMode 第 5 步做了）。
     *
     * 这里额外 hook 一次以覆盖 ComposeView 的 attach 时机：
     * 有些 Activity 的 Compose 内容在 setContentView 之前就 attach 了，
     * 那时 Configuration 可能还是旧的。
     */
    private fun hookComposeContextWrapper(cl: ClassLoader) {
        // 给 ComposeView 换一个夜间 Context，保证 LocalConfiguration 是夜间
        runCatching {
            val cls = XposedHelpers.findClass(
                "androidx.compose.ui.platform.ComposeView", cl,
            )
            XposedHelpers.findAndHookMethod(
                cls, "setContext", android.content.Context::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ctx = param.args[0] as? android.content.Context ?: return
                        val cfg = android.content.res.Configuration(ctx.resources.configuration)
                        cfg.uiMode = (cfg.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                        param.args[0] = ctx.createConfigurationContext(cfg)
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: compose ctx hook skipped -> ${it.message}") }
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
