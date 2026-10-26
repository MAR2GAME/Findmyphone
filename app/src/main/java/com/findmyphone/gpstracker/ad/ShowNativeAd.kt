package com.findmyphone.gpstracker.ad

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.firebase.analytics.FirebaseAnalytics
import com.findmyphone.gpstracker.ad.pool.AdConfig.LOAD_TIME_PLAY_FINISH
import com.findmyphone.gpstracker.ad.pool.AdPool
import com.findmyphone.gpstracker.ad.pool.MyAdLoader
import com.findmyphone.gpstracker.bean.NativeAdClickEvent
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LogUtil

import com.singular.sdk.Singular
import com.singular.sdk.SingularAdData
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import org.greenrobot.eventbus.EventBus
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

object ShowNativeAd {

    suspend fun getNativeAd(
        activity: Activity,
        areaKey: String
    ): NativeAd? {
        if (!AdLimitManager.getInstance().canShowNativeAd(areaKey)) return null
        val weakActivity = WeakReference(activity)
        val activityRef = weakActivity.get() ?: return null
        return try {
            MyAdLoader.initNativeId()
            val cachedAd = AdPool.getAdmobNative(areaKey)
            if (cachedAd != null) {
                cachedAd
            } else {
                // 使用线程安全的方式获取 NativeIdBean
                val nativeIdBean = MyAdLoader.getNextNativeIdForUsage()
                if (nativeIdBean == null) {
                    return null
                }
                loadAdsConcurrently(
                    areaKey,
                    activityRef,
                    nativeIdBean.highPriceID,
                    nativeIdBean.midPriceID,
                    nativeIdBean.lowPriceID
                )
            }
        } catch (e: Exception) {
            Log.e("ShowNativeAd", "getNativeAd error", e)
            null
        }
    }

    suspend fun loadAdsConcurrently(
        areaKey: String,
        context: Context,
        highUnitId: String,
        mediumUnitId: String,
        lowUnitId: String
    ): NativeAd = coroutineScope {
        data class AdTask(val deferred: Deferred<NativeAd?>, val unitId: String)

        val tasks = listOf(
            AdTask(async { loadNativeAd(areaKey, context, highUnitId) }, highUnitId),
            AdTask(async { loadNativeAd(areaKey, context, mediumUnitId) }, mediumUnitId),
            AdTask(async { loadNativeAd(areaKey, context, lowUnitId) }, lowUnitId)
        )
        var selectedTask: AdTask? = null
        var selectedAd: NativeAd? = null

        for (task in tasks) {
            val ad = try {
                task.deferred.await()
            } catch (e: Exception) {
                null
            }
            if (ad != null) {
                selectedTask = task
                selectedAd = ad
                break
            }
        }

        if (selectedTask == null) {
            tasks.forEach { task ->
                if (task.deferred.isCompleted) {
                    runCatching { task.deferred.await() }.getOrNull()?.destroy()
                }
            }
            error("All ads failed to load")
        }

        tasks.forEach { task ->
            if (task != selectedTask) {
                task.deferred.cancel()
                if (task.deferred.isCompleted) {
                    runCatching { task.deferred.await() }.getOrNull()?.destroy()
                }
            }
        }

        selectedAd!!.setOnPaidEventListener { adValue ->
            handleAdRevenue(
                adValue,
                areaKey,
                LogAdParam.NativeAd,
                selectedTask.unitId,
                LogAdParam.ad_platform_admob,
                selectedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName
                    ?: LogAdParam.unknown,
                LogAdParam.ad_format_native
            )
        }
        return@coroutineScope selectedAd
    }

    suspend fun loadNativeAd(
        areaKey: String,
        context: Context,
        adUnitId: String
    ): NativeAd? = suspendCancellableCoroutine { continuation ->
        AdLimitManager.getInstance().recordNativeAdRequest()
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                if (continuation.isActive) {
                    continuation.resume(nativeAd)
                } else {
                    nativeAd.destroy()
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdClicked() {
                    LogUtil.log(
                        LogAdData.ad_click,
                        mapOf(
                            LogAdParam.ad_platform to LogAdParam.ad_platform_admob,
                            LogAdParam.ad_areakey to areaKey,
                            LogAdParam.ad_format to LogAdParam.ad_format_native
                        )
                    )
                    EventBus.getDefault().post(NativeAdClickEvent(true))
                    AdLimitManager.getInstance().recordNativeAdClick()
                }

                override fun onAdImpression() {
                   ActivityManagerUtils.currentActivity()?.let {
                        MyAdLoader.loadNative(it, LOAD_TIME_PLAY_FINISH)
                    }
                    AdLimitManager.getInstance().recordNativeAdShow()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun handleAdRevenue(
        adValue: AdValue,
        areaKey: String,
        adType: String,
        unitName: String,
        platform: String,
        adSource: String,
        adformat: String
    ) {
        val micros = adValue.valueMicros
        val currency = adValue.currencyCode
        val revenue = micros / 1_000_000.0

        val att = JSONObject()
        try {
            att.put(LogAdParam.revenue, revenue)
            att.put(LogAdParam.adType, adType)
        } catch (e: JSONException) {
            // ignore
        }
        Singular.eventJSON(LogAdData.ad_revenue, att)
        if (revenue > 0) {
            val data = SingularAdData(platform, currency, revenue)
            Singular.adRevenue(data)
        } else {
            if (AppPrefsUtils.getBoolean("is_first_ad", true)) {
                AppPrefsUtils.commitBoolean("is_first_ad", false)
                AdvCheckManager.params.fromNature = true
            }
        }

        LogUtil.log(
            LogAdData.ad_impression,
            mapOf(
                LogAdParam.ad_areakey to areaKey,
                FirebaseAnalytics.Param.AD_PLATFORM to platform,
                FirebaseAnalytics.Param.AD_UNIT_NAME to unitName,
                FirebaseAnalytics.Param.AD_FORMAT to adformat,
                FirebaseAnalytics.Param.AD_SOURCE to adSource,
                FirebaseAnalytics.Param.CURRENCY to currency,
                FirebaseAnalytics.Param.VALUE to revenue,
                LogAdParam.ad_preload to true,
            )
        )
        LogUtil.log(
            LogAdData.ad_revenue,
            mapOf(
                LogAdParam.ad_areakey to areaKey,
                FirebaseAnalytics.Param.AD_PLATFORM to platform,
                FirebaseAnalytics.Param.AD_UNIT_NAME to unitName,
                FirebaseAnalytics.Param.AD_FORMAT to adformat,
                FirebaseAnalytics.Param.AD_SOURCE to adSource,
                FirebaseAnalytics.Param.CURRENCY to currency,
                FirebaseAnalytics.Param.VALUE to revenue,
                LogAdParam.ad_preload to true,
            )
        )
        LogUtil.logTaiChiAdmob(adValue)
    }
}