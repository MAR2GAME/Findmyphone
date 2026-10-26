package com.findmyphone.gpstracker.utils

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.findmyphone.gpstracker.base.MyApplication


import kotlin.also

class ScheduledNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ScheduledNotificationWorker"
        private const val ACTION_SCHEDULED_PUSH = "SCHEDULED_PUSH"
        private const val PREF_IS_SEND = "is_send"
    }
    override suspend fun doWork(): Result {
        return try {
            val timeStr = inputData.getString(ScheduledPushScheduler.KEY_TIME)
                ?: return Result.success().also {
                    Log.w(TAG, "No time string provided in input data")
                }
            Log.d(TAG, "开始发送通知")
            val appInstance = MyApplication.instance
            if (appInstance != null) {
                handleNotification(appInstance, timeStr)
            } else {
                Log.e(TAG, "MyApp instance is null, cannot send notification")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process scheduled notification", e)
            Result.failure()
        }
    }

    private suspend fun handleNotification(appInstance: MyApplication, timeStr: String) {
        val shouldSendNotification = when {
            appInstance.isAppInForeground() -> {
                AppPrefsUtils.getBoolean(PREF_IS_SEND, false) // 默认值为true
            }
            else -> true // 应用不在前台时总是发送
        }
        if (shouldSendNotification) {
            sendScheduledPushBroadcast(appInstance)
        } else {
            Log.d(TAG, "应用在前台且配置为不发送通知，跳过")
        }
        // 始终调度下一次通知
        ScheduledPushScheduler.scheduleNextDay(applicationContext, timeStr)
    }
    private fun sendScheduledPushBroadcast(appContext: Context) {
        try {
            val intent = Intent(ACTION_SCHEDULED_PUSH)
            intent.setPackage("com.findmyphone.gpstracker")
            appContext.sendBroadcast(intent)
            Log.d(TAG, "定时推送广播已发送")
        } catch (e: Exception) {
            Log.e(TAG, "发送广播失败", e)
        }
    }
}