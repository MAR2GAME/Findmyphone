package com.findmyphone.gpstracker.ad.pool

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.findmyphone.gpstracker.ad.AdLimitManager
import com.findmyphone.gpstracker.ad.LogAdData
import com.findmyphone.gpstracker.ad.LogAdParam
import com.findmyphone.gpstracker.bean.MyNativeAd
import com.findmyphone.gpstracker.bean.NativeAdClickEvent
import com.findmyphone.gpstracker.bean.NativeAdConfigBean
import com.findmyphone.gpstracker.bean.NativeIdBean
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.CircularIterator
import com.findmyphone.gpstracker.utils.LogUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pdffox.adv.AdvertiseSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

object MyAdLoader {
    private const val TAG = "AdLoader"
    private val iteratorLock = Any()
    private val initLock = Any()
    private val loadingQueue = ConcurrentHashMap<String, Job>()
    private val nativeLoadingCount = AtomicInteger(0)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Volatile
    private var iterator: CircularIterator<NativeIdBean>? = null

    @Volatile
    var nativeIdBeans: List<NativeIdBean> = emptyList()
        private set

    fun initNativeId() {
        synchronized(initLock) {
            runCatching {
                val ids = AppPrefsUtils.getString("native_ad_ids")
                val oldIds = AppPrefsUtils.getString("old_native_ad_ids")
                if (nativeIdBeans.isNotEmpty() && ids == oldIds && iterator != null) return
                AppPrefsUtils.commitString("old_native_ad_ids", ids)
                val json = if (AppConfig.isDebug) DEBUG_NATIVE_IDS else ids
                val parsed: List<NativeIdBean>? = Gson().fromJson(
                    json,
                    object : TypeToken<List<NativeIdBean>>() {}.type,
                )
                if (!parsed.isNullOrEmpty()) {
                    nativeIdBeans = parsed
                    synchronized(iteratorLock) {
                        iterator = CircularIterator.of(parsed)
                    }
                }
            }.onFailure { throwable ->
                Log.e(TAG, "initNativeId failed", throwable)
            }
        }
    }

    private fun nextNativeId(): NativeIdBean? = synchronized(iteratorLock) {
        iterator?.next()
    }

    fun getNextNativeIdForUsage(): NativeIdBean? = synchronized(iteratorLock) {
        iterator?.next()
    }

    fun isNetworkAvailableQuick(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun loadNative(context: Context, triggerKey: String = AdConfig.LOAD_TIME_OPEN_APP) {
        if (!isNetworkAvailableQuick(context)) return
        if (!AppPrefsUtils.getBoolean("canLoadNativeAd", true)) return
        updateNativeLoadConfig()
        if (!AdConfig.canLoadNative(triggerKey)) return

        val key = "native_admob"
        val job = scope.launch {
            try {
                fillAdmobNative(context)
            } finally {
                loadingQueue.remove(key, coroutineContext[Job])
            }
        }
        if (loadingQueue.putIfAbsent(key, job)?.isActive == true) {
            job.cancel()
        }
    }

    private fun updateNativeLoadConfig() {
        val config = if (AdvertiseSdk.isNature) {
            AppPrefsUtils.getString("adload_config_nature")
        } else {
            AppPrefsUtils.getString("adload_config")
        }
        if (config != AppPrefsUtils.getString("old_pool_config")) {
            AppPrefsUtils.commitString("old_pool_config", config)
            AdConfig.updateConfigFromJson(config)
        }
    }

    private fun fillAdmobNative(context: Context) {
        initNativeId()
        AdChecker.checkExpiredNative()
        val needLoad = AdConfig.adload_poolsize_native -
            AdPool.admobNativePool.size -
            nativeLoadingCount.get()
        if (needLoad <= 0 || nativeIdBeans.isEmpty()) return

        repeat(needLoad) {
            nativeLoadingCount.incrementAndGet()
            scope.launch {
                try {
                    val ids = nextNativeId() ?: return@launch
                    val config = loadAdsConcurrently(
                        context,
                        ids.highPriceID,
                        ids.midPriceID,
                        ids.lowPriceID,
                    )
                    AdPool.putAdmobNative(config)
                } finally {
                    nativeLoadingCount.decrementAndGet()
                }
            }
        }
    }

    suspend fun loadAdsConcurrently(
        context: Context,
        highUnitId: String,
        mediumUnitId: String,
        lowUnitId: String,
    ): NativeAdConfigBean = coroutineScope {
        val highDeferred = async { loadNativeAd(context, highUnitId) }
        val mediumDeferred = async { loadNativeAd(context, mediumUnitId) }
        val lowDeferred = async { loadNativeAd(context, lowUnitId) }
        val high = runCatching { highDeferred.await() }.getOrNull()
        if (high != null) {
            mediumDeferred.cancel()
            lowDeferred.cancel()
        }
        val medium = runCatching { mediumDeferred.await() }.getOrNull()
        val low = runCatching { lowDeferred.await() }.getOrNull()
        NativeAdConfigBean(
            MyNativeAd(highUnitId, high),
            MyNativeAd(mediumUnitId, medium),
            MyNativeAd(lowUnitId, low),
        )
    }

    suspend fun loadNativeAd(context: Context, adUnitId: String): NativeAd? =
        suspendCancellableCoroutine { continuation ->
            AdLimitManager.getInstance().recordNativeAdRequest()
            val loader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad ->
                    if (continuation.isActive) continuation.resume(ad) else ad.destroy()
                }
                .withAdListener(object : AdListener() {
                    override fun onAdClicked() {
                        LogUtil.log(
                            LogAdData.ad_click,
                            mapOf(
                                LogAdParam.ad_platform to LogAdParam.ad_platform_admob,
                                LogAdParam.ad_format to LogAdParam.ad_format_native,
                                LogAdParam.ad_preload to true,
                            ),
                        )
                        EventBus.getDefault().post(NativeAdClickEvent(true))
                        AdLimitManager.getInstance().recordNativeAdClick()
                    }

                    override fun onAdImpression() {
                        ActivityManagerUtils.currentActivity()?.let {
                            loadNative(it, AdConfig.LOAD_TIME_PLAY_FINISH)
                        }
                        AdLimitManager.getInstance().recordNativeAdShow()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
                .build()
            loader.loadAd(AdRequest.Builder().build())
        }

    fun cleanup() {
        loadingQueue.values.forEach { it.cancel() }
        loadingQueue.clear()
        nativeLoadingCount.set(0)
    }

    private const val DEBUG_NATIVE_IDS = """[
      {"highPriceID":"ca-app-pub-3940256099942544/2247696110","midPriceID":"ca-app-pub-3940256099942544/2247696110","lowPriceID":"ca-app-pub-3940256099942544/2247696110"},
      {"highPriceID":"ca-app-pub-3940256099942544/2247696110","midPriceID":"ca-app-pub-3940256099942544/2247696110","lowPriceID":"ca-app-pub-3940256099942544/2247696110"}
    ]"""
}
