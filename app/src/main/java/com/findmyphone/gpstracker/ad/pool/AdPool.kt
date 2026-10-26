package com.findmyphone.gpstracker.ad.pool

import android.util.Log
import com.findmyphone.gpstracker.ad.AdvCheckManager
import com.findmyphone.gpstracker.ad.LogAdData
import com.findmyphone.gpstracker.ad.LogAdParam
import com.findmyphone.gpstracker.bean.NativeAdConfigBean
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.nativead.NativeAd
import com.google.firebase.analytics.FirebaseAnalytics
import com.singular.sdk.Singular
import com.singular.sdk.SingularAdData
import org.json.JSONObject

object AdPool {
    private const val TAG = "AdPool"

    val admobNativePool = LinkedHashMap<NativeAdConfigBean, Long>()

    fun putAdmobNative(config: NativeAdConfigBean) {
        synchronized(admobNativePool) {
            admobNativePool[config] = System.currentTimeMillis()
            while (admobNativePool.size > AdConfig.adload_poolsize_native) {
                val oldest = admobNativePool.entries.firstOrNull()?.key ?: break
                destroy(oldest)
                admobNativePool.remove(oldest)
            }
        }
    }

    fun getAdmobNative(areaKey: String): NativeAd? {
        AdChecker.checkExpiredNative()
        val config = synchronized(admobNativePool) {
            admobNativePool.maxByOrNull { it.value }?.key
        } ?: return null
        val candidates = listOf(config.highNativeAd, config.midNativeAd, config.lowNativeAd)
        val selected = candidates.firstOrNull { it.nativeAd != null } ?: config.lowNativeAd
        candidates.filter { it !== selected }.forEach { it.nativeAd?.destroy() }
        val nativeAd = selected.nativeAd
        nativeAd?.setOnPaidEventListener { value ->
            handleRevenue(value, areaKey, selected.id, nativeAd)
        }
        synchronized(admobNativePool) {
            admobNativePool.remove(config)
        }
        return nativeAd
    }

    fun clearAllPools() {
        synchronized(admobNativePool) {
            admobNativePool.keys.forEach(::destroy)
            admobNativePool.clear()
        }
    }

    private fun destroy(config: NativeAdConfigBean) {
        config.highNativeAd.nativeAd?.destroy()
        config.midNativeAd.nativeAd?.destroy()
        config.lowNativeAd.nativeAd?.destroy()
    }

    private fun handleRevenue(value: AdValue, areaKey: String, unitId: String, ad: NativeAd) {
        val revenue = value.valueMicros / 1_000_000.0
        runCatching {
            Singular.eventJSON(
                LogAdData.ad_revenue,
                JSONObject().apply {
                    put(LogAdParam.revenue, revenue)
                    put(LogAdParam.adType, LogAdParam.NativeAd)
                },
            )
            if (revenue > 0) {
                Singular.adRevenue(
                    SingularAdData(LogAdParam.adMob, value.currencyCode, revenue)
                )
            } else if (AppPrefsUtils.getBoolean("is_first_ad", true)) {
                AppPrefsUtils.commitBoolean("is_first_ad", false)
                AdvCheckManager.params.fromNature = true
            }
        }.onFailure { throwable ->
            Log.e(TAG, "Native ad revenue reporting failed", throwable)
        }
        val params = mapOf(
            LogAdParam.ad_areakey to areaKey,
            FirebaseAnalytics.Param.AD_PLATFORM to LogAdParam.ad_platform_admob,
            FirebaseAnalytics.Param.AD_UNIT_NAME to unitId,
            FirebaseAnalytics.Param.AD_FORMAT to LogAdParam.ad_format_native,
            FirebaseAnalytics.Param.AD_SOURCE to
                (ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: LogAdParam.unknown),
            FirebaseAnalytics.Param.CURRENCY to value.currencyCode,
            FirebaseAnalytics.Param.VALUE to revenue,
            LogAdParam.ad_preload to true,
        )
        LogUtil.log(LogAdData.ad_impression, params)
        LogUtil.log(LogAdData.ad_revenue, params)
        LogUtil.logTaiChiAdmob(value)
    }
}
