package com.findmyphone.gpstracker.utils

import android.R.attr.country
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import androidx.core.net.toUri
import cn.thinkingdata.analytics.TDAnalytics
import cn.thinkingdata.analytics.TDConfig

import com.findmyphone.gpstracker.ad.AdvCheckManager
import com.findmyphone.gpstracker.ad.AdvCheckManager.params
import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.base.command.getDetailCurrentTime
import com.findmyphone.gpstracker.bean.AdMapping
import com.findmyphone.gpstracker.bean.IpResponse
import com.findmyphone.gpstracker.bean.LocationInfo
import com.findmyphone.gpstracker.bean.ScheduledTimeBean

import com.findmyphone.gpstracker.config.AppConfig


import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.get
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import com.singular.sdk.Singular
import com.singular.sdk.SingularConfig
import com.singular.sdk.SingularDeviceAttributionHandler


import kotlinx.coroutines.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.apply
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.jvm.java
import kotlin.takeIf
import kotlin.text.contains
import kotlin.text.equals
import kotlin.text.isEmpty
import kotlin.text.isNotBlank
import kotlin.text.isNotEmpty
import kotlin.text.lowercase
import kotlin.text.orEmpty
import kotlin.time.Duration.Companion.seconds
import kotlin.to

object SdkInitUtils {
    private const val TAG = "SdkInit"
    private const val INIT_TIMEOUT_SECONDS = 15L

    val gson = Gson()

    // 初始化状态跟踪
    private val isInitializing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var initializationJob: Job? = null

    // 协程作用域管理
    private val initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 异步初始化所有SDK（主入口）
     */
    fun initSDKAsync(context: MyApplication): Job {
        // 防止重复初始化
        if (isInitializing.getAndSet(true)) {
            return initializationJob ?: Job().apply { complete() }
        }
        initializationJob = initializationScope.launch {
            try {
                initAllSDKsWithPriority(context)
                isInitialized.set(true)
                Log.e(TAG, "SDK initialization completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "SDK initialization failed", e)
                isInitializing.set(false) // 允许重试
            } finally {
                isInitializing.set(false)
            }
        }
        return initializationJob!!
    }

    /**
     * 等待SDK初始化完成（可选）
     */
    suspend fun awaitInitialization() {
        initializationJob?.join()
    }

    /**
     * 检查是否已完成初始化
     */
    fun isInitialized(): Boolean = isInitialized.get()

    /**
     * 按优先级初始化所有SDK
     */
    private suspend fun initAllSDKsWithPriority(context: MyApplication) {
        withTimeout(INIT_TIMEOUT_SECONDS.seconds) {
            // 阶段1: 串行执行的核心初始化（必须按顺序）
            initPhase1CoreTasks(context)
            // 阶段2: 并行执行的SDK初始化（分析类 SDK）
            initPhase2ParallelSDKs(context)
            // 阶段3: 主线程相关的初始化
        }
    }


    /**
     * 阶段1: 核心串行任务（包括 AdMob 和基础配置）
     */
    private suspend fun initPhase1CoreTasks(context: MyApplication) {
        // 1. 记录首次打开
        recordFirstOpenIfNeeded()
        initThinkingAnalyticsSDKAsync()
        // 2. 初始化 Remote Config（快速获取，不等待完整加载）
        initRemoteConfigFast(3600) // 使用较短的缓存时间
        // 3. 初始化 Singular（归因）
        initSingularAsync(AppConfig.Singular_Api_Key, AppConfig.Singular_Secret, context)
    }



    /**
     * 阶段2: 并行SDK初始化（分析类 SDK，不依赖广告）
     */
    private suspend fun initPhase2ParallelSDKs(context: MyApplication) = coroutineScope {
        // 使用结构化并发并行执行
        val analyticsJob = async { initAnalyticsSDKsInParallel(context) }
        analyticsJob.await()
    }


    /**
     * 并行初始化分析SDK
     */
    private suspend fun initAnalyticsSDKsInParallel(context: MyApplication) = coroutineScope {
        val jobs = listOf(
            async { initFirebaseAnalyticsAsync() },
        )
        // 等待所有分析SDK初始化
        jobs.awaitAll()
    }


    /**
     * 快速初始化Remote Config（不等待完整加载）
     */
    private fun initRemoteConfigFast(minimumFetchIntervalInSeconds: Long) {
        initializationScope.launch {
            try {
                val remoteConfig = Firebase.remoteConfig
                val configSettings = remoteConfigSettings {
                    this.minimumFetchIntervalInSeconds = minimumFetchIntervalInSeconds
                }
                remoteConfig.setConfigSettingsAsync(configSettings)
                // 异步获取配置
                launch {
                    try {
                        val fetchResult = remoteConfig.fetchAndActivate().await()
                        if (fetchResult) {
                            update(remoteConfig)
                            Log.i(TAG, "Remote config fetched and activated")
                        } else {
                            Log.w(TAG, "Remote config not activated")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Remote config fetch failed", e)
                    }
                }
                // 配置更新监听器
                remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                    override fun onUpdate(configUpdate: ConfigUpdate) {
                        initializationScope.launch {
                            try {
                                remoteConfig.activate().await()
                                update(remoteConfig)
                                Log.i(TAG, "Remote config updated from server")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to activate updated config", e)
                            }
                        }
                    }

                    override fun onError(error: FirebaseRemoteConfigException) {
                        Log.e(TAG, "Remote config update error", error)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Remote config init error", e)
            }
        }
    }

    /**
     * 异步初始化Singular
     */
    private suspend fun initSingularAsync(key: String, secret: String, context: MyApplication) {
        return withContext(Dispatchers.IO) {
            try {
                val config = SingularConfig(key, secret)
                    .withLogLevel(if (AppConfig.isDebug) 1 else 0)
                    .withSingularDeviceAttribution(createDeviceAttributionHandler())
                Singular.init(context, config)
                Log.i(TAG, "Singular initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Singular init error", e)
            }
        }
    }

    /**
     * 异步初始化Thinking Analytics
     */
    private suspend fun initThinkingAnalyticsSDKAsync() {
        return withContext(Dispatchers.IO) {
            try {
                val config = TDConfig.getInstance(
                    MyApplication.instance,
                    AppConfig.THINKING_APPID,
                    AppConfig.THINKING_URL
                ).apply {
                    setMode(getTDMode())
                }
                TDAnalytics.init(config)
                withContext(Dispatchers.Main.immediate) {
                    TDAnalytics.enableAutoTrack(
                        TDAnalytics.TDAutoTrackEventType.APP_START or
                                TDAnalytics.TDAutoTrackEventType.APP_END or
                                TDAnalytics.TDAutoTrackEventType.APP_INSTALL or
                                TDAnalytics.TDAutoTrackEventType.APP_VIEW_SCREEN or
                                TDAnalytics.TDAutoTrackEventType.APP_CLICK or
                                TDAnalytics.TDAutoTrackEventType.APP_CRASH
                    )
                    TDAnalytics.enableLog(AppConfig.isDebug)
                }


                Log.i(TAG, "Thinking Analytics initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Thinking Analytics init error", e)
            }
        }
    }

    /**
     * 异步初始化Firebase Analytics
     */
    private suspend fun initFirebaseAnalyticsAsync() {
        withContext(Dispatchers.Main.immediate) {
            try {
                FirebaseAnalytics.getInstance(MyApplication.instance).apply {
                    setAnalyticsCollectionEnabled(true)
                }
                // 异步订阅主题，不等待完成
                FirebaseMessaging.getInstance().subscribeToTopic("all")
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.i(TAG, "Subscribed to FCM topic")
                        } else {
                            Log.w(TAG, "Subscribe to topic failed", task.exception)
                        }
                    }
                Log.i(TAG, "Firebase Analytics initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Analytics init error", e)
            }
        }
    }


    /**
     * 记录首次打开
     */
    private suspend fun recordFirstOpenIfNeeded() {
        if (params.isFirstOpen) {
            params.apply {
                installTime = System.currentTimeMillis()
                isFirstOpen = false
                LogUtil.setUser(
                    mapOf("my_first_open_time" to getDetailCurrentTime(System.currentTimeMillis())),
                    "user_setOnce"
                )
            }
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(AppConfig.IPInfoV2Url)
                        .get()
                        .build()
                    val call = okHttpClient.newCall(request)
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                        }
                        override fun onResponse(call: Call, response: Response) {
                            if (response.isSuccessful) {
                                val responseBodyString = response.body?.string()
                                if (responseBodyString != null) {
                                    // 使用 responseBodyString 做你需要的事
                                    val ipResponse =
                                        gson.fromJson(responseBodyString, IpResponse::class.java)
                                    if (ipResponse != null) {
                                        val locationInfo = gson.fromJson(
                                            ipResponse.location_info,
                                            LocationInfo::class.java
                                        )
                                        if (locationInfo != null) {
                                            if (locationInfo.isp?.contains(
                                                    "google",
                                                    ignoreCase = true
                                                ) == true || locationInfo.asn?.contains("15169") == true
                                            ) {
                                                Log.e(TAG, "checkIpInfo: isp为Google")
                                                AdvCheckManager.params.fromNature = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Singular init error", e)
                }
            }
        }
    }


    /**
     * 获取Thinking Analytics模式
     */
    private fun getTDMode(): TDConfig.TDMode {
        return if (AppConfig.isDebug) {
            TDConfig.TDMode.DEBUG
        } else {
            TDConfig.TDMode.NORMAL
        }
    }

    /**
     * 创建设备归因处理器
     */
    private fun createDeviceAttributionHandler(): SingularDeviceAttributionHandler {
        return object : SingularDeviceAttributionHandler {
            override fun onDeviceAttributionInfoReceived(attributionData: Map<String, Any>) {
                try {
                    val promoteParams = createPromoteParams(attributionData)
                    TDAnalytics.setSuperProperties(promoteParams)
                    TDAnalytics.userSet(promoteParams)
                    Log.i(TAG, "Device attribution received")
                } catch (e: JSONException) {
                    Log.e(TAG, "JSON error in device attribution", e)
                }
            }

            private fun createPromoteParams(attributionData: Map<String, Any>): JSONObject {
                return JSONObject().apply {
                    val network = attributionData["network"]?.toString().orEmpty()
                    val campaignId = attributionData["campaign_id"]?.toString().orEmpty()
                    val campaignName = attributionData["campaign_name"]?.toString().orEmpty()
                    val isNatural =
                        network.equals("Organic", ignoreCase = true) || network.isEmpty()
                    Log.e("weiyu","network"+network)
                    AdvCheckManager.params.fromQuantity = isNatural
                    put("fromNature", isNatural)
                    put("network", network)
                    put("is_debug", AppConfig.isDebug)
                    put("campaign_id", campaignId)
                    if (campaignName.isNotEmpty()) {
                        put("campaign_name", campaignName)
                    }
                    put("passthrough", attributionData["passthrough"]?.toString().orEmpty())
                    put("match_type", attributionData["match_type"]?.toString().orEmpty())
                    put("click_timestamp", attributionData["click_timestamp"]?.toString().orEmpty())
                }
            }
        }
    }

    /**
     * 更新配置（保持原有逻辑）
     */
    fun update(remoteConfig: FirebaseRemoteConfig) {
        val ABTestName = remoteConfig.getString("ABTestName")
        val userProperties =  JSONObject()
        userProperties.put("ABTestName", ABTestName)
        TDAnalytics.userSet(userProperties)
        val openreview = remoteConfig.getBoolean("openreview")
        AppPrefsUtils.commitBoolean("openreview", openreview)
        //   var ad_mapping = remoteConfig.getString("ad_mapping")
        var ad_policy_tag = "ad_policy"
        var ad_native_policy_tag = "ad_native_policy"
        var adload_config_tag = "adload_config"
        var notification_config_tag="notification_config"
//        val country = getUserPreferredCountryCode()
//        val brand = Build.BRAND
//        try {
//            var adMapping = gson.fromJson(ad_mapping, AdMapping::class.java)
//            var configs = adMapping.configs
//            for (strategyConfigItem in configs) {
//                if (strategyConfigItem.countrys.contains(country)&&strategyConfigItem.brands.contains(brand)) {
//                    ad_policy_tag = strategyConfigItem.config.ad
//                    ad_native_policy_tag=strategyConfigItem.config.ad_native
//                    adload_config_tag=strategyConfigItem.config.preload
//                    notification_config_tag=strategyConfigItem.config.notification
//                }
//            }
//        } catch (e: Exception) {
//
//        }
        val ad_policy = remoteConfig.getString(ad_policy_tag)
        val ad_policy_nature = remoteConfig.getString("ad_policy_nature")
        val ad_native_policy=remoteConfig.getString(ad_native_policy_tag)

        val ad_native_policy_nature=remoteConfig.getString("ad_native_policy_nature")
        val adload_config = remoteConfig.getString(adload_config_tag)
        val adload_config_nature = remoteConfig.getString("adload_config_nature")
        val notification_config=remoteConfig.getString(notification_config_tag)
        val notification_config_nature=remoteConfig.getString("notification_config_nature")
        AppPrefsUtils.commitBoolean("skip_setlanguage", remoteConfig.getBoolean("skip_setlanguage"))
        AppPrefsUtils.commitBoolean("skip_login", remoteConfig.getBoolean("skip_login"))
        AppPrefsUtils.commitBoolean("skip_guide", remoteConfig.getBoolean("skip_guide"))
        AppPrefsUtils.commitString("nativead_click_to_hideconfig",  remoteConfig.getString("nativead_click_to_hideconfig"))
        NativeAdConfigManager.init()
        AppPrefsUtils.commitString("ad_policy", ad_policy)
        AppPrefsUtils.commitString("ad_policy_nature", ad_policy_nature)
        AppPrefsUtils.commitString("notification_config", notification_config)
        AppPrefsUtils.commitString("notification_config_nature", notification_config_nature)
        AppPrefsUtils.commitString("notification_content", remoteConfig.getString("notification_content"))
        AppPrefsUtils.commitLong("show_main_bottom_native_time",remoteConfig.getLong("show_main_bottom_native_time") )
        AppPrefsUtils.commitString("ad_native_policy", ad_native_policy)
        AppPrefsUtils.commitString("ad_native_policy_nature", ad_native_policy_nature)
        val native_ad_ids = remoteConfig.getString("native_ad_ids")


        var native_refresh_time = remoteConfig.getLong("native_refresh_time")
        AppPrefsUtils.commitLong("native_refresh_time", native_refresh_time)
        AppPrefsUtils.commitString("adload_config", adload_config)
        AppPrefsUtils.commitString("adload_config_nature", adload_config_nature)
        // 推送时间配置
        val time = remoteConfig.getString("push_scheduled_sending_time")
        if (TextUtils.isEmpty(time)) {
            ScheduledPushScheduler.cancelAllWork(MyApplication.instance!!)
        } else {
            try {
                val scheduledTimeBean = gson.fromJson(time, ScheduledTimeBean::class.java)
                AppPrefsUtils.commitInt("scheduled_offset_second", scheduledTimeBean.offset_second)
                ScheduledPushScheduler.applyRemoteTimes(
                    MyApplication.instance,
                    scheduledTimeBean.hour_minute_of_day
                )
                Log.i(TAG, "Scheduled push time configured")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing scheduled time", e)
            }
        }
        // ABTestName：实验名称，用于后续埋点分组。


    }
    /**
     * 获取用户首选国家代码
     */
    fun getUserPreferredCountryCode(): String {
        val defaultLocale = Locale.getDefault()
        return defaultLocale.country.takeIf { it.isNotBlank() }?.lowercase() ?: "us"
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        initializationJob?.cancel()
        initializationScope.cancel()
        isInitialized.set(false)
        isInitializing.set(false)
        initializationJob = null
    }
}
