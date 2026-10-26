package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenAdMigrationSourceTest {
    @Test
    fun splash_uses_sdk_open_ad_and_legacy_helpers_are_removed() {
        val main = File("src/main")
        val splashSource = File(
            main,
            "java/com/findmyphone/gpstracker/ui/SplashActivity.kt",
        ).readText()

        assertTrue(splashSource.contains("AdvertisePageMediator.preloadLaunchAds("))
        assertTrue(splashSource.contains("AdvertisePageMediator.hasCachedLaunchOpenAd()"))
        assertTrue(splashSource.contains("AdvertisePageMediator.isLaunchOpenAdLoading()"))
        assertTrue(splashSource.contains("AdvertisePageMediator.showCachedLaunchOpenAd("))
        assertFalse(splashSource.contains("com.findmyphone.gpstracker.ad.ShowOpenAd"))
        assertFalse(splashSource.contains("MyAdLoader.loadOpen"))
        assertFalse(splashSource.contains("SdkInitUtils.awaitAdMobInitialization"))
        assertFalse(splashSource.contains("UmpConsentManager"))
        assertFalse(
            File(main, "java/com/findmyphone/gpstracker/ad/ShowOpenAd.kt").exists()
        )
        assertFalse(
            File(main, "java/com/findmyphone/gpstracker/ad/AppOpenHelper.kt").exists()
        )
    }
}
