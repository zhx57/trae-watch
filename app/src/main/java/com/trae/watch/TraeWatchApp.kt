package com.trae.watch

import android.app.Application
import com.trae.watch.data.auth.AppGraph

class TraeWatchApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // 启动路径只做必要的对象构造，不碰数据库、不发网络请求
        graph = AppGraph(this)
    }
}
