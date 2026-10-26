package com.findmyphone.gpstracker.receiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.findmyphone.gpstracker.services.PersistentNotificationService


import kotlin.jvm.java
import kotlin.let

class NotificationDeletedReceiver : BroadcastReceiver() {
    companion object {
    }
    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            context?.let {
                val serviceIntent = Intent(it, PersistentNotificationService::class.java)
                it.startForegroundService(serviceIntent)
            }
        }catch (e: Exception){

        }

    }
}