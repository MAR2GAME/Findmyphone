package com.findmyphone.gpstracker.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.apply
import kotlin.collections.forEach
import kotlin.jvm.java

object NotificationChannelManager {
    const val PERSISTENT_CHANNEL_ID = "find_persistent_notification_channel"
    const val PERSISTENT_CHANNEL_NAME = "persistent_notification_notice"
    const val NORMAL_CHANNEL_ID = "find_normal_notification_channel"
    const val NORMAL_CHANNEL_NAME = "normal_notification_notice"
    fun createAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            listOf(createPersistentChannel(), createNormalChannel()).forEach { channel ->
                notificationManager.createNotificationChannel(channel)
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPersistentChannel(): NotificationChannel {
        return NotificationChannel(
            PERSISTENT_CHANNEL_ID,
            PERSISTENT_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            enableLights(false)
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNormalChannel(): NotificationChannel {
        return NotificationChannel(
            NORMAL_CHANNEL_ID,
            NORMAL_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 100, 200, 300)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
    }
}