package com.trae.watch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.trae.watch.MainActivity
import com.trae.watch.R
import com.trae.watch.TraeWatchApp

/**
 * 常驻前台服务：维持 SSE 长连接。
 *
 * 在国产 ROM 上被杀的应对：
 *  - 前台服务 + 独立进程（AndroidManifest 里 process=":stream"）
 *  - 启动时引导用户加入电池优化白名单
 *  - 被杀了由 PollJobService 每 15 分钟兜底
 */
class StreamService : Service() {

    companion object {
        const val CHANNEL_ID = "trae_stream"
        const val NOTIF_ID = 1001
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"

        fun start(ctx: Context) {
            val i = Intent(ctx, StreamService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, StreamService::class.java).setAction(ACTION_STOP))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification("正在连接…"))
        (application as? TraeWatchApp)?.graph?.let { g ->
            // 实际 SSE 订阅由 ChatViewModel 按当前会话建立；
            // 这里只负责保活与状态通知，避免多会话并发拉流
            if (!g.token.hasAccount) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_trae)
            .setContentTitle("Trae")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }
}
