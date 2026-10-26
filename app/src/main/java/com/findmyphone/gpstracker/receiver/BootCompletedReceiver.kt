package com.findmyphone.gpstracker.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.findmyphone.gpstracker.services.PersistentNotificationService
import com.findmyphone.gpstracker.utils.StartServiceWorker
import kotlin.random.Random
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 监听开机完成和自定义 FCM 广播，负责启动 PersistentNotificationService
 * 使用 WorkManager 规避 Android 8.0+ 的后台启动限制，并包含权限检查和防重复逻辑
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
        private const val ACTION_CUSTOM = "FCM_NOTIFICATION"
        private const val MIN_INTERVAL_MS = 30_000L                     // 最小调度间隔 30 秒
        private const val MIN_DELAY_SECONDS = 5L
        private const val MAX_DELAY_SECONDS = 10L
        private const val IMMEDIATE_DELAY_SECONDS = 2L
        private const val UNIQUE_WORK_NAME = "start_service_work"
        private const val WORK_TAG = "start_service_worker"

        // 使用 elapsedRealtime 避免系统时间修改影响防重复逻辑
        private val lastScheduleTime = AtomicLong(0L)

        /**
         * 判断是否允许调度
         * 条件：距上次调度 >= 最小间隔 且 服务未运行
         */
        @Synchronized
        fun shouldSchedule(): Boolean {
            val now = SystemClock.elapsedRealtime()
            val diff = now - lastScheduleTime.get()
            return diff >= MIN_INTERVAL_MS && !PersistentNotificationService.isRunning
        }

        /**
         * 更新上次调度时间
         */
        @Synchronized
        fun updateScheduleTime() {
            lastScheduleTime.set(SystemClock.elapsedRealtime())
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
            ACTION_CUSTOM -> handleCustomAction(context)
            else -> Unit // 忽略未知 action
        }
    }

    private fun handleBootCompleted(context: Context) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Boot completed but missing POST_NOTIFICATIONS permission")
            return
        }
        scheduleServiceStart(context, delay = true)
    }

    private fun handleCustomAction(context: Context) {
        if (!shouldSchedule()) {
            Log.d(TAG, "Skip scheduling (rate limit or service already running)")
            return
        }
        updateScheduleTime()
        scheduleServiceStart(context, delay = false)
    }

    private fun scheduleServiceStart(context: Context, delay: Boolean) {
        // 再次检查权限和服务状态（防止并发变更）
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Missing notification permission, abort scheduling")
            return
        }
        if (PersistentNotificationService.isRunning) {
            Log.d(TAG, "Service already running, skip scheduling")
            return
        }

        val delaySeconds = if (delay) {
            Random.nextLong(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS + 1)
        } else {
            IMMEDIATE_DELAY_SECONDS
        }

        val workRequest = OneTimeWorkRequestBuilder<StartServiceWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()

        // 使用唯一工作，避免堆积；保留已存在的未执行工作（防止过于频繁）
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        Log.d(TAG, "Scheduled service start with delay ${delaySeconds}s")
    }

    /**
     * 检查通知权限（Android 13+ 需要 POST_NOTIFICATIONS 运行时权限）
     */
    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


}