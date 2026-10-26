package com.findmyphone.gpstracker.utils

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * 全局位置上报管理器（单例）
 * 状态完全由 ForegroundLocationReporter.isActive() 决定，无假死风险。
 */
object LocationReporterManager {

    private const val TAG = "LocationReporterMgr"

    @Volatile
    private var reporter: ForegroundLocationReporter? = null

    /**
     * 启动位置上报（已运行则忽略，否则自动恢复）
     */
    @RequiresPermission(
        allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    fun start(context: Context) {
        synchronized(this) {
            if (reporter?.isActive() == true) {
                Log.d(TAG, "位置上报已在运行")
                return
            }
            if (reporter == null) {
                reporter = ForegroundLocationReporter(context.applicationContext)
            }
            reporter?.start()
            Log.d(TAG, "启动完成，当前活跃=${reporter?.isActive()}")
        }
    }

    /**
     * 停止位置上报
     */
    fun stop() {
        synchronized(this) {
            if (reporter?.isActive() != true) {
                Log.d(TAG, "位置上报未运行")
                return
            }
            reporter?.stop()
            Log.d(TAG, "已停止")
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        synchronized(this) {
            stop()
            reporter = null
        }
    }

    /**
     * 查询真实运行状态
     */
    fun isRunning(): Boolean = reporter?.isActive() == true

    /**
     * 动态设置定位频率（供外部调用）
     * @param high true: 高频率（前台/实时跟踪），false: 低频率（后台省电）
     */
    @RequiresPermission(
        allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    fun setHighFrequency(high: Boolean) {
        synchronized(this) {
            reporter?.setHighFrequency(high)
            Log.d(TAG, "频率模式: ${if (high) "高频" else "低频"}")
        }
    }
}