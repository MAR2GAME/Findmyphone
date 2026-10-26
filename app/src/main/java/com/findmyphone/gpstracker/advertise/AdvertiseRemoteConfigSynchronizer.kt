package com.findmyphone.gpstracker.advertise

import android.util.Log
import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.bean.ScheduledTimeBean
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.NativeAdConfigManager
import com.findmyphone.gpstracker.utils.ScheduledPushScheduler
import com.google.gson.Gson
import com.pdffox.adv.AdvertiseSdk

object AdvertiseRemoteConfigSynchronizer {
    private const val TAG = "AdvertiseRemoteConfig"
    private val gson = Gson()

    fun register(application: MyApplication) {
        AdvertiseSdk.getRemoteConfigInitStatus(
            AdvertiseSdk.RemoteConfigInitStatusListener {
                    isInitialized,
                    isSuccessful,
                    error,
                ->
                if (isSuccessful) {
                    runCatching { synchronize(application) }
                        .onFailure { Log.e(TAG, "Remote Config synchronization failed", it) }
                } else {
                    Log.w(
                        TAG,
                        "Remote Config initialization failed: initialized=$isInitialized",
                        error,
                    )
                }
            }
        )
    }

    private fun synchronize(application: MyApplication) {
        listOf(
            "notification_config",
            "notification_config_nature",
            "notification_content",
            "ad_policy",
            "ad_policy_nature",
            "ad_native_policy",
            "ad_native_policy_nature",
            "adload_config",
            "adload_config_nature",
            "native_ad_ids",
            "nativead_click_to_hideconfig",
        ).forEach { key ->
            AppPrefsUtils.commitString(
                key,
                AdvertiseSdk.getRemoteConfigString(key, AppPrefsUtils.getString(key)),
            )
        }
        NativeAdConfigManager.init()
        synchronizeScheduledPush(application)
    }

    private fun synchronizeScheduledPush(application: MyApplication) {
        val value = AdvertiseSdk.getRemoteConfigString("push_scheduled_sending_time")
        if (value.isBlank()) {
            ScheduledPushScheduler.cancelAllWork(application)
            return
        }
        runCatching { gson.fromJson(value, ScheduledTimeBean::class.java) }
            .onSuccess { schedule ->
                AppPrefsUtils.commitInt("scheduled_offset_second", schedule.offset_second)
                ScheduledPushScheduler.applyRemoteTimes(application, schedule.hour_minute_of_day)
            }
            .onFailure { Log.e(TAG, "Invalid push schedule", it) }
    }
}
