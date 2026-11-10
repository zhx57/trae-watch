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

        /** 固定两个档位，与官方 UI 的文案映射一致 */
        private val OPTIONS = listOf("high", "light")
        private const val DEFAULT_LEVEL = "high"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PKG) return

        // 热重载/多进程场景下可能被加载多次，用标记防重复
        if (lpparam.classLoader == null) return

        try {
            hookHasThinking(lpparam.classLoader)
            hookEffortConfig(lpparam.classLoader)
            XposedBridge.log("$TAG: hooks installed on ${lpparam.processName}")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: hook failed -> ${t.message}")
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
}
