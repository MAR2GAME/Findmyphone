package com.findmyphone.gpstracker.base

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.TypedArray
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.findmyphone.gpstracker.BuildConfig
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AdvCheckManager
import com.findmyphone.gpstracker.advertise.AdvertiseRemoteConfigSynchronizer
import com.findmyphone.gpstracker.advertise.AdvertiseSdkConfigFactory
import com.findmyphone.gpstracker.base.activity.ILandscapeAllowed
import com.findmyphone.gpstracker.bean.ScheduledTimeBean
import com.findmyphone.gpstracker.config.AppConfig.ACTION_CUSTOM_BROADCAST
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LocaleHelper
import com.findmyphone.gpstracker.utils.NativeAdConfigManager
import com.findmyphone.gpstracker.utils.SafeChecker
import com.findmyphone.gpstracker.utils.ScheduledPushScheduler
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.gson.Gson
import com.pdffox.adv.AdvertiseSdk


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class MyApplication: Application(){
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // 使用 AtomicInteger 保证线程安全
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var _instance: MyApplication? = null
        val instance: MyApplication
            get() = _instance ?: throw IllegalStateException("Application not initialized")

        // 全局唯一的 PlacesClient
        lateinit var placesClient: PlacesClient
            private set
    }

    private val activityCount = AtomicInteger(0)
    fun isAppInForeground() = activityCount.get()>0
    override fun onCreate() {
        super.onCreate()
        _instance = this
        recordInstallTimeIfNeeded()
        initializeAdvertiseSdk()
        initializeApplication()
    }



    private fun initializeApplication() {
        getPushConfig()
        initMap()
        registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        applicationScope.launch {
            delay(1000) // 延迟1秒，让应用先启动
            SafeChecker.performAuxiliaryChecks()
        }
    }


    fun getPushConfig(){
        AdvertiseSdk.getRemoteConfigInitStatus(object : AdvertiseSdk.RemoteConfigInitStatusListener{
            override fun onStatus(
                isInitialized: Boolean,
                isSuccessful: Boolean,
                error: Throwable?
            ) {
                if(isSuccessful){
                    AppPrefsUtils.commitString("notification_config", AdvertiseSdk.getRemoteConfigString("notification_config"))
                    AppPrefsUtils.commitString("notification_config_nature", AdvertiseSdk.getRemoteConfigString("notification_config_nature"))
                    AppPrefsUtils.commitString("notification_content" ,  AdvertiseSdk.getRemoteConfigString("notification_content"))
                    AppPrefsUtils.commitString("ad_native_policy",  AdvertiseSdk.getRemoteConfigString("ad_native_policy"))
                    AppPrefsUtils.commitString("ad_native_policy_nature",  AdvertiseSdk.getRemoteConfigString("ad_native_policy_nature"))
                    AppPrefsUtils.commitString("adload_config",  AdvertiseSdk.getRemoteConfigString("adload_config"))
                    AppPrefsUtils.commitString("adload_config_nature",  AdvertiseSdk.getRemoteConfigString("adload_config_nature"))
                    AppPrefsUtils.commitBoolean("openreview",  AdvertiseSdk.getRemoteConfigBoolean("openreview"))
                    AppPrefsUtils.commitString("native_ad_ids", AdvertiseSdk.getRemoteConfigString("native_ad_ids"))
                    AppPrefsUtils.commitString("nativead_click_to_hideconfig",  AdvertiseSdk.getRemoteConfigString("nativead_click_to_hideconfig"))
                    NativeAdConfigManager.init()
                    val time =AdvertiseSdk.getRemoteConfigString("push_scheduled_sending_time")
                    if (TextUtils.isEmpty(time)) {
                        ScheduledPushScheduler.cancelAllWork(MyApplication.instance!!)
                    } else {
                        try {
                            val scheduledTimeBean = Gson().fromJson(time, ScheduledTimeBean::class.java)
                            AppPrefsUtils.commitInt("scheduled_offset_second", scheduledTimeBean.offset_second)
                            ScheduledPushScheduler.applyRemoteTimes(
                                MyApplication.instance,
                                scheduledTimeBean.hour_minute_of_day
                            )
                        } catch (e: Exception) {
                        }
                    }

                }
            }

        })

    }

    private fun recordInstallTimeIfNeeded() {
        val params = AdvCheckManager.params
        if (!params.isFirstOpen) return
        params.installTime = System.currentTimeMillis()
        params.isFirstOpen = false
    }

    private fun initializeAdvertiseSdk() {
        runCatching {
            runBlocking {
                AdvertiseSdk.init(
                    context = this@MyApplication,
                    isTest = BuildConfig.DEBUG,
                    sdkConfig = AdvertiseSdkConfigFactory.create(this@MyApplication),
                )
            }
        }.onFailure { throwable ->
            Log.e("MyApplication", "AdvertiseSdk initialization failed", throwable)
        }
    }


    // ========== 生命周期回调（时机改为 onResume，更可靠） ==========
    private val activityLifecycleCallbacks = object : ActivityLifecycleCallbacks {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {
            activityCount.incrementAndGet()
        }

        override fun onActivityResumed(activity: Activity) {
            lockPortraitIfNeeded(activity)
        }

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            if (activityCount.decrementAndGet() == 0) {
                val intent = Intent(ACTION_CUSTOM_BROADCAST)
                this@MyApplication.sendBroadcast(intent)
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}

        // ---------- 竖屏锁定逻辑 ----------
        private fun lockPortraitIfNeeded(activity: Activity) {
            // 1. 透明/悬浮 Activity → 跳过（防止异常）
            if (isTranslucentOrFloating(activity)) {
                return
            }

            // 2. 实现了豁免接口的页面 → 跳过
            if (activity is ILandscapeAllowed) {
                return
            }

            // 3. 已经竖屏 → 跳过（避免重复设置导致闪烁/重建）
            if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                return
            }
            // 4. 安全设置，异常兜底永不崩溃
            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } catch (e: Exception) {
            }
        }

        @SuppressLint("ResourceType")
        private fun isTranslucentOrFloating(activity: Activity): Boolean {
            val theme = activity.theme ?: return false
            val attrs = intArrayOf(
                android.R.attr.windowIsTranslucent,
                android.R.attr.windowIsFloating
            )
            val typedArray: TypedArray = theme.obtainStyledAttributes(attrs)
            val translucent = typedArray.getBoolean(0, false)
            val floating = typedArray.getBoolean(1, false)
            typedArray.recycle()
            return translucent || floating
        }
    }

    fun  initMap(){
        val apiKey = BuildConfig.MAPS_API_KEY
        if (apiKey.isNullOrEmpty() || apiKey == "YOUR_API_KEY") {
            Toast.makeText(this, R.string.error_maps_api_key_missing, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }
        // 创建唯一的 PlacesClient
        placesClient = Places.createClient(this)
    }
    override fun attachBaseContext(base: Context) {
        // 为 Application 设置语言，返回已应用语言配置的 Context
        val localizedContext = LocaleHelper.setLocaleForApplication(base)
        super.attachBaseContext(localizedContext)
//        applicationScope.launch {
//            val isChek= SafeChecker.performEssentialCheck(this@MyApplication)
//            if(!isChek){
//                withContext(Dispatchers.Main){
//                    SafeChecker.forceExit()
//                }
//            }
//        }
    }



    override fun onTerminate() {
        applicationScope.cancel()
        unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
        _instance = null
        super.onTerminate()
    }


}
