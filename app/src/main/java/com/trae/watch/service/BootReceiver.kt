package com.trae.watch.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trae.watch.TraeWatchApp

/** 开机自启 + 应用更新后重启服务，避免用户手动打开 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val app = context.applicationContext as? TraeWatchApp ?: return
                if (app.graph.token.hasAccount) {
                    StreamService.start(context)
                    PollJobService.schedule(context)
                }
            }
        }
    }
}
