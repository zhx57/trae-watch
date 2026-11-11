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

        /** Compose 色板数据源，getLight() 返回的就是界面实际使用的浅色板 */
        private const val CLS_COLOR_TOKENS =
            "com.bytedance.trae.kmp.ui.theme.TraeColorTokens"
        private const val CLS_PALETTE_BASE =
            "com.bytedance.trae.kmp.ui.theme.TraeColors"
        /** 浅/深色板实现类的包前缀，拼接 LightColors / DarkColors */
        private const val CLS_PALETTE_PREFIX =
            "com.bytedance.trae.kmp.ui.theme.Trae"

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
            // 思考内容：ThoughtRenderer 里动态 new 的 SimpleMarkdownTextView，
            // 颜色取自 common_ui 的 trae_text_text_default，深色下偏灰。
            hookThoughtText(lpparam.classLoader)
            installSkillDiagnostics(lpparam.classLoader)
            hookDarkMode(lpparam.classLoader)
            // 最后一层兜底：Activity 内容就绪后直接遍历 View 树刷成深色。
            // 前面那些资源层 hook 依赖的东西太多（资源解析时机、night 变体是否
            // 真的被选中、Compose 是否绕过 XML），View 树后处理不依赖任何前提，
            // 只要视图显示出来就一定能改到。
            hookDarkModeViewTree(lpparam.classLoader)
            hookWhiteBackground(lpparam.classLoader)
            hookSendIconResources(lpparam.classLoader)
            hookBlackText()
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

        // 需要的 Source 行。
        // ContextSource 枚举全集是：
        //   PROJECT_FILES, SKILLS, PLUGINS, LIBRARY, LARK_DOCS, LARK_CONTACTS
        // 用户明确要求：技能交回 TRAE 原生，不要 hook 干预，
        // 这里只补「插件」和「飞书文档」。
        for (source in listOf("PROJECT_FILES", "PLUGINS", "LARK_DOCS")) {
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

        // 4) init 执行前就把静态字段改成夜间（因为 init 里写死 darkMode = 1）
        runCatching {
            XposedHelpers.findAndHookMethod(
                CLS_THEME_MANAGER, cl, "init",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        forceNight(param.method.declaringClass)
                    }

                    override fun afterHookedMethod(param: MethodHookParam) {
                        // init 内部会 setDefaultNightMode(1)，靠下面的入口拦截改成 2
                        forceNight(param.method.declaringClass)
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: dark init hook skipped -> ${it.message}") }

        // 4.5) 关键：拦 AppCompatDelegate.setDefaultNightMode，把所有非夜间值改成夜间。
        //      这是 XML 资源走 -night 目录的唯一入口，也是 init() 里那行硬编码的拦截点。
        runCatching {
            val delegateCls = XposedHelpers.findClass(CLS_APPCOMPAT_DELEGATE, cl)
            XposedHelpers.findAndHookMethod(
                delegateCls, "setDefaultNightMode", java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if ((param.args[0] as Int) != MODE_NIGHT_YES) {
                            param.args[0] = MODE_NIGHT_YES
                        }
                    }
                },
            )
            XposedBridge.log("$TAG: setDefaultNightMode forced to NIGHT_YES")
        }.onFailure { XposedBridge.log("$TAG: night mode entry hook skipped -> ${it.message}") }

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

        // 5.5) 【真正的根因】TRAE 的 TraeCommonAppCompatActivity 重写了
        //      applyOverrideConfiguration()，里面重建了一个只带 locale 的
        //      Configuration 对象，把 uiMode 的 night 位抹成默认值。
        //      这导致 Activity 的 Resources 永远按 default 配置解析 ——
        //      即便 AppCompatDelegate 全局模式是 NIGHT_YES，
        //      night 变体也永远选不中，所有 XML 页面因此全白。
        //
        //      hook 在它调用 super 之前，把 night 位补回去。
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.view.ContextThemeWrapper::class.java,
                "applyOverrideConfiguration",
                android.content.res.Configuration::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cfg = param.args[0] as? android.content.res.Configuration
                            ?: return
                        cfg.uiMode = (cfg.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                },
            )
            XposedBridge.log("$TAG: applyOverrideConfiguration night bit restored")
        }.onFailure { XposedBridge.log("$TAG: overrideConfiguration hook skipped -> ${it.message}") }

        // 6) 补齐 TRAE 自己漏做 night 变体的颜色资源
        //    注意：这一层依赖资源系统真的按 night 配置解析。TRAE 的
        //    applyOverrideConfiguration() 会重建 Configuration（只保留 locale），
        //    抹掉 uiMode 的 night 位，所以这里多数时候拿到的还是浅色值。
        //    真正兜底的是 hookDarkModeViewTree()，它在 View 树上直接改。
        hookMissingNightColors()
    }

    /**
     * G. 修复 TRAE 缺失 night 变体的硬编码颜色。
     *
     * 【上一版为什么无效】
     * hook GradientDrawable.setColor() 是空转的。XML shape 的 <solid> 在
     * GradientDrawable.inflate() 里被直接写进 mGradientState.mSolidColors 字段，
     * 根本不调用 setColor()。所以那个 hook 一次都没命中 —— 输入框依然白。
     *
     * 【这一版的做法】
     * 换到 Resources 层：hook Resources.getDrawable() / getDrawableForDensity()，
     * 拿到 Drawable 之后按「资源 id」精确替换。资源 id 与 drawable 的对应关系
     * 是从 APK 的 res/layout 反查出来的，写死在下面。
     *
     * 同时修一个文字色问题：用户气泡 res/layout/trae_item_user_message.xml 里
     * tv_message 的 textColor 是硬编码 #FF1A1A1A。气泡背景
     * trae_bg_chat_bubble_sent 的 solid 走 trae_bg_bg_overlay_l1，有 night 变体
     * 会变深，于是深色下黑底黑字。这必须在 TextView 上改，改 drawable 没用。
     */
    private fun hookMissingNightColors() {
        // 资源 id -> 深色替换色（ARGB），用 ColorFilter 整体染色
        val tintMap = HashMap<Int, Int>()
        // 资源 id 集合：直接置为全透明（纯装饰性白底，隐藏后不影响布局）
        val hideSet = HashSet<Int>()

        // ID 全部通过 resources.arsc 反查确认，见 _ids.py 的输出：
        //
        //   0x7F070456  trae_bg_input_top_layer          <solid #B3FFFFFF> 输入框上层
        //   0x7F070499  trae_bg_title_bar_mark_gradient  <gradient #FFF7F6F5 -> #00F7F6F5>
        //   0x7F070479  trae_bg_scroll_to_bottom         <solid #FFFFFFFF> 回到底部按钮
        //
        // 前两个是纯白色的装饰层，深色下直接隐藏最干净：
        //   输入框真正的外观由外层 full_input_container 的
        //   trae_bg_input_bottom_layer 提供（solid 走 trae_bg_bg_overlay_l2，
        //   有 night 变体，深色下是深灰，圆角 28dip 与描边都完整）。
        //   顶部白条只是一条 24dip 高的渐隐遮罩，删掉不影响任何内容。
        hideSet.add(0x7F070456)
        hideSet.add(0x7F070499)

        // 回到底部按钮本来是白圆底 + 深色箭头，深色下需要反过来
        tintMap[0x7F070479] = 0xFF2A2A2A.toInt()

        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val id = (param.args.getOrNull(0) as? Number)?.toInt() ?: return
                val origin = param.result as? android.graphics.drawable.Drawable ?: return
                runCatching {
                    if (hideSet.contains(id)) {
                        val d = origin.constantState?.newDrawable()?.mutate() ?: origin.mutate()
                        d.alpha = 0
                        param.result = d
                    } else {
                        val color = tintMap[id] ?: return
                        param.result = tinted(origin, color)
                    }
                }.onFailure { XposedBridge.log("$TAG: drawable swap fail $id -> ${it.message}") }
            }
        }

        // Resources 的三个取 drawable 入口都要拦，不同 SDK 版本走不同重载
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getDrawable", java.lang.Integer.TYPE, android.content.res.Resources.Theme::class.java,
                hook,
            )
        }.onFailure { XposedBridge.log("$TAG: getDrawable(id,theme) skipped -> ${it.message}") }

        runCatching {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getDrawable", java.lang.Integer.TYPE, hook,
            )
        }.onFailure { XposedBridge.log("$TAG: getDrawable(id) skipped -> ${it.message}") }

        runCatching {
            XposedHelpers.findAndHookMethod(
                android.content.res.Resources::class.java,
                "getDrawableForDensity",
                java.lang.Integer.TYPE, java.lang.Integer.TYPE,
                android.content.res.Resources.Theme::class.java, hook,
            )
        }.onFailure { XposedBridge.log("$TAG: getDrawableForDensity skipped -> ${it.message}") }

        // 用户气泡里的硬编码黑字 -> 深色下改浅色
        hookUserBubbleText()

        XposedBridge.log("$TAG: night drawable + bubble text patch installed")
    }

    /** 用 ColorFilter 把 shape drawable 染成目标色，保留 corners / stroke 形状 */
    private fun tinted(src: android.graphics.drawable.Drawable, color: Int): android.graphics.drawable.Drawable {
        val d = src.constantState?.newDrawable()?.mutate() ?: src.mutate()
        d.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        return d
    }

    /**
     * 用户气泡文字色修复。
     *
     * res/layout/trae_item_user_message.xml 里：
     *   <TextView android:id="@+id/tv_message" android:textColor="#FF1A1A1A" .../>
     * 硬编码黑字，没有 night 变体。气泡背景 trae_bg_chat_bubble_sent 的颜色走
     * trae_bg_bg_overlay_l1，是有 night 变体的，深色下会变成深灰，
     * 于是用户自己发的消息就成了黑底黑字。
     *
     * TextView.setTextColor(int) 在 setText 前会被调用，hook 它按 id 判断最稳。
     */
    private fun hookUserBubbleText() {
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.widget.TextView::class.java,
                "setTextColor", java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val tv = param.thisObject as? android.widget.TextView ?: return
                        // 只处理用户气泡正文：id 名必须是 tv_message 且当前色是硬编码黑
                        val name = try {
                            tv.resources.getResourceEntryName(tv.id)
                        } catch (t: Throwable) {
                            null
                        }
                        if (name != "tv_message") return
                        val c = (param.args[0] as? Number)?.toInt() ?: return
                        if (c == 0xFF1A1A1A.toInt()) {
                            param.args[0] = 0xFFE5E5E5.toInt()
                        }
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: bubble textColor hook skipped -> ${it.message}") }
    }

    /** 同步静态字段，避免别处直接读字段时拿到旧值 */
    private fun forceNight(themeCls: Class<*>) {
        runCatching {
            XposedHelpers.setStaticIntField(themeCls, "darkMode", MODE_NIGHT_YES)
            XposedHelpers.setStaticBooleanField(themeCls, "isDarkMode", true)
        }
    }

    /**
     * H. View 树后处理 —— 不依赖任何资源解析路径的兜底方案。
     *
     * 【为什么之前的方案全都没生效】
     * 前几版分别尝试过：hook Compose 色板、hook AppCompatDelegate、hook ThemeManager、
     * hook Resources.getDrawable。四个方向都依赖同一个前提 —— 资源系统真的按 night
     * 配置去解析了。但 TRAE 的 TraeCommonAppCompatActivity 在 applyOverrideConfiguration()
     * 里重建了 Configuration（只保留 locale），把 uiMode 的 night 位抹掉了，
     * 所以即便 AppCompatDelegate 的全局模式是 NIGHT_YES，Activity 的 Resources
     * 拿到的仍是 default 配置，night 变体永远选不中。
     *
     * 这解释了为什么"设置页是黑的、其他都是白的"：设置页是 Compose，走色板对象；
     * 其余原生页面走 XML 资源，全部拿到浅色值。
     *
     * 【这一版】
     * 直接在 Activity 的窗口内容就绪之后遍历 View 树，按 view id 精确刷色。
     * 不关心资源是怎么来的，只关心最终显示的是什么。
     *
     * 只处理用户明确反馈的三处：
     *   1) main_input_area / full_input_container 输入区 —— 白底
     *   2) title_bar_mark 顶部白条 —— 浅色渐变遮罩
     *   3) ll_message_container 用户气泡 + tv_message 文字 —— 黑底黑字
     */
    private fun hookDarkModeViewTree(cl: ClassLoader) {
        // 每次 Activity 内容就绪都扫一遍。用 onContentChanged 而不是 onCreate，
        // 因为 xml 真正 attach 到窗口之后才有完整 View 树，且 Fragment 场景也覆盖。
        val scan = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? android.app.Activity ?: return
                val decor = activity.window?.decorView ?: return
                runCatching { applyDarkToTree(decor) }
                    .onFailure { XposedBridge.log("$TAG: view tree scan failed -> ${it.message}") }
            }
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                android.app.Activity::class.java, "onContentChanged", scan,
            )
            XposedBridge.log("$TAG: view tree dark hook installed")
        }.onFailure { XposedBridge.log("$TAG: view tree hook skipped -> ${it.message}") }

        // 每个 View 挂载到窗口时处理它自己。
        // 不遍历子树：onAttachedToWindow 对每个 View 都会触发，处理自己就是 O(n)，
        // 而遍历子树会变成 O(n^2)。而且 Dialog / BottomSheet 的内容也能被覆盖到，
        // 因为它们同样会 attach 到自己的 Window。
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.view.View::class.java, "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val v = param.thisObject as? android.view.View ?: return
                        // 同步先处理一遍（id 精确匹配的立刻生效），
                        // 再 post 到下一帧补一次 —— 那时布局已完成，v.width 可用，
                        // 才能识别 ConstraintLayout match_constraint 那种 width=0 的满宽容器。
                        runCatching { processView(v) }
                        // 只有带 shape 背景的才需要等布局完成再判一次，避免列表里
                        // 每个 View 都往消息队列塞一个任务。
                        if (v.background is android.graphics.drawable.GradientDrawable) {
                            runCatching { v.post { runCatching { processView(v) } } }
                        }
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: attach hook skipped -> ${it.message}") }
    }

    /**
     * 遍历 View 树，按 id 名精确改色。
     *
     * 每次都重新处理，不去重。原因：RecyclerView 复用时 item 的 bind 会重置属性，
     * 去重会导致第二次显示时又变回白色。
     */
    private fun applyDarkToTree(root: android.view.View) {
        val stack = ArrayList<android.view.View>(32)
        stack.add(root)
        while (stack.isNotEmpty()) {
            val v = stack.removeAt(stack.size - 1)
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    v.getChildAt(i)?.let { stack.add(it) }
                }
            }
            runCatching { processView(v) }
        }
    }

    /** 处理单个 View：优先按 id 名精确改，否则尝试白色底板识别 */
    private fun processView(v: android.view.View) {
        val name = if (v.id == android.view.View.NO_ID) null
        else runCatching { v.resources.getResourceEntryName(v.id) }.getOrNull()
        if (name != null && darkViewNames.contains(name)) {
            paintDark(v, name)
            return
        }
        paintWhiteSurface(v)
    }

    /**
     * 白色底板识别。
     *
     * 【关键】必须在 onAttachedToWindow 之后调用，因为那时 layoutParams 已经设置好。
     * 之前放在 View.setBackground 的 hook 里，而 XML inflate 时 setBackground 是在
     * View 构造函数内部执行的，那一刻 layoutParams 还是 null，所以全部被跳过。
     *
     * 判定：GradientDrawable + 纯色接近不透明白 + layoutParams 满宽。
     * 满足就改色，用 constantState.newDrawable().mutate() 复制一份再 setColor，
     * 这样圆角和描边都保留，也不会污染其它共享同一 ConstantState 的 View。
     */
    private fun paintWhiteSurface(v: android.view.View) {
        // 情况 A：纯色 ColorDrawable。
        // loading_progress_bar_voice.xml 的根就是一个纯色：
        //   android:background="@android:0106000D"（系统白）
        // 它是 ColorDrawable 不是 GradientDrawable，之前全部漏掉，
        // 导致语音长按时的白色遮罩一直存在。
        val cd = v.background as? android.graphics.drawable.ColorDrawable
        if (cd != null) {
            if (!isNearOpaqueWhite(cd.color)) return
            if (!isWide(v)) return
            v.setBackgroundColor(0xFF1E1E1E.toInt())
            XposedBridge.log("$TAG: white color surface darkened id=${v.id}")
            return
        }

        val bg = v.background as? android.graphics.drawable.GradientDrawable ?: return
        val lp = v.layoutParams ?: return
        if (!isWide(v, lp)) return
        val csl = runCatching { bg.color }.getOrNull() ?: return
        if (csl.isStateful) return
        if (!isNearOpaqueWhite(csl.defaultColor)) return
        val copy = runCatching { bg.constantState?.newDrawable()?.mutate() }.getOrNull() ?: return
        val gd = copy as? android.graphics.drawable.GradientDrawable ?: return
        gd.setColor(0xFF1E1E1E.toInt())
        v.background = gd
        XposedBridge.log("$TAG: white surface darkened id=${v.id}")
    }

    /**
     * 把父容器的圆角背景改成深色，保留原有圆角和描边。
     *
     * 用于 toast 这类 wrap_content 的小容器：它们不满足「满宽」条件，
     * 走不到 paintWhiteSurface，但同样是白底白字。
     */
    private fun darkenParentBackground(v: android.view.View) {
        val parent = v.parent as? android.view.View ?: return
        val bg = parent.background as? android.graphics.drawable.GradientDrawable ?: return
        runCatching {
            val copy = bg.constantState?.newDrawable()?.mutate() as? android.graphics.drawable.GradientDrawable
                ?: return
            copy.setColor(0xFF2A2A2A.toInt())
            parent.background = copy
        }
    }

    /**
     * 把浅色的按钮背景压深，保留圆角。
     *
     * 用于确认按钮：文字用的是 onaccent（白色），一旦背景是浅色就读不出来。
     * 只处理 RGB 三个通道都 >= 0xB0 的浅色，品牌色和状态色不会被误伤。
     */
    private fun darkenLightButton(v: android.view.View) {
        val bg = v.background as? android.graphics.drawable.GradientDrawable ?: return
        val csl = runCatching { bg.color }.getOrNull() ?: return
        if (csl.isStateful) return
        if (!isLightColor(csl.defaultColor)) return
        runCatching {
            val copy = bg.constantState?.newDrawable()?.mutate() as? android.graphics.drawable.GradientDrawable
                ?: return
            copy.setColor(0xFF2A2A2A.toInt())
            v.background = copy
        }
    }

    /** 不透明且三个通道都 >= 0xB0 视为浅色 */
    private fun isLightColor(c: Int): Boolean {
        val a = (c ushr 24) and 0xFF
        if (a < 0x80) return false
        return ((c ushr 16) and 0xFF) >= 0xB0 &&
            ((c ushr 8) and 0xFF) >= 0xB0 &&
            (c and 0xFF) >= 0xB0
    }

    /**
     * 是否横向铺满。
     *
     * 判定：
     *   1) MATCH_PARENT（-1）
     *   2) 显式写死的宽像素 > 屏幕 80%
     *   3) 已经完成布局，实测宽度 >= 屏幕 80%
     *      —— bottom_sheet_skills.xml 的 title_bar 用的是 ConstraintLayout
     *         的 match_constraint 写法（width=0 + 左右约束），layoutParams.width
     *         是 0，只有 layout 之后的 v.width 才是屏幕宽。
     */
    private fun isWide(
        v: android.view.View,
        lp: android.view.ViewGroup.LayoutParams? = v.layoutParams,
    ): Boolean {
        val screenW = v.resources.displayMetrics.widthPixels
        if (lp != null) {
            if (lp.width == android.view.ViewGroup.LayoutParams.MATCH_PARENT) return true
            if (lp.width > screenW * 0.8f) return true
        }
        return v.width >= screenW * 0.8f
    }

    /**
     * J. 白色底板统一拦截 —— 不依赖布局时机和尺寸。
     *
     * 【之前为什么漏】
     * 1) onAttachedToWindow 触发时 View 还没测量，width/height 都是 0，
     *    我上一版用「宽度 ≥ 85% 屏幕」做判断，于是全部提前 return。
     * 2) BottomSheetDialogFragment 的 View 挂在 Dialog 的独立 Window 上，
     *    根本不在 Activity.decorView 里，按 Activity 遍历永远够不到。
     *
     * 【这一版】
     * 直接 hook View.setBackground(Drawable)。这是所有背景的最终入口，
     * 无论是 XML inflate 还是代码 setBackgroundResource，都会经过这里，
     * 也不受测量时机和窗口归属影响。
     *
     * 判定条件（三重收紧，避免误伤）：
     *   - 是 GradientDrawable
     *   - 它的纯色（getColor()，API 24+）接近纯白且不透明
     *   - 该 View 的布局尺寸允许接近满宽（layoutParams 里 MATCH_PARENT 或很大），
     *     不能等到测量后才知道，所以改看 layoutParams
     */
    private fun hookWhiteBackground(cl: ClassLoader) {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val v = param.thisObject as? android.view.View ?: return
                val bg = param.args[0] as? android.graphics.drawable.GradientDrawable ?: return
                // GradientDrawable.getColor() 在 API 24+ 返回 ColorStateList
                val csl = runCatching { bg.color }.getOrNull() ?: return
                if (csl.isStateful) return
                if (!isNearOpaqueWhite(csl.defaultColor)) return
                // 只处理横向铺满的容器，避免把白色小圆点/图标底也刷掉
                val lp = v.layoutParams
                val wide = lp != null &&
                    (lp.width == android.view.ViewGroup.LayoutParams.MATCH_PARENT ||
                        lp.width > v.resources.displayMetrics.widthPixels * 0.8f)
                if (!wide) return
                runCatching {
                    val d = bg.constantState?.newDrawable()?.mutate() ?: bg.mutate()
                    d.colorFilter = android.graphics.PorterDuffColorFilter(
                        0xFF1E1E1E.toInt(), android.graphics.PorterDuff.Mode.SRC_IN,
                    )
                    param.args[0] = d
                }
            }
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                android.view.View::class.java,
                "setBackground", android.graphics.drawable.Drawable::class.java,
                hook,
            )
            XposedBridge.log("$TAG: white background hook installed")
        }.onFailure { XposedBridge.log("$TAG: setBackground hook skipped -> ${it.message}") }

        runCatching {
            XposedHelpers.findAndHookMethod(
                android.view.View::class.java,
                "setBackgroundColor", java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val c = (param.args[0] as? Number)?.toInt() ?: return
                        if (!isNearOpaqueWhite(c)) return
                        val v = param.thisObject as? android.view.View ?: return
                        val lp = v.layoutParams ?: return
                        val wide = lp.width == android.view.ViewGroup.LayoutParams.MATCH_PARENT ||
                            lp.width > v.resources.displayMetrics.widthPixels * 0.8f
                        if (wide) param.args[0] = 0xFF1E1E1E.toInt()
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: setBackgroundColor hook skipped -> ${it.message}") }
    }

    /** 判定是否接近纯白。alpha 门槛放到 0xB0，覆盖 #B3FFFFFF 这种半透明白底 */
    private fun isNearOpaqueWhite(color: Int): Boolean {
        val a = (color ushr 24) and 0xFF
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return a >= 0xB0 && r >= 0xE8 && g >= 0xE8 && b >= 0xE8
    }

    /** 需要处理的控件名 */
    private val darkViewNames = setOf(
        "full_input_container",
        "main_input_area",
        "title_bar_mark",
        "ll_message_container",
        "btn_scroll_to_bottom",
        // 人类确认（提问选项）卡片：trae_dialog_human_checkpoint.xml
        "normal_content",
        "card_container",
        "title_scroll_view",
        "option_root",
        "checkbox_container",
        // 加号菜单（输入区上下文选择）：图标是黑色 vector，需要 tint
        "iv_direct_icon",
        "iv_source_icon",
        "tv_direct_title",
        "tv_source_title",
        // 输入框底部操作按钮：只列深黑图标。紫色语音键 / 黄色通行证键保持原色，
        // 染白会破坏品牌色，所以不放进名单。
        "btn_plus",
        "btn_microphone",
        "btn_hold_to_talk",
        // toast：trae_layout_inline_toast.xml 的根是 wrap_content，
        // 不满足「满宽」条件，所以白色底一直没被处理，白底白字看不见。
        "tv_inline_toast_message",
        // 思考过程正文：trae_item_detail_thought.xml 的 tv_content 用
        // trae_text_text_secondary，night 值是 #737373，在深色背景上偏暗。
        "tv_content",
        // 确认类按钮。两个来源：
        //   1) dialog_edit_name.xml 的 btn_save / btn_cancel，
        //      背景 bg_dialog_btn 是浅灰 #FFE8E9EB，按钮宽度只有半屏，
        //      满宽条件不成立，所以白字压在浅灰底上。
        //   2) trae_dialog_custom_confirm.xml 的 btn_confirm，
        //      背景 trae_bg_bg_invert 在深色模式下反转成白色，
        //      而文字 trae_text_text_onaccent 也是白色，白底白字。
        "btn_confirm",
        "btn_save",
        "btn_ok",
        "btn_done",
        "btn_cancel",
        // 插件 / 技能选择面板的空态类容器。
        // 注意：content_container / search_container 被 6 个布局共用，
        // 按名字全局改会误伤消息气泡，改为在 View 上判断父链里的 title_bar 来决定。
    )

    /** 按控件名执行深色改写 */
    private fun paintDark(v: android.view.View, name: String) {
        val density = v.resources.displayMetrics.density
        when (name) {
            // 输入区外层：真正的圆角背景层。原本走 trae_bg_input_bottom_layer，
            // 该 drawable 的 solid 引用 overlay_l2（有 night 变体），但 TRAE 的
            // applyOverrideConfiguration 抹掉了 uiMode，导致取到浅色值。
            "full_input_container" -> {
                v.background = android.graphics.drawable.GradientDrawable().apply {
                    setShape(android.graphics.drawable.GradientDrawable.RECTANGLE)
                    setColor(0xFF232323.toInt())
                    cornerRadius = 28f * density
                    setStroke((1f * density).toInt(), 0x33FFFFFF.toInt())
                }
            }
            // 输入区上层：原本是 #B3FFFFFF 半透明白，直接去掉
            "main_input_area" -> v.background = null
            // 顶部渐变白条：整条隐藏
            "title_bar_mark" -> v.visibility = android.view.View.GONE
            // 用户气泡：深色圆角底 + 所有子 TextView 改成浅色文字
            "ll_message_container" -> {
                v.background = android.graphics.drawable.GradientDrawable().apply {
                    setShape(android.graphics.drawable.GradientDrawable.RECTANGLE)
                    setColor(0xFF333333.toInt())
                    cornerRadius = 20f * density
                }
                recolorTexts(v, 0xFFE5E5E5.toInt())
            }
            // 回到底部按钮：白圆底改深色
            "btn_scroll_to_bottom" -> {
                v.background = android.graphics.drawable.GradientDrawable().apply {
                    setShape(android.graphics.drawable.GradientDrawable.OVAL)
                    setColor(0xFF2A2A2A.toInt())
                }
            }
            // 提问选项卡片内容区：trae_bg_checkpoint_card 的 solid 是硬编码
            // #FFFFFFFF，无 night 变体，且由 XML inflate 直接写入，走不到 setColor。
            "normal_content" -> {
                v.background = rounded(0xFF1E1E1E.toInt(), 24f * density)
            }
            // 卡片外层 CardView：边框和阴影本体，也得压深。
            // 模块没有 cardview 依赖，用反射调 setCardBackgroundColor。
            "card_container" -> runCatching {
                XposedHelpers.callMethod(v, "setCardBackgroundColor", 0xFF1E1E1E.toInt())
            }
            // 问题标题滚动区：去掉浅色渐隐边
            "title_scroll_view" -> {
                runCatching {
                    XposedHelpers.callMethod(v, "setRequiresFadingEdge", 0)
                }
            }
            // 单个选项行：trae_bg_checkpoint_option_normal 也是硬编码浅色
            "option_root" -> {
                v.background = rounded(0xFF1E1E1E.toInt(), 12f * density)
                recolorTexts(v, 0xFFE5E5E5.toInt())
            }
            // 选项序号框：trae_bg_checkpoint_option_index 硬编码浅色
            "checkbox_container" -> {
                v.background = rounded(0xFF2E2E2E.toInt(), 6f * density)
            }
            // 加号菜单图标：vector 本身是黑色，深色底上完全看不见，改成浅色
            "iv_direct_icon", "iv_source_icon" -> (v as? android.widget.ImageView)
                ?.setColorFilter(0xFFE5E5E5.toInt())
            // 加号菜单标题文字
            "tv_direct_title", "tv_source_title" -> (v as? android.widget.TextView)
                ?.setTextColor(0xFFE5E5E5.toInt())
            // 输入框底部操作按钮。只有真正是深色的那几张需要提亮：
            //   ic_input_bar_add.png           (37,37,37) 深黑 -> 必须提亮
            //   ic_input_bar_microphone.png    (37,37,37) 深黑 -> 必须提亮
            //   ic_hold_to_talk.png            (29,30,29) 深黑 -> 必须提亮
            // 下面几个本来就是彩色，染白会破坏品牌色，绝不能动：
            //   ic_input_bar_voice_chat.png    (83,72,228) 紫色
            //   ic_input_bar_express_999.png   (207,175,30) 黄色
            //   ic_input_bar_use_express_pass  (17,199,121) 绿色
            //   ic_input_arrow_up.png          (82,71,228) 紫色
            "btn_plus", "btn_microphone", "btn_hold_to_talk" ->
                (v as? android.widget.ImageView)?.setColorFilter(0xFFE5E5E5.toInt())
            // toast 文字本身跟随主题（是浅色），问题在它的容器背景。
            // 容器是 wrap_content，满宽判断永远不成立，所以这里直接把父容器
            // 的圆角背景复制一份改成深色，保留 16dip 圆角和描边。
            "tv_inline_toast_message" -> darkenParentBackground(v)
            // 思考过程正文提亮。#737373 是 secondary 的 night 值，
            // 只在当前确实是这个值时才改，避免影响到其它用同名 id 的 TextView。
            "tv_content" -> (v as? android.widget.TextView)?.let { tv ->
                if (tv.currentTextColor == 0xFF737373.toInt()) {
                    tv.setTextColor(0xFFB8B8B8.toInt())
                }
            }
            // 确认类按钮：背景只要是浅色就压深，保证白色文字能读出来。
            // 品牌色（绿、蓝、红）的某个通道会很低，不会被误判。
            "btn_confirm", "btn_save", "btn_ok", "btn_done", "btn_cancel" -> darkenLightButton(v)
        }
    }

    /**
     * 按资源 id 精确给输入区图标上色。
     *
     * 这些图标是 png 位图，没有 tint 属性，只能靠 setColorFilter。
     * 只在「深色不可读」的那几张上动手，紫色品牌图和绿色等彩色图保持原样。
     */
    private fun hookSendIconResources(cl: ClassLoader) {
        // 资源 id -> 目标色。全部通过 _ids.py 反查确认。
        // 注意：这里只列深灰/近黑的图标。
        val recolor = HashMap<Int, Int>()

        // 图标资源名 -> id 在运行时按名字解析，避免硬编码出错。
        // 只列深色图标。ic_input_bar_voice_chat 是紫色 (83,72,228)，
        // ic_input_arrow_up 是紫色 (82,71,228)，都不需要处理。
        val names = listOf(
            "ic_input_bar_add",
            "ic_input_bar_microphone",
            "ic_hold_to_talk",
            "ic_input_arrow_up_enable",
            // 消息长按菜单 / 会话三点菜单的图标，实测像素：
            //   ic_menu_copy.png              (49,53,58)  深灰
            //   ic_menu_delete.png            (49,53,58)  深灰
            //   ic_menu_revert.png            (48,53,58)  深灰
            //   ic_conversation_rename.png    (27,27,26)  近黑
            //   ic_conversation_menu_artifact.png (27,27,26) 近黑
            // ic_conversation_delete.png 是红色 (231,70,58)，本身可见，不放进来。
            "ic_menu_copy",
            "ic_menu_delete",
            "ic_menu_revert",
            "ic_conversation_rename",
            "ic_conversation_menu_artifact",
        )
        val ids = HashMap<String, Int>()
        for (nm in names) {
            val id = runCatching {
                Class.forName("com.bytedance.trae.conversation.R\$drawable", false, cl)
                    .getField(nm).getInt(null)
            }.getOrNull() ?: continue
            ids[nm] = id
            recolor[id] = 0xFFE5E5E5.toInt()
        }
        XposedBridge.log("$TAG: send icon ids resolved=${ids.keys}")

        runCatching {
            XposedHelpers.findAndHookMethod(
                android.widget.ImageView::class.java,
                "setImageResource", java.lang.Integer.TYPE,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val id = (param.args[0] as? Number)?.toInt() ?: return
                        val color = recolor[id] ?: return
                        (param.thisObject as? android.widget.ImageView)
                            ?.setColorFilter(color)
                    }
                },
            )
            XposedBridge.log("$TAG: setImageResource icon tint installed")
        }.onFailure { XposedBridge.log("$TAG: setImageResource hook skipped -> ${it.message}") }

        // XML 里的 android:src 不走 setImageResource。
        // ImageView 构造函数读 styleable 时是：
        //     mResource = mSrcResId = a.getResourceId(...);
        //     setImageDrawable(drawable);
        // 所以 hook setImageDrawable，再反射读 mSrcResId 就能拿到资源 id，
        // 精确知道这张图是不是我们要提亮的那几张。
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.widget.ImageView::class.java,
                "setImageDrawable", android.graphics.drawable.Drawable::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val iv = param.thisObject as? android.widget.ImageView ?: return
                        val srcId = runCatching {
                            XposedHelpers.getIntField(iv, "mSrcResId")
                        }.getOrNull() ?: return
                        val color = recolor[srcId] ?: return
                        iv.setColorFilter(color)
                    }
                },
            )
            XposedBridge.log("$TAG: setImageDrawable icon tint installed")
        }.onFailure { XposedBridge.log("$TAG: setImageDrawable hook skipped -> ${it.message}") }
    }

    /**
     * K. 硬编码纯黑文字统一提亮。
     *
     * trae_revert_dialog.xml 里三处文字都是写死的 #FF000000：
     *   tv_dialog_title / tv_message / btn_cancel
     * 没有 night 变体，也不引用任何主题色，所以深色背景下完全看不见。
     * btn_confirm 是 #FFFF050A 红色，属于危险操作提示，保持不动。
     *
     * 这里只处理「不透明且 RGB 全部 <= 0x20」的纯黑/近黑，
     * 不会碰品牌色、状态色和任何带透明度的文字。
     */
    private fun hookBlackText() {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val c = (param.args[0] as? Number)?.toInt() ?: return
                if (isNearBlack(c)) param.args[0] = 0xFFE5E5E5.toInt()
            }
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.widget.TextView::class.java,
                "setTextColor", java.lang.Integer.TYPE, hook,
            )
        }.onFailure { XposedBridge.log("$TAG: textColor(int) hook skipped -> ${it.message}") }

        // XML 的 android:textColor 在 TextView 构造里走 ColorStateList 重载
        runCatching {
            XposedHelpers.findAndHookMethod(
                android.widget.TextView::class.java,
                "setTextColor", android.content.res.ColorStateList::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val csl = param.args[0] as? android.content.res.ColorStateList ?: return
                        if (csl.isStateful) return
                        if (!isNearBlack(csl.defaultColor)) return
                        param.args[0] = android.content.res.ColorStateList.valueOf(0xFFE5E5E5.toInt())
                    }
                },
            )
            XposedBridge.log("$TAG: black text hook installed")
        }.onFailure { XposedBridge.log("$TAG: textColor(csl) hook skipped -> ${it.message}") }
    }

    /** 不透明且 RGB 都 <= 0x20 视为纯黑 */
    private fun isNearBlack(c: Int): Boolean {
        val a = (c ushr 24) and 0xFF
        if (a < 0x80) return false
        return ((c ushr 16) and 0xFF) <= 0x20 &&
            ((c ushr 8) and 0xFF) <= 0x20 &&
            (c and 0xFF) <= 0x20
    }

    /** 生成一个纯色圆角 drawable */
    private fun rounded(color: Int, radius: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setShape(android.graphics.drawable.GradientDrawable.RECTANGLE)
            setColor(color)
            cornerRadius = radius
        }

    /** 把子树里所有 TextView 的文字色刷成指定值 */
    private fun recolorTexts(root: android.view.View, color: Int) {
        if (root is android.widget.TextView) root.setTextColor(color)
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                root.getChildAt(i)?.let { recolorTexts(it, color) }
            }
        }
    }

    /**
     * L. 思考内容文字提亮。
     *
     * 思考不是 XML，而是 ThoughtRenderer.render() 里动态创建的：
     *   SimpleMarkdownTextView tv = new SimpleMarkdownTextView(context, ...);
     *   tv.setTextColor(ContextCompat.getColor(context,
     *       com.bytedance.trae.common_ui.R.color.trae_text_text_default));
     *
     * 注意颜色取的是 common_ui 包里的 trae_text_text_default，和主包同名但不同资源，
     * 在深色下取到的值偏灰，所以正文看起来灰蒙蒙的。
     * 直接在 render 返回后覆盖成亮色最直接，也不会影响普通消息。
     */
    private fun hookThoughtText(cl: ClassLoader) {
        // 方案 A：直接改取色入口。
        // 思考正文和思考状态分别由 ThoughtRenderer / ThinkingRenderer 渲染，
        // 二者都用 ContextCompat.getColor(context, com.bytedance.trae.common_ui.R.color.xxx)
        // 取色。common_ui 里这套文字色在深色下偏灰，所以从这里统一替换，
        // 比逐个 hook render 覆盖面更全。
        val overrides = HashMap<Int, Int>()
        runCatching {
            val colorCls = Class.forName("com.bytedance.trae.common_ui.R\$color", false, cl)
            fun id(name: String): Int = colorCls.getField(name).getInt(null)
            overrides[id("trae_text_text_default")] = 0xFFE5E5E5.toInt()
            overrides[id("trae_text_text_secondary")] = 0xFFC0C0C0.toInt()
            overrides[id("trae_text_text_tertiary")] = 0xFFA8A8A8.toInt()
        }.onFailure { XposedBridge.log("$TAG: common_ui color ids failed -> ${it.message}") }
        XposedBridge.log("$TAG: common_ui text color overrides=${overrides.size}")

        if (overrides.isNotEmpty()) {
            runCatching {
                val compatCls = XposedHelpers.findClass(
                    "androidx.core.content.ContextCompat", cl,
                )
                XposedHelpers.findAndHookMethod(
                    compatCls, "getColor",
                    android.content.Context::class.java, java.lang.Integer.TYPE,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val id = (param.args.getOrNull(1) as? Number)?.toInt() ?: return
                            val c = overrides[id] ?: return
                            param.result = c
                        }
                    },
                )
                XposedBridge.log("$TAG: ContextCompat.getColor hook installed")
            }.onFailure { XposedBridge.log("$TAG: getColor hook skipped -> ${it.message}") }
        }

        // 方案 B：兜底，直接改 render 返回的 View。
        runCatching {
            val rendererCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.chat.block.renderer.ThoughtRenderer", cl,
            )
            val thoughtCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.chat.block.AgentContentBlock\$Thought", cl,
            )
            XposedHelpers.findAndHookMethod(
                rendererCls, "render",
                android.content.Context::class.java, thoughtCls,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val tv = param.result as? android.widget.TextView ?: return
                        runCatching { tv.setTextColor(0xFFDCDCDC.toInt()) }
                    }
                },
            )
            XposedBridge.log("$TAG: thought text hook installed")
        }.onFailure { XposedBridge.log("$TAG: thought text hook skipped -> ${it.message}") }
    }

    /**
     * M. 技能加载诊断（临时）。
     *
     * 已确认入口可见性恒为 true（ChatAttachmentConfig 的 SKILLS lambda 直接
     * return true），所以「技能少」只可能是数据层问题。
     * 这一段把关键节点记到文件，便于定位真实原因：
     *   /sdcard/Android/data/com.bytedance.trae.cn/files/trae_patch.log
     */
    private fun installSkillDiagnostics(cl: ClassLoader) {
        val tag = "$TAG[diag]"

        fun log(msg: String) {
            XposedBridge.log("$tag: $msg")
            runCatching {
                val app = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication",
                ) as? android.content.Context ?: return
                val dir = app.getExternalFilesDir(null) ?: app.filesDir
                java.io.File(dir, "trae_patch.log")
                    .appendText("${System.currentTimeMillis()} $msg\n")
            }
        }

        // 1) 打开技能面板时，记录设备类型和上下文参数
        runCatching {
            val fragmentCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.widget.InputFragment", cl,
            )
            val deviceCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.devices.SelectedDeviceItem", cl,
            )
            XposedHelpers.findAndHookMethod(
                fragmentCls, "showSkillsBottomSheetForDevice",
                deviceCls, java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val d = param.args[0]
                        val type = runCatching { XposedHelpers.callMethod(d, "getType") }.getOrNull()
                        val id = runCatching { XposedHelpers.callMethod(d, "getId") }.getOrNull()
                        val dir = runCatching { XposedHelpers.callMethod(d, "getSelectedDirectory") }.getOrNull()
                        val pid = runCatching { XposedHelpers.callMethod(d, "getProjectId") }.getOrNull()
                        log("open skills type=$type id=$id dir=$dir pid=$pid")
                    }
                },
            )
        }.onFailure { log("hook showSkillsBottomSheetForDevice failed: ${it.message}") }

        // 2) 记录每次接口返回的技能数量
        runCatching {
            val dataCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.network.ListSkillsData", cl,
            )
            XposedHelpers.findAndHookMethod(
                dataCls, "getAllItems",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val list = param.result as? List<*> ?: return
                        log("api items=${list.size}")
                    }
                },
            )
        }.onFailure { log("hook getAllItems failed: ${it.message}") }

        // 3) 记录最终发布到 UI 的数量，以及是否走了 Hub
        runCatching {
            val vmCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.skill.SkillsViewModel", cl,
            )
            XposedHelpers.findAndHookMethod(
                vmCls, "publishSkills",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val vm = param.thisObject
                        val ordinary = runCatching {
                            XposedHelpers.getObjectField(vm, "ordinarySkills") as? List<*>
                        }.getOrNull()
                        val plugin = runCatching {
                            XposedHelpers.getObjectField(vm, "pluginSkills") as? List<*>
                        }.getOrNull()
                        val cliId = runCatching {
                            XposedHelpers.getObjectField(vm, "cliId")
                        }.getOrNull()
                        log("publish ordinary=${ordinary?.size} plugin=${plugin?.size} cliId=$cliId")
                    }
                },
            )
        }.onFailure { log("hook publishSkills failed: ${it.message}") }
    }

    /**
     * I. 【已停用，未在 handleLoadPackage 中调用】
     *
     * 之前为了修「远程会话技能少」注入了 cliId / workspaceFolder / projectId
     * 并强制 includePluginSkills=true。实际证明 TRAE 原生就能正确加载那台电脑的
     * 技能，这套注入反而把数据源改坏了，所以整段停用，恢复原生行为。
     * 保留代码仅作记录。
     *
     * 逆向 InputFragment.showSkillsBottomSheetForDevice()：
     *
     *   CliType type = device.getType();
     *   switch (type) {
     *     case LOCAL: showSkillsBottomSheet(device.getId(), dir, null, false, ...);
     *     case IDE:   showSkillsBottomSheet(device.getId(), dir, projectId, true, ...);
     *     default:    showSkillsBottomSheet(null, null, null, false, ...);  // REMOTE 落这里
     *   }
     *
     * 而 SkillsViewModel 里 SkillRequestPolicy.shouldUseHubApi() 的判定是
     * cliId 非空才走 Hub（远程设备技能），cliId 为空就走账号级普通技能列表。
     * 所以远程 PC 会话点技能看到的是账号技能，不是那台 PC 上的技能。
     *
     * 修法：在 showSkillsBottomSheetForDevice 入口把 REMOTE 设备的 id 记下来，
     * 等到 newInstance 被调用时替换第一个参数 cliId。
     */
    private fun hookRemoteSkills(cl: ClassLoader) {
        // 用静态变量而不是 ThreadLocal：showSkillsBottomSheetForDevice 到
        // newInstance 之间可能跨协程线程，ThreadLocal 会丢值。
        // 记录三元组：设备 id / 该设备当前选中的工作目录 / 项目 id。
        // 之前只补了 cliId，workspaceFolder 和 projectId 仍为 null，
        // Hub API 少了这两个参数就查不到那台 PC 工作目录下的本地技能。
        val pending = java.util.concurrent.atomic.AtomicReference<Triple<String, String?, String?>?>(null)

        // 1) 在分发入口截获 REMOTE 设备
        runCatching {
            val inputFragmentCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.widget.InputFragment", cl,
            )
            val deviceCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.devices.SelectedDeviceItem", cl,
            )
            XposedHelpers.findAndHookMethod(
                inputFragmentCls,
                "showSkillsBottomSheetForDevice",
                deviceCls, java.lang.Boolean.TYPE,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        pending.set(null)
                        val device = param.args[0] ?: return
                        val type = runCatching {
                            XposedHelpers.callMethod(device, "getType")
                        }.getOrNull() ?: return
                        val id = runCatching {
                            XposedHelpers.callMethod(device, "getId") as? String
                        }.getOrNull()
                        val dir = runCatching {
                            XposedHelpers.callMethod(device, "getSelectedDirectory") as? String
                        }.getOrNull()
                        val pid = runCatching {
                            XposedHelpers.callMethod(device, "getProjectId") as? String
                        }.getOrNull()
                        XposedBridge.log("$TAG: skill entry type=$type id=$id dir=$dir pid=$pid")
                        if (type.toString() == "REMOTE" && !id.isNullOrBlank()) {
                            pending.set(Triple(id, dir, pid))
                        }
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: remote skill entry hook skipped -> ${it.message}") }

        // 2) 在 Companion.newInstance 上补 cliId。
        //    最后一个参数是 SoloChatMode 枚举，必须用真实类型而不是 Object，
        //    否则 findAndHookMethod 找不到方法。
        runCatching {
            val companionCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.skill.SkillsBottomSheet\$Companion", cl,
            )
            val modeCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.tracker.SoloChatMode", cl,
            )
            XposedHelpers.findAndHookMethod(
                companionCls, "newInstance",
                String::class.java, String::class.java, String::class.java,
                java.lang.Boolean.TYPE, String::class.java, String::class.java,
                String::class.java, java.lang.Boolean.TYPE, modeCls,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val info = pending.get() ?: return
                        XposedBridge.log(
                            "$TAG: injecting cliId=${info.first} dir=${info.second} pid=${info.third}",
                        )
                        // cliId：让 SkillRequestPolicy.shouldUseHubApi() 返回 true
                        param.args[0] = info.first
                        // workspaceFolder / projectId：Hub API 靠这两个参数定位
                        // 那台 PC 上具体工作目录的技能，留空就只能拿到设备级列表。
                        if (param.args[1] == null && !info.second.isNullOrBlank()) {
                            param.args[1] = info.second
                        }
                        if (param.args[2] == null && !info.third.isNullOrBlank()) {
                            param.args[2] = info.third
                        }
                        param.args[3] = true
                        // 第 8 个参数 includePluginSkills。
                        //
                        // SkillsViewModel.loadSkills() 里：
                        //   pluginSkillsResolved = !includePluginSkills;
                        //   if (includePluginSkills) launch { pluginSkills = ... };
                        //   fetchSkills(false);
                        // publishSkills() 最终发布的是 ordinarySkills + pluginSkills。
                        //
                        // 所以这个开关决定「本地/插件技能」那一组要不要一起拉。
                        // 远程会话下它由 isPluginVisible 策略算出来，常常是 false，
                        // 于是只有 Hub 那一批远程技能，本地技能全没了。
                        // 置 true 后两组会合并显示。
                        param.args[7] = true
                    }
                },
            )
            XposedBridge.log("$TAG: newInstance hook installed")
        }.onFailure { XposedBridge.log("$TAG: newInstance hook skipped -> ${it.message}") }

        // 3) 最后一道保险：直接 hook SkillsViewModel.loadSkills()。
        //    即便 newInstance 的 hook 因为签名不匹配没装上，这里也能在真正发起请求前
        //    把 cliId 补进去 —— loadSkills 内部第一句就是读 this.cliId 决定走哪个 API。
        runCatching {
            val vmCls = XposedHelpers.findClass(
                "com.bytedance.trae.conversation.skill.SkillsViewModel", cl,
            )
            XposedHelpers.findAndHookMethod(
                vmCls, "loadSkills",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val vm = param.thisObject ?: return
                        // 无条件打开插件/本地技能那一组的拉取，保证列表是
                        // ordinarySkills + pluginSkills 合并后的完整结果。
                        runCatching {
                            XposedHelpers.setBooleanField(vm, "includePluginSkills", true)
                        }
                        // cliId 只在远程会话时补，非远程保持原样。
                        val info = pending.getAndSet(null) ?: return
                        val id = info.first
                        val cur = runCatching {
                            XposedHelpers.getObjectField(vm, "cliId") as? String
                        }.getOrNull()
                        if (cur.isNullOrBlank()) {
                            XposedHelpers.setObjectField(vm, "cliId", id)
                            // 同理补上工作目录和项目 id，Hub 查询才会覆盖
                            // 那台 PC 当前目录下的本地技能。
                            if (!info.second.isNullOrBlank()) {
                                runCatching {
                                    XposedHelpers.setObjectField(vm, "workspaceFolder", info.second)
                                }
                            }
                            if (!info.third.isNullOrBlank()) {
                                runCatching {
                                    XposedHelpers.setObjectField(vm, "projectId", info.third)
                                }
                            }
                            XposedBridge.log("$TAG: loadSkills cliId patched to $id")
                        }
                    }
                },
            )
            XposedBridge.log("$TAG: loadSkills hook installed")
        }.onFailure { XposedBridge.log("$TAG: loadSkills hook skipped -> ${it.message}") }
    }

    /** 反射调 AppCompatDelegate.setDefaultNightMode，让资源走 -night 目录 */
    private fun applyAppCompatNight(cl: ClassLoader) {
        runCatching {
            val cls = XposedHelpers.findClass(CLS_APPCOMPAT_DELEGATE, cl)
            XposedHelpers.callStaticMethod(cls, "setDefaultNightMode", MODE_NIGHT_YES)
        }.onFailure { XposedBridge.log("$TAG: appcompat night skipped -> ${it.message}") }
    }

    /**
     * G. 强制 Compose 主题为深色 —— 通过替换色板对象。
     *
     * 【为什么不能 hook TraeDesignTheme】
     * 上一版 hook 了这个 @Composable 函数并改 args[0] = true，
     * 实测全白无效：Compose 编译器把它内联进了调用方（调用点多、函数体小），
     * 运行期并不存在一次真实的 invokestatic，所以 hook 永远不触发。
     * 开屏变黑但界面全白，正好说明只有 AppCompat 的 -night 资源生效了，
     * Compose 那部分完全没走到。
     *
     * 【这一版的办法】
     * 直接换色板数据源。TraeColorTokens 的结构是：
     *
     *   private static final TraeColors Light = TraeLightColors.INSTANCE;
     *   private static final TraeColors Dark  = TraeDarkColors.INSTANCE;
     *   public TraeColors getLight() { return Light; }
     *   public TraeColors getDark()  { return Dark;  }
     *
     * TraeLightColors / TraeDarkColors 都继承同一个抽象类 TraeColors，类型完全兼容。
     * 所以只要把 Light 换成 Dark，无论调用方传 true 还是 false、
     * 有没有被内联、走哪条取色路径，最终拿到的都是深色板。
     *
     * 同时做两件事，互为保险：
     *   1. 直接替换静态字段 Light = Dark 实例（覆盖"字段被直接读"的情况）
     *   2. hook getLight() 恒返回 Dark（覆盖"方法未被内联且早于字段替换被调用"的情况）
     */
    private fun hookComposeDarkTheme(cl: ClassLoader) {
        val tokensCls = runCatching {
            XposedHelpers.findClass(CLS_COLOR_TOKENS, cl)
        }.getOrNull()
        if (tokensCls == null) {
            XposedBridge.log("$TAG: palette class not found")
            return
        }

        // 方式一（最强）：直接把静态字段 Light 改成 Dark 实例。
        // 这样即使 getLight() 被内联进调用方、读的是字段本身，也一律拿到深色板。
        runCatching {
            val dark = XposedHelpers.getStaticObjectField(tokensCls, "Dark")
            if (dark != null) {
                XposedHelpers.setStaticObjectField(tokensCls, "Light", dark)
                XposedBridge.log("$TAG: Light field replaced by Dark")
            }
        }.onFailure { XposedBridge.log("$TAG: palette field swap skipped -> ${it.message}") }

        // 方式二（冗余保险）：getLight() 恒返回 Dark。
        // 部分路径可能在字段替换前就缓存了旧引用，这里再兜一层。
        runCatching {
            XposedHelpers.findAndHookMethod(
                tokensCls, "getLight",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val dark = XposedHelpers.getStaticObjectField(
                            param.method.declaringClass, "Dark",
                        )
                        if (dark != null) param.result = dark
                    }
                },
            )
            XposedBridge.log("$TAG: getLight -> Dark hook installed")
        }.onFailure { XposedBridge.log("$TAG: palette getter hook skipped -> ${it.message}") }

        // 方式三（最底层兜底）：把 TraeLightColors 的每个取色方法转发到 TraeDarkColors 同名方法。
        // 这一步绕过所有中间层：即使字段替换失败、getter 被内联、
        // 只要最终取到颜色，就一定经过某个 getXxx(long) 方法。
        hookPaletteMethods(cl)

        // 诊断：打印当前 Light 字段指向的实例类型，确认替换是否成功
        runCatching {
            XposedHelpers.getStaticObjectField(tokensCls, "Light")?.let {
                XposedBridge.log("$TAG: Light now -> ${it.javaClass.name}")
            }
            XposedHelpers.getStaticObjectField(tokensCls, "Dark")?.let {
                XposedBridge.log("$TAG: Dark -> ${it.javaClass.name}")
            }
        }.onFailure { XposedBridge.log("$TAG: diag failed -> ${it.message}") }
    }

    /**
     * 遍历 TraeColors 抽象方法，在浅色实现上 hook，转发到深色实现的同名方法。
     *
     * TraeColors 的每个方法返回 long（Compose Color 是 @JvmInline value class，
     * 编译后就是 long），签名形如 mo9297getAccentAccentAmber0d7_KjU()J。
     * 两个实现类方法名完全一致，可以直接按名字对应。
     */
    private fun hookPaletteMethods(cl: ClassLoader) {
        val lightCls = runCatching {
            XposedHelpers.findClass("$CLS_PALETTE_PREFIX.LightColors", cl)
        }.getOrNull()
        val darkCls = runCatching {
            XposedHelpers.findClass("$CLS_PALETTE_PREFIX.DarkColors", cl)
        }.getOrNull()
        val baseCls = runCatching {
            XposedHelpers.findClass(CLS_PALETTE_BASE, cl)
        }.getOrNull()

        if (lightCls == null || darkCls == null || baseCls == null) {
            XposedBridge.log("$TAG: palette classes missing light=$lightCls dark=$darkCls base=$baseCls")
            return
        }

        var count = 0
        for (m in baseCls.declaredMethods) {
            if (m.returnType != java.lang.Long.TYPE) continue
            if (m.parameterTypes.isNotEmpty()) continue
            runCatching {
                val darkMethod = darkCls.getDeclaredMethod(m.name)
                darkMethod.isAccessible = true
                val darkInstance = XposedHelpers.getStaticObjectField(darkCls, "INSTANCE")
                XposedHelpers.findAndHookMethod(
                    lightCls, m.name,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = darkMethod.invoke(darkInstance)
                        }
                    },
                )
                count++
            }
        }
        XposedBridge.log("$TAG: palette forwarded methods = $count")
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

        // 2) 可用输入源：只确保 PLUGINS。
        //    技能（SKILLS）交回 TRAE 原生判断，不再强行注入。
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
                        for (name in listOf("PLUGINS")) {
                            addSourceByName(cl, merged, name)
                        }
                        param.result = merged
                    }
                },
            )
        }.onFailure { XposedBridge.log("$TAG: rollout hook skipped -> ${it.message}") }

        // 3) 【已移除 isPluginVisible 的 hook】
        //
        // 这个 hook 之前强制返回 true，但它的返回值不只控制插件入口，
        // 还会被 showSkillsBottomSheet 当作第 8 个参数 includePluginSkills 传给
        // SkillsBottomSheet.newInstance：
        //
        //   companion.newInstance(cliId, workspaceFolder, projectId, isIdeSkills,
        //       conversationId, configName, agentType,
        //       chatAttachmentConfig.isPluginVisible(...),   // ← includePluginSkills
        //       analyticsChatMode);
        //
        // 而 SkillsViewModel.loadSkills() 里：
        //   pluginSkillsResolved = !includePluginSkills;
        //   if (includePluginSkills) launch { pluginSkills = ...; publishSkills() }
        //   fetchSkills(false);
        //
        // 也就是说，强制 true 会改变技能列表的发布分支和时序，把本来正常的
        // 技能加载搅乱。这是「技能变少」的直接原因。
        //
        // 插件入口可见性已经由上面的 Feature.PLUGIN 开关保证：
        //   entries$lambda$3 = !isIde && FeatureCapability.isEnabled(PLUGIN)
        //                      && PluginCapability.isSupported(...)
        // Feature 恒为 true 后，PLUGINS 自然可见，不需要再动 isPluginVisible。
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
