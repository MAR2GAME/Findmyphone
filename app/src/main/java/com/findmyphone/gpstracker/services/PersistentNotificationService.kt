package com.findmyphone.gpstracker.services

import android.Manifest
import android.R.attr.tag
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AdvCheckManager

import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.base.command.hasLocationPermission
import com.findmyphone.gpstracker.bean.LanguagesBean
import com.findmyphone.gpstracker.bean.PushInfoBean
import com.findmyphone.gpstracker.bean.PushMessageBean
import com.findmyphone.gpstracker.bean.ScreenOnFilter
import com.findmyphone.gpstracker.bean.TemporaryNotificationBean
import com.findmyphone.gpstracker.bean.Trigger
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.receiver.NotificationDeletedReceiver
import com.findmyphone.gpstracker.ui.SplashActivity
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.CircularIterator
import com.findmyphone.gpstracker.utils.LocationReporterManager
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.utils.NotificationChannelManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ironsource.adqualitysdk.sdk.i.it
import com.pdffox.adv.AdvertiseSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.compareTo
import kotlin.inc
import kotlin.jvm.java
import kotlin.text.compareTo
import kotlin.times

class PersistentNotificationService : Service() {

    companion object {
        val isRunning: Boolean
            get() = _isRunning.get()
        private val _isRunning = AtomicBoolean(false)
        const val NOTIFICATION_ID = 15
        private const val WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L // 10分钟
        const val ACTION_START_MESSAGE = "ACTION_START_MESSAGE"
        const val ACTION_START_BG_REPORTING = "ACTION_START_BG_REPORTING"
        const val ACTION_STOP_BG_REPORTING = "ACTION_STOP_BG_REPORTING"
        private const val KEY_REPORTING_ENABLED = "KEY_REPORTING_ENABLED"
        const val SCHEDULED_PUSH    = "SCHEDULED_PUSH"
    }

    private lateinit var wakeLock: PowerManager.WakeLock

    private val reporterReceiver = createReporterReceiver()
    private lateinit var notificationManager: NotificationManager
    private val messageReceiver = createMessageReceiver()

    private var tempNotificationIds = mutableListOf<Int>()
    lateinit var iterator: CircularIterator<Int>

    private lateinit var audioManager: AudioManager
    private var volumeObserver: ContentObserver? = null
    private var lastVolumeSnapshot: Map<Int, Int> = emptyMap()
    private var lastVolumeCallbackTime = 0L

    private var pushMessageBeans = mutableListOf<PushMessageBean>()
    private var logParams = mutableMapOf<String, Any>()
    // 时间戳存储
    private var sendPowerTime: Long by AppPrefsUtils.PreferenceDelegate(
        "sendPowerConnectedTime",
        0L
    )
    private var sendBackgroundTime: Long by AppPrefsUtils.PreferenceDelegate(
        "sendBackgroundTime",
        0L
    )
    private var sendUserPresentTime: Long by AppPrefsUtils.PreferenceDelegate(
        "sendUserPresentTime",
        0L
    )
    private var sendPackageAddTime: Long by AppPrefsUtils.PreferenceDelegate(
        "sendPackageAddTime",
        0L
    )
    private var sendPackageRemoveTime: Long by AppPrefsUtils.PreferenceDelegate(
        "sendPackageRemoveTime",
        0L
    )
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val eventDispatcher = Dispatchers.Default.limitedParallelism(1)

    // 广播接收器
    private val screenReceiver = createScreenReceiver()
    private val installReceiver = createInstallReceiver()
    private val batteryReceiver = createBatteryReceiver()

    private var pushInfoConfig: PushInfoBean? = null
    var pushInfoMap: Map<Int, Trigger> = mapOf()
    var screenOnFilters: MutableList<ScreenOnFilter> = mutableListOf()
    val languageCode = Locale.getDefault().language


    private fun createScreenReceiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    launchNotificationEvent { handleScreenOn() }
                }
                Intent.ACTION_USER_PRESENT -> {
                    launchNotificationEvent { handleUserPresent() }
                }
                AppConfig.ACTION_CUSTOM_BROADCAST -> launchNotificationEvent { handleBackHomePresent() }
                SCHEDULED_PUSH -> launchNotificationEvent { handleSchedulePresent() }
            }
        }
    }
    private fun createInstallReceiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED -> launchNotificationEvent { handlePackageAdded() }
                Intent.ACTION_PACKAGE_REMOVED -> {
                    launchNotificationEvent { handlePackageRemoved() }
                }

            }
        }
    }
    private fun createBatteryReceiver() = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> launchNotificationEvent { handlePowerConnected() }
                Intent.ACTION_POWER_DISCONNECTED -> launchNotificationEvent { handlePowerDisconnected() }
            }
        }
    }

    private fun launchNotificationEvent(block: suspend () -> Unit) {
        serviceScope.launch(eventDispatcher) {
            block()
        }
    }

    private fun initVolumeKeyListener() {
        if (volumeObserver != null) return
        audioManager = getSystemService(AudioManager::class.java)
        lastVolumeSnapshot = currentVolumeSnapshot()
        volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                handleVolumeChanged()
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            volumeObserver!!
        )
    }

    private fun currentVolumeSnapshot(): Map<Int, Int> {
        return listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_VOICE_CALL
        ).associateWith { stream ->
            runCatching { audioManager.getStreamVolume(stream) }.getOrDefault(-1)
        }
    }

    private fun handleVolumeChanged() {
        if (!::audioManager.isInitialized) return
        val now = System.currentTimeMillis()
        if (now - lastVolumeCallbackTime < 250L) return
        val oldSnapshot = lastVolumeSnapshot
        val newSnapshot = currentVolumeSnapshot()
        newSnapshot.entries.firstOrNull { (stream, volume) ->
            oldSnapshot[stream] != null && oldSnapshot[stream] != volume
        } ?: run {
            lastVolumeSnapshot = newSnapshot
            return
        }
        lastVolumeSnapshot = newSnapshot
        lastVolumeCallbackTime = now
        launchNotificationEvent {
            handleNotificationEvent(
                action = Intent.ACTION_PACKAGE_ADDED,
                scene = "app_installed",
            )
        }
    }



    private fun handleUserPresent() {
        handleNotificationEvent(
            action = Intent.ACTION_USER_PRESENT,
            scene = "phone_unlock",
        )
    }

    private fun handleSchedulePresent() {
        handleNotificationEvent(
            action = SCHEDULED_PUSH,
            scene = "schedule",
        )
    }

    private fun handleBackHomePresent() {
        handleNotificationEvent(
            action = AppConfig.ACTION_CUSTOM_BROADCAST,
            scene = "background",
        )
    }

    private fun handlePackageAdded() {
        handleNotificationEvent(
            action = Intent.ACTION_PACKAGE_ADDED,
            scene = "app_installed",
        )
    }

    private fun handlePackageRemoved() {
        handleNotificationEvent(
            action = Intent.ACTION_PACKAGE_REMOVED,
            scene = "app_uninstalled",
        )
    }

    private fun handlePowerConnected() {
        handleNotificationEvent(
            action = Intent.ACTION_POWER_CONNECTED,
            scene = "charging_start",
        )
    }

    private fun handlePowerDisconnected() {
        handleNotificationEvent(
            action = Intent.ACTION_POWER_DISCONNECTED,
            scene = "charging_end",
        )
    }


    private fun handleNotificationEvent(
        action: String,
        scene: String,
    ) {
        try {
            initPushConfig {
                val currentTime = System.currentTimeMillis()
                var canSend = canSendTemporaryNotification(currentTime, action)
                if (canSend) {
                    logParams.clear()
                    logParams["scene"] = scene
                    LogUtil.log("notification_app_shown", logParams)
                    AppPrefsUtils.commitInt("push_count", AppPrefsUtils.getInt("push_count",0)+1)
                    var changePushMessageBeans = getChangePushMessageBeans()
                    serviceScope.launch(eventDispatcher) {
                        for (i in 0 until changePushMessageBeans.size) {
                            var temporaryNotificationBean = getMessage(changePushMessageBeans[i])
                            if (i > 0) {
                                delay(3500L)
                            }
                            if (!AppPrefsUtils.getBoolean("in_splash",false)) {
                                sendTemporaryNotification(
                                    iconRes = temporaryNotificationBean.iconRes,
                                    title = temporaryNotificationBean.title,
                                    message = temporaryNotificationBean.content,
                                    button = temporaryNotificationBean.button,
                                    layoutId = temporaryNotificationBean.layoutId,
                                    bigLayoutId = temporaryNotificationBean.bigLayoutId,
                                    headsUpLayoutId = temporaryNotificationBean.headsUpLayoutId,
                                    intent = temporaryNotificationBean.intent!!,
                                )
                            }

                        }
                    }
                }
            }
        } catch (e: Exception) {

        }
    }


    private fun canSendTemporaryNotification(currentTime: Long, action: String): Boolean {
        if(!AppPrefsUtils.getBoolean("canLoadAd",true)|| MyApplication.instance.isAppInForeground() ){
            return false
        }
        if(pushInfoConfig==null){
            return false
        }
        if (AppPrefsUtils.getBoolean("in_splash",false)) {
            return false
        }
        val windowStartTime= AppPrefsUtils.getLong("windowStartTime",0)
        if (windowStartTime == 0L || currentTime - windowStartTime >= 24 * 60 * 60 * 1000L) {
            AppPrefsUtils.commitInt("push_count",0)
            AppPrefsUtils.commitLong("windowStartTime",currentTime)
        }
        var count=AppPrefsUtils.getInt("push_count",0)
        if (count < pushInfoConfig!!.max24H) {
            when (action) {
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    if (pushInfoMap.contains(3)) {
                        if (AdvCheckManager.params.installTime + pushInfoMap[3]!!.offset_second * 1000 > currentTime) {
                            return false
                        }
                        if (currentTime - sendPowerTime < pushInfoMap[3]!!.interval_second * 1000) {
                            return false
                        }
                        sendPowerTime = currentTime
                        return true
                    } else {
                        return false
                    }
                }

                AppConfig.ACTION_CUSTOM_BROADCAST -> {
                    if (pushInfoMap.contains(2)) {
                        if (AdvCheckManager.params.installTime + pushInfoMap[2]!!.offset_second * 1000 > currentTime) {
                            return false
                        }
                        if (currentTime - sendBackgroundTime < pushInfoMap[2]!!.interval_second * 1000) {
                            return false
                        }
                        sendBackgroundTime = currentTime
                        return true
                    } else {
                        return false
                    }
                }

                Intent.ACTION_USER_PRESENT -> {

                    if (pushInfoMap.contains(1)) {
                        if (AdvCheckManager.params.installTime + pushInfoMap[1]!!.offset_second * 1000 > currentTime) {
                            return false
                        }
                        if (currentTime - sendUserPresentTime < pushInfoMap[1]!!.interval_second * 1000) {
                            return false
                        }
                        sendUserPresentTime = currentTime
                        return true
                    } else {
                        return false
                    }
                }

                Intent.ACTION_PACKAGE_ADDED -> {
                    if (pushInfoMap.contains(4)) {
                        if (AdvCheckManager.params.installTime + pushInfoMap[4]!!.offset_second * 1000 > currentTime) {
                            return false
                        }
                        if (currentTime - sendPackageAddTime < pushInfoMap[4]!!.interval_second * 1000) {
                            return false
                        }
                        sendPackageAddTime = currentTime
                        return true
                    } else {
                        return false
                    }
                }

                Intent.ACTION_PACKAGE_REMOVED -> {
                    if (pushInfoMap.contains(5)) {
                        if (AdvCheckManager.params.installTime + pushInfoMap[5]!!.offset_second * 1000 > currentTime) {
                            return false
                        }
                        if (currentTime - sendPackageRemoveTime < pushInfoMap[5]!!.interval_second * 1000) {
                            return false
                        }
                        sendPackageRemoveTime = currentTime
                        return true
                    } else {
                        return false
                    }
                }

                SCHEDULED_PUSH -> {
                    return AdvCheckManager.params.installTime + AppPrefsUtils.getInt("scheduled_offset_second",0) * 1000 < currentTime
                }
            }
        } else {
            return false
        }
        return false
    }



    private fun handleScreenOn() {
        if(!AppPrefsUtils.getBoolean("canLoadAd",true)|| MyApplication.instance.isAppInForeground()){
            return
        }

        initPushConfig {
            if(pushInfoConfig==null){
                return@initPushConfig
            }
            if (screenOnFilters.size > 0) {
                for (i in 0 until screenOnFilters.size) {
                    val currentTime = System.currentTimeMillis()
                    var windowStartTime= AppPrefsUtils.getLong("windowStartTime",0)
                    if (windowStartTime == 0L || currentTime - windowStartTime >= 24 * 60 * 60 * 1000L) {
                        AppPrefsUtils.commitInt("push_count",0)
                        AppPrefsUtils.commitLong("windowStartTime",currentTime)
                    }
                    var count=AppPrefsUtils.getInt("push_count",0)
                    if (count< pushInfoConfig!!.max24H) {
                        if (AdvCheckManager.params.installTime + screenOnFilters[i].offset_second * 1000 > currentTime) {
                            continue
                        }
                        var sendTime = AppPrefsUtils.getLong("screenOn_" + i, 0)
                        if (currentTime - sendTime < screenOnFilters[i].interval_second * 1000) {
                            continue
                        }
                        AppPrefsUtils.commitLong("screenOn_" + i, currentTime)
                        logParams.clear()
                        logParams["scene"] = "screenOn"
                        LogUtil.log("notification_app_shown", logParams)
                        var changePushMessageBeans = getChangePushMessageBeans()
                        serviceScope.launch(eventDispatcher) {
                            delay(screenOnFilters[i].screen_on_second * 1000L)
                            count++
                            AppPrefsUtils.commitInt("push_count",count)
                            for (i in 0 until changePushMessageBeans.size) {
                                var temporaryNotificationBean =
                                    getMessage(changePushMessageBeans[i])
                                if (i > 0) {
                                    delay(3500L)
                                }
                                if(!MyApplication.instance.isAppInForeground()&&!AppPrefsUtils.getBoolean("in_splash",false)){
                                    sendTemporaryNotification(
                                        iconRes = temporaryNotificationBean.iconRes,
                                        title = temporaryNotificationBean.title,
                                        message = temporaryNotificationBean.content,
                                        button = temporaryNotificationBean.button,
                                        layoutId = temporaryNotificationBean.layoutId,
                                        bigLayoutId = temporaryNotificationBean.bigLayoutId,
                                        headsUpLayoutId = temporaryNotificationBean.headsUpLayoutId,
                                        intent = temporaryNotificationBean.intent!!,
                                    )
                                }
                            }
                        }
                    } else {
                        continue
                    }
                }
            }
        }
    }


    private fun getMessage(
        pushMessageBean: PushMessageBean,
    ): TemporaryNotificationBean {

        var temporaryNotificationBean = TemporaryNotificationBean(
            pushMessageBean.Title,
            pushMessageBean.Content,
            pushMessageBean.Button,
            0,
            0,
            0,
            0,
            null
        )
        if (!TextUtils.isEmpty(pushMessageBean.Languages)) {
            val languagesBean =
                gson.fromJson(pushMessageBean.Languages, LanguagesBean::class.java)
            var filter = languagesBean.keys.filter { it.language == languageCode }
            if (filter.size > 0) {
                temporaryNotificationBean.content = filter[0].content
                temporaryNotificationBean.button = filter[0].button
                temporaryNotificationBean.title = filter[0].title
            }
        }
        temporaryNotificationBean.layoutId = R.layout.temp_notification
        temporaryNotificationBean.bigLayoutId = R.layout.temp_notification_big
        temporaryNotificationBean.headsUpLayoutId = R.layout.headsup_normal
        temporaryNotificationBean.intent = getIntent(pushMessageBean.Route)
        when (pushMessageBean.Route) {
            "/main" -> {
                temporaryNotificationBean.iconRes = R.mipmap.ic_push_1
            }
            "/nearbyPlaces" -> {
                temporaryNotificationBean.iconRes = R.mipmap.ic_push_3
            }
            "/phoneNumber" -> {
                temporaryNotificationBean.iconRes = R.mipmap.ic_push_2
            }
            "/setupZone" -> {
                temporaryNotificationBean.iconRes = R.mipmap.ic_push_6
            }
            "/friendList" -> {
                temporaryNotificationBean.iconRes = R.mipmap.ic_push_5
            }
            "/zoneAlert" -> {
                temporaryNotificationBean.iconRes = R.mipmap.ic_push_4
            }
        }
        return temporaryNotificationBean
    }

    private fun getChangePushMessageBeans(): MutableList<PushMessageBean> {
        var indexs = pushMessageBeans.randomIndices(pushInfoConfig!!.each_trigger_sent)
        var changePushMessageBeans = mutableListOf<PushMessageBean>()
        for (index in indexs) {
            changePushMessageBeans.add(pushMessageBeans[index])
        }
        return changePushMessageBeans
    }
    fun <T> Collection<T>.randomIndices(n: Int): Set<Int> {
        require(n in 0..size) { "n must be between 0 and ${size}" }
        return when {
            n == 0 -> emptySet()
            n ==size -> indices.toSet()
            n * 2 <= size -> {
                // 对于少量抽样，使用while循环
                val result = mutableSetOf<Int>()
                while (result.size < n) {
                    result.add((0 until size).random())
                }
                result
            }
            else -> {
                // 对于大量抽样，使用shuffled
                indices.shuffled().take(n).toSet()
            }
        }
    }




    private fun initPushConfig(onNext: () -> Unit) {
        ensurePushConfigInitialized()
        ensureNotificationConfigInitialized()
        onNext()
    }

    private fun ensurePushConfigInitialized() {
        val currentConfig = if(AdvertiseSdk.isNature){
            AppPrefsUtils.getString("notification_config_nature")
        }else{
            AppPrefsUtils.getString("notification_config")
        }
        val oldConfig = AppPrefsUtils.getString("old_notification_config")
        // 只在配置变化或为空时重新初始化
        if (pushInfoConfig == null || currentConfig != oldConfig) {
            synchronized(this) {
                // 双重检查锁定
                if (pushInfoConfig == null || currentConfig != oldConfig) {
                    AppPrefsUtils.commitString("old_notification_config", currentConfig)
                    initializePushConfig(currentConfig)
                    processPushConfigFilters()
                }
            }
        }
    }
    private fun initializePushConfig(currentConfig:String) {
        if (!TextUtils.isEmpty(currentConfig)){
            pushInfoConfig= Gson().fromJson(currentConfig, PushInfoBean::class.java)
        }
    }

    private fun processPushConfigFilters() {
        pushInfoConfig?.triggers?.let { triggers ->
            if (triggers.isNotEmpty()) {
                pushInfoMap = triggers.associateBy { it.id }
                // 处理屏幕开启过滤器
                pushInfoMap[6]?.filter?.let { filterList ->
                    screenOnFilters.clear()
                    screenOnFilters.addAll(filterList)
                }
            }
        }
    }

    private fun ensureNotificationConfigInitialized() {
        initializeTempNotificationIds()
        ensureNotificationConfigUpdated()
    }

    private fun ensureNotificationConfigUpdated() {
        if (pushMessageBeans.isNullOrEmpty()) {
            pushMessageBeans = mutableListOf<PushMessageBean>()
        }
        val currentConfig = AppPrefsUtils.getString("notification_content")
        val oldConfig = AppPrefsUtils.getString("old_notification_content")
        if (pushMessageBeans.isEmpty() || currentConfig != oldConfig) {
            AppPrefsUtils.commitString("old_notification_content", currentConfig)
            loadNotificationConfig()
        }
    }
    val gson = Gson()
    private fun loadNotificationConfig() {
        pushMessageBeans?.clear()
        val type = object : TypeToken<List<PushMessageBean>>() {}.type
        val configJson = AppPrefsUtils.getString("notification_content")
        if (!TextUtils.isEmpty(configJson)) {
            val notificationList =  gson.fromJson<List<PushMessageBean>>(configJson, type)
            pushMessageBeans?.addAll(notificationList)
        }
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate() {
        super.onCreate()
        setupNotificationChannels()
        startForegroundService()
        initPushConfig {}
        _isRunning.set(true)   // ✅ 修改私有变量
        // 根据存储的标记，确保全局上报管理器处于正确状态
        if (AppPrefsUtils.getBoolean(KEY_REPORTING_ENABLED, false) && hasLocationPermission(true)) {
            // 如果标记为 true，确保上报已启动（权限已在外部保证）
            if (!LocationReporterManager.isRunning()) {
                LocationReporterManager.start(applicationContext)
            }
        }
        registerReceivers()
        acquireWakeLock()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerReceivers() {
        val reporterFilter = IntentFilter().apply {
            addAction(ACTION_START_BG_REPORTING)
            addAction(ACTION_STOP_BG_REPORTING)
        }
        val messageFilter = IntentFilter().apply {
            addAction(ACTION_START_MESSAGE)
        }
        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        // 屏幕状态广播
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AppConfig.ACTION_CUSTOM_BROADCAST)
            addAction(SCHEDULED_PUSH)
        }
        // 应用安装卸载广播
        val appFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        // 版本兼容注册
        val registerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RECEIVER_EXPORTED
        } else {
            RECEIVER_NOT_EXPORTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(reporterReceiver, reporterFilter, registerFlags)
            registerReceiver(messageReceiver, messageFilter, registerFlags)
            registerReceiver(batteryReceiver, batteryFilter, registerFlags)
            registerReceiver(screenReceiver, screenFilter, registerFlags)
            registerReceiver(installReceiver, appFilter, registerFlags)
        } else {
            registerReceiver(reporterReceiver, reporterFilter)
            registerReceiver(messageReceiver, messageFilter)
            registerReceiver(batteryReceiver, batteryFilter)
            registerReceiver(screenReceiver, screenFilter)
            registerReceiver(installReceiver, appFilter)
        }

        initVolumeKeyListener()

    }
    private fun createMessageReceiver() = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_START_MESSAGE) {
                Log.e("weiyu","sos消息来了")
                val messageType = intent.getStringExtra("messageType")
                val message = intent.getStringExtra("message")
                serviceScope.launch(eventDispatcher) {
                    handleStartMessage(messageType, message)
                }
            }

        }
    }

    private fun handleStartMessage(messageType: String?, message: String?) {
        when(messageType){
            "0"->{
                sendTemporaryNotification(
                    iconRes = R.mipmap.iv_sos,
                    title = MyApplication.instance.getString(R.string.sos_alert),
                    message = message,
                    button = MyApplication.instance.getString(R.string.go),
                    layoutId = R.layout.temp_power_notification,
                    bigLayoutId = R.layout.temp_power_big_notification,
                    headsUpLayoutId = R.layout.headsup_power,
                    intent = getIntent("/friendList")
                )
            }
            "1"->{
                var msg=""
                if(message?.contains("%") == true){
                    val split= message.split("%")
                    if(split.size==3){
                        when(split[1]){
                            "enter"->{
                                msg= getString(R.string.enter_place, split[0], split[2])
                            }
                            "leave"->{
                                msg= getString(R.string.leave_place, split[0], split[2])
                            }
                        }
                        sendTemporaryNotification(
                            iconRes = R.mipmap.ic_message_come,
                            title = MyApplication.instance.getString(R.string.zone_alert),
                            message = msg,
                            button = MyApplication.instance.getString(R.string.go),
                            layoutId = R.layout.temp_notification,
                            bigLayoutId = R.layout.temp_notification_big,
                            headsUpLayoutId = R.layout.headsup_normal,
                            intent = getIntent("/friendList")
                        )
                    }
                }
            }
        }
    }


    private fun getIntent(next:String): Intent {
        return Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtras(Bundle().apply {
                putString("next", next)
                putString("appOpenFrom", "notification")
            })
        }
    }
    private fun createReporterReceiver() = object : BroadcastReceiver() {
        @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            serviceScope.launch(eventDispatcher) {
                handleReporterAction(action)
            }
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun handleReporterAction(action: String) {
        when (action) {
            ACTION_START_BG_REPORTING -> {
                if (hasLocationPermission(true)) {
                    Log.e("weiyu", "start background reporting")
                    if (!LocationReporterManager.isRunning()) {
                        LocationReporterManager.start(applicationContext)
                    }
                    AppPrefsUtils.commitBoolean(KEY_REPORTING_ENABLED, true)
                }
            }
            ACTION_STOP_BG_REPORTING -> {
                Log.e("weiyu", "stop background reporting")
                LocationReporterManager.stop()
                AppPrefsUtils.commitBoolean(KEY_REPORTING_ENABLED, false)
            }
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${this::class.java.name}::WakeLock"
        )
        wakeLock.acquire(WAKE_LOCK_TIMEOUT)
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.set(false)   // ✅ 修改私有变量
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        unregisterReceivers()
        serviceScope.cancel()
        // 服务销毁时，如果应用进程未退出，通常不应该停止上报（因为可能还有后台需求）。
        // 但为了保险，这里不做额外操作，由 Activity 或其他组件控制上报的生命周期。
        super.onDestroy()
    }
    private fun unregisterReceivers() {
        try {
            unregisterReceiver(reporterReceiver)
            unregisterReceiver(messageReceiver)
            unregisterReceiver(batteryReceiver)
            unregisterReceiver(screenReceiver)
            unregisterReceiver(installReceiver)
            volumeObserver?.let { contentResolver.unregisterContentObserver(it) }
            volumeObserver = null
        } catch (e: Exception) {
            // 忽略未注册接收器的异常
        }
    }

    private fun startForegroundService() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()   // 停止服务
            Log.e("PersistentNotificationService", "启动前台服务失败", e)
        }
    }

    private fun initializeTempNotificationIds() {
        if (tempNotificationIds.isNullOrEmpty()) {
            tempNotificationIds = mutableListOf<Int>()
            pushInfoConfig?.NMax?.let { nMax ->
                if (nMax > 0) {
                    tempNotificationIds.addAll(1..nMax)
                    iterator = CircularIterator.of(tempNotificationIds)
                }
            }
        }
    }


    @SuppressLint("RemoteViewLayout")
    private fun sendTemporaryNotification(
        iconRes: Int,
        title: String,
        message: String?,
        button: String,
        layoutId: Int,
        bigLayoutId: Int,
        headsUpLayoutId: Int,
        intent: Intent,
    ) {
        try {
            initializeTempNotificationIds()
            val id = iterator.next()
            val remoteViews = RemoteViews(packageName, layoutId).apply {
                setImageViewResource(R.id.iv_push, iconRes)
                setTextViewText(R.id.tv_detail,message )
                setTextViewText(R.id.tv_next, button)
                if (isHuaweiOrHonor()) {
                    setViewVisibility(R.id.progress_ripple, View.GONE)
                    setViewVisibility(R.id.tv_hint, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.progress_ripple, View.VISIBLE)
                    setViewVisibility(R.id.tv_hint, View.GONE)
                }
            }
            val bigRemoteViews = RemoteViews(packageName, bigLayoutId).apply {
                setImageViewResource(R.id.iv_push, iconRes)
                setTextViewText(R.id.tv_title, title)
                setTextViewText(R.id.tv_message, message)
                setTextViewText(R.id.tv_next, button)
                if (isHuaweiOrHonor()) {
                    setViewVisibility(R.id.progress_ripple, View.GONE)
                    setViewVisibility(R.id.tv_hint, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.progress_ripple, View.VISIBLE)
                    setViewVisibility(R.id.tv_hint, View.GONE)
                }
            }
            var headsUpLayoutRemoteViews = RemoteViews(packageName, headsUpLayoutId).apply {
                setImageViewResource(R.id.iv_push, iconRes)
                setTextViewText(R.id.tv_message, message)
                setTextViewText(R.id.tv_title, title)
                setTextViewText(R.id.tv_next, button)
                if (isHuaweiOrHonor()) {
                    setViewVisibility(R.id.progress_ripple, View.GONE)
                    setViewVisibility(R.id.tv_hint, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.progress_ripple, View.VISIBLE)
                    setViewVisibility(R.id.tv_hint, View.GONE)
                }
            }
            val notification =
                NotificationCompat.Builder(this, NotificationChannelManager.NORMAL_CHANNEL_ID)
                    .setContentTitle(title)
                    .setSmallIcon(R.mipmap.ic_notify)
                    .setCustomContentView(remoteViews)
                    .setCustomBigContentView(bigRemoteViews)
                    .setCustomHeadsUpContentView(headsUpLayoutRemoteViews)  // 浮动通知布局
                    .setContentIntent(
                        PendingIntent.getActivity(
                            this,
                            id,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(false)
                    .setOnlyAlertOnce(false)
                    .setShowWhen(true)
                    .setWhen(System.currentTimeMillis())
                    .apply {
                        // 为通知设置唯一标签，便于管理
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
                        }
                    }
                    .build()
            notificationManager.notify(id, notification)
        } catch (e: Exception) {

        }
    }

    fun isHuaweiOrHonor(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.getDefault())
        val brand = Build.BRAND.lowercase(Locale.getDefault())
        val fingerprint = Build.FINGERPRINT.lowercase(Locale.getDefault())

        return manufacturer.contains("huawei") ||
                brand.contains("honor") ||
                fingerprint.contains("huawei")
    }


    private fun buildNotification(): Notification {
        // 修复通知标题和内容（原代码中两个 setContentText 会覆盖）
        val deleteIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, NotificationDeletedReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannelManager.PERSISTENT_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.running_in_background))
            .setSmallIcon(R.mipmap.ic_notify)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDeleteIntent(deleteIntent)
            .build()
    }

    private fun setupNotificationChannels() {
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        NotificationChannelManager.createAllChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
