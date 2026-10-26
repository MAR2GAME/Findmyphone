package com.findmyphone.gpstracker.advertise

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import com.findmyphone.gpstracker.ad.AreaKey
import com.google.android.ump.UserMessagingPlatform
import com.pdffox.adv.AdvertiseSdk
import com.pdffox.adv.adv.AdConfig
import com.pdffox.adv.adv.AdPool
import com.pdffox.adv.adv.ShowOpenAd

object AdvertisePageMediator {
    private const val TAG = "AdvertisePageMediator"

    fun initSplashConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (activity.isUnavailable()) {
            onComplete(false)
            return
        }
        runCatching {
            val hasCachedConsentState = AdvertiseSdk.initConsent(activity) { success ->
                onComplete(success)
            }
            if (hasCachedConsentState) {
                onComplete(true)
            }
        }.onFailure { throwable ->
            Log.w(TAG, "initSplashConsent failed", throwable)
            onComplete(false)
        }
    }

    fun showSplashConsent(
        activity: Activity,
        onWillShowForm: (() -> Unit)? = null,
        onComplete: () -> Unit,
    ) {
        if (activity.isUnavailable()) {
            onComplete()
            return
        }
        runCatching {
            logSplashConsentFormIfAvailable(activity, onWillShowForm)
            AdvertiseSdk.showSplashConsent(activity) {
                onComplete()
            }
        }.onFailure { throwable ->
            Log.w(TAG, "showSplashConsent failed", throwable)
            onComplete()
        }
    }

    @SuppressLint("MissingPermission")
    fun preloadLaunchAds(context: Context) {
        runCatching {
            if (AdvertiseSdk.canPreloadOpen(AdvertiseSdk.LOAD_TIME_OPEN_APP)) {
                AdvertiseSdk.preloadOpen(context)
            }
            if (AdvertiseSdk.canPreloadInterstitial(AdvertiseSdk.LOAD_TIME_OPEN_APP)) {
                AdvertiseSdk.preloadInterstitial(context)
            }
        }.onFailure { throwable ->
            Log.w(TAG, "preloadLaunchAds failed", throwable)
        }
    }

    fun hasCachedLaunchOpenAd(): Boolean {
        return runCatching {
            val now = System.currentTimeMillis()
            val cacheLifetimeMs = AdConfig.adload_cache_time_open
            AdPool.admobOpenPool.values.any { loadedAt ->
                isCachedOpenAdFresh(loadedAt, now, cacheLifetimeMs)
            }
        }.onFailure { throwable ->
            Log.w(TAG, "read App Open cache state failed", throwable)
        }.getOrDefault(false)
    }

    fun isLaunchOpenAdLoading(): Boolean {
        return runCatching {
            AdPool.admobOpenIsLoadingNum > 0
        }.onFailure { throwable ->
            Log.w(TAG, "read App Open loading state failed", throwable)
        }.getOrDefault(false)
    }

    fun suppressNextForegroundOpenAd() {
        runCatching {
            AdvertiseSdk.suppressNextAppOpenAd = true
        }.onFailure { throwable ->
            Log.w(TAG, "suppressNextForegroundOpenAd failed", throwable)
        }
    }

    fun setForegroundOpenAdEnabled(enabled: Boolean) {
        runCatching {
            AdvertiseSdk.isAppOpenAdEnabled = enabled
        }.onFailure { throwable ->
            Log.w(TAG, "setForegroundOpenAdEnabled failed", throwable)
        }
    }

    fun showInterstitial(
        activity: Activity,
        areaKey: String?,
        onClosed: () -> Unit,
    ) {
        showInterstitialPlayback(
            areaKey = areaKey,
            isAvailable = { !activity.isUnavailable() },
            show = { areaKey, onComplete ->
                AdvertiseSdk.showInterstitialAd(activity, areaKey) {
                    onComplete()
                }
            },
            onClosed = onClosed,
            onFailure = { throwable ->
                Log.w(TAG, "showInterstitial failed; continuing flow", throwable)
            },
        )
    }

    fun isForegroundOpenAdEnabled(): Boolean {
        return runCatching {
            AdvertiseSdk.isAppOpenAdEnabled
        }.onFailure { throwable ->
            Log.w(TAG, "read foreground App Open state failed", throwable)
        }.getOrDefault(true)
    }

    fun showCachedLaunchOpenAd(
        activity: Activity,
        onContinue: () -> Unit,
    ): Boolean {
        if (activity.isUnavailable()) return false
        val continueOnce = CallbackOnce {
            if (!activity.isUnavailable()) {
                onContinue()
            }
        }

        return runCatching {
            ShowOpenAd.showOpenAd(
                activity = activity,
                areaKey = AreaKey.openPageAdv,
                onCloseListener = ShowOpenAd.OpenAdCloseListener { continueOnce.invoke() },
                onLoadedListener = ShowOpenAd.OpenAdLoadedListener { },
                onPaidListener = ShowOpenAd.OpenAdPaidListener { _ -> },
            )
            true
        }.onFailure { throwable ->
            Log.w(TAG, "showLaunchOpenAd failed", throwable)
        }.getOrDefault(false)
    }

    internal fun isCachedOpenAdFresh(
        loadedAt: Long,
        now: Long,
        cacheLifetimeMs: Long,
    ): Boolean {
        return now - loadedAt < cacheLifetimeMs
    }

    private fun Activity.isUnavailable(): Boolean = isFinishing || isDestroyed

    private fun logSplashConsentFormIfAvailable(
        activity: Activity,
        onWillShowForm: (() -> Unit)?,
    ) {
        runCatching {
            if (UserMessagingPlatform.getConsentInformation(activity).isConsentFormAvailable) {
                onWillShowForm?.invoke()
            }
        }.onFailure { throwable ->
            Log.w(TAG, "read UMP consent form state failed", throwable)
        }
    }
}

internal fun showInterstitialPlayback(
    areaKey: String?,
    isAvailable: () -> Boolean,
    show: (String, () -> Unit) -> Unit,
    onClosed: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    if (!isAvailable()) return

    val closeOnce = CallbackOnce {
        if (isAvailable()) {
            onClosed()
        }
    }

    if (areaKey.isNullOrBlank()) {
        closeOnce.invoke()
        return
    }

    runCatching {
        show(areaKey) {
            closeOnce.invoke()
        }
    }.onFailure { throwable ->
        onFailure(throwable)
        closeOnce.invoke()
    }
}
