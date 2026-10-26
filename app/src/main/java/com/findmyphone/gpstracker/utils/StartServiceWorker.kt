package com.findmyphone.gpstracker.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.findmyphone.gpstracker.services.PersistentNotificationService

class StartServiceWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    companion object {
        private const val TAG = "StartServiceWorker"
    }
    override fun doWork(): Result {
        return try {
            val ctx = applicationContext
            // 再次检查通知权限（可能在延迟期间被用户撤销）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Notification permission revoked, cannot start foreground service")
                    return Result.failure()
                }
            }

            // 如果服务已在运行，避免重复启动
            if (PersistentNotificationService.isRunning) {
                Log.d(TAG, "Service already running, worker skipped")
                return Result.success()
            }

            val serviceIntent = Intent(ctx, PersistentNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(serviceIntent)
            } else {
                ctx.startService(serviceIntent)
            }
            Log.i(TAG, "Service start requested successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed to start service", e)
            // 可重试的异常（如系统暂时繁忙），WorkManager 会根据退避策略重试
            Result.retry()
        }
    }
}