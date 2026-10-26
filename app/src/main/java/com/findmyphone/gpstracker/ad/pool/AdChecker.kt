package com.findmyphone.gpstracker.ad.pool

object AdChecker {
    fun checkExpiredNative() {
        val now = System.currentTimeMillis()
        val expired = AdPool.admobNativePool
            .filterValues { now - it > AdConfig.adload_cache_time }
            .keys
        expired.forEach { config ->
            config.highNativeAd.nativeAd?.destroy()
            config.midNativeAd.nativeAd?.destroy()
            config.lowNativeAd.nativeAd?.destroy()
            AdPool.admobNativePool.remove(config)
        }
    }
}
