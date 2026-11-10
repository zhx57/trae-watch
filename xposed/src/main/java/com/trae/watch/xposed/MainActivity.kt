package com.trae.watch.xposed

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 模块说明页。
 *
 * Xposed 模块本身没有实际功能界面，这里只用来：
 *  1. 让用户能在桌面找到并确认模块已安装
 *  2. 说明作用范围和验证方法（框架是否启用只能由框架决定）
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
        })

        root.addView(TextView(this).apply {
            text = """
                作用：让 TRAE 的自定义模型/第三方模型也能选择思考强度。

                原理：TRAE 只在服务端下发 reasoning_effort_config 时显示思考强度入口。
                自定义模型没有这个配置，所以入口被隐藏。本模块通过 hook
                RemoteModelItem.getHasThinkingCapability 与 getReasoningEffortConfig，
                为缺失配置的模型补上 high / light 两档。

                生效条件：
                  1. 设备已 root 并安装 LSPosed / EdXposed
                  2. 在框架的模块列表里启用本模块
                  3. 作用域勾选 com.bytedance.trae.cn
                  4. 强制停止 TRAE 后重新打开

                验证：打开 TRAE → 模型选择 → 选一个自定义模型 →
                模型能力面板里应出现「思考强度」一行。
            """.trimIndent()
            textSize = 13f
            setPadding(0, pad, 0, 0)
            gravity = Gravity.START
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
