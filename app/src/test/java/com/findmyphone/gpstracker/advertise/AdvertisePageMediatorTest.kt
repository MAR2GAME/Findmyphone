package com.findmyphone.gpstracker.advertise

import com.findmyphone.gpstracker.ad.AreaKey
import com.pdffox.adv.AdvertiseSdk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertisePageMediatorTest {
    @After
    fun resetForegroundOpenAdState() {
        AdvertiseSdk.isAppOpenAdEnabled = true
    }

    @Test
    fun reads_the_current_foreground_open_ad_state() {
        AdvertiseSdk.isAppOpenAdEnabled = false

        assertFalse(AdvertisePageMediator.isForegroundOpenAdEnabled())
    }

    @Test
    fun cached_open_ad_is_valid_only_before_expiry() {
        assertTrue(
            AdvertisePageMediator.isCachedOpenAdFresh(
                loadedAt = 1_000L,
                now = 1_999L,
                cacheLifetimeMs = 1_000L,
            )
        )
        assertFalse(
            AdvertisePageMediator.isCachedOpenAdFresh(
                loadedAt = 1_000L,
                now = 2_000L,
                cacheLifetimeMs = 1_000L,
            )
        )
    }

    @Test
    fun callback_once_invokes_its_callback_only_once() {
        var invocationCount = 0
        val callback = CallbackOnce { invocationCount += 1 }

        callback.invoke()
        callback.invoke()

        assertEquals(1, invocationCount)
    }

    @Test
    fun blank_interstitial_area_key_continues_once_without_presenting() {
        var continuationCount = 0
        var presentationCount = 0

        showInterstitialPlayback(
            areaKey = "  ",
            isAvailable = { true },
            show = { _, _ -> presentationCount += 1 },
            onClosed = { continuationCount += 1 },
            onFailure = { },
        )

        assertEquals(0, presentationCount)
        assertEquals(1, continuationCount)
    }

    @Test
    fun duplicate_interstitial_completion_continues_once() {
        var continuationCount = 0

        showInterstitialPlayback(
            areaKey = "testArea",
            isAvailable = { true },
            show = { _, onComplete ->
                onComplete()
                onComplete()
            },
            onClosed = { continuationCount += 1 },
            onFailure = { },
        )

        assertEquals(1, continuationCount)
    }

    @Test
    fun thrown_interstitial_presenter_error_continues_once() {
        var continuationCount = 0
        var failureCount = 0

        showInterstitialPlayback(
            areaKey = "testArea",
            isAvailable = { true },
            show = { _, _ -> throw IllegalStateException("presenter failed") },
            onClosed = { continuationCount += 1 },
            onFailure = { failureCount += 1 },
        )

        assertEquals(1, failureCount)
        assertEquals(1, continuationCount)
    }

    @Test
    fun unavailable_before_interstitial_start_does_not_present_or_continue() {
        var continuationCount = 0
        var presentationCount = 0

        showInterstitialPlayback(
            areaKey = "testArea",
            isAvailable = { false },
            show = { _, _ -> presentationCount += 1 },
            onClosed = { continuationCount += 1 },
            onFailure = { },
        )

        assertEquals(0, presentationCount)
        assertEquals(0, continuationCount)
    }

    @Test
    fun unavailable_before_interstitial_close_does_not_continue() {
        var isAvailable = true
        var continuationCount = 0
        lateinit var onComplete: () -> Unit

        showInterstitialPlayback(
            areaKey = "testArea",
            isAvailable = { isAvailable },
            show = { _, completion -> onComplete = completion },
            onClosed = { continuationCount += 1 },
            onFailure = { },
        )

        isAvailable = false
        onComplete()

        assertEquals(0, continuationCount)
    }

    @Test
    fun interstitial_request_gate_rejects_reentrant_requests_until_released() {
        val gate = InterstitialRequestGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun launch_open_ad_uses_the_policy_aware_sdk_entrypoint() {
        val source = File(
            "src/main/java/com/findmyphone/gpstracker/advertise/AdvertisePageMediator.kt"
        ).readText()

        assertTrue(source.contains("ShowOpenAd.showOpenAd("))
        assertTrue(source.contains("areaKey = AreaKey.openPageAdv"))
        assertFalse(source.contains("ShowOpenAd.showAdmobOpenAdFromCache("))
    }

    @Test
    fun interstitial_ad_uses_the_policy_aware_sdk_entrypoint() {
        val source = File(
            "src/main/java/com/findmyphone/gpstracker/advertise/AdvertisePageMediator.kt"
        ).readText()

        assertTrue(source.contains("fun showInterstitial("))
        assertTrue(source.contains("AdvertiseSdk.showInterstitialAd(activity, areaKey)"))
        assertTrue(source.contains("areaKey.isNullOrBlank()"))
        assertTrue(source.contains("showInterstitial failed; continuing flow"))
    }
}
