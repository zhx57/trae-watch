package com.trae.watch.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.trae.watch.TraeWatchApp

/**
 * 兜底轮询：SSE 被系统杀掉后，每 15 分钟拉一次会话状态。
 *
 * 这是手表端的必要成本：单靠前台服务在国产 ROM 上不可靠，
 * 没有这个兜底就会完全失联。
 */
class PollJobService : JobService() {

    companion object {
        private const val JOB_ID = 2001
        private const val TAG = "PollJob"

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            val info = JobInfo.Builder(
                JOB_ID,
                ComponentName(ctx, PollJobService::class.java),
            )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            js.schedule(info)
        }

        fun cancel(ctx: Context) {
            ctx.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val graph = (application as? TraeWatchApp)?.graph ?: return false
        if (!graph.token.hasAccount) return false
        val worker = Thread({
            try {
                val convs = graph.api.localConversations()
                Log.i(TAG, "poll ok, conversations=${convs.size}")
            } catch (e: Exception) {
                Log.w(TAG, "poll failed: ${e.message}")
            } finally {
                jobFinished(params, false)
            }
        }, "poll-job")
        worker.isDaemon = true
        worker.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
