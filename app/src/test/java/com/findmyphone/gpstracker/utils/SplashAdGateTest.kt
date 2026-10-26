package com.findmyphone.gpstracker.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashAdGateTest {

    @Test
    fun keepsSplashWaitingWhileOpenAdCanStillLoad() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L)
        gate.markAdRequestStarted(startElapsedMs = 0L)

        assertFalse(gate.shouldNavigateFromProgress(progress = 100, nowElapsedMs = 6_499L))
    }

    @Test
    fun allowsNavigationWhenOpenAdWaitTimesOut() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L)
        gate.markAdRequestStarted(startElapsedMs = 0L)

        assertTrue(gate.shouldNavigateFromProgress(progress = 100, nowElapsedMs = 6_500L))
    }

    @Test
    fun allowsNavigationImmediatelyWhenOpenAdFailsToLoad() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L)
        gate.markAdRequestStarted(startElapsedMs = 0L)
        gate.markAdUnavailable()

        assertTrue(gate.shouldNavigateFromProgress(progress = 100, nowElapsedMs = 500L))
    }

    @Test
    fun doesNotNavigateFromProgressAfterOpenAdIsDisplayed() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L)
        gate.markAdRequestStarted(startElapsedMs = 0L)
        gate.markAdLoaded(loadedElapsedMs = 100L)
        gate.markAdDisplayed()

        assertFalse(gate.shouldNavigateFromProgress(progress = 100, nowElapsedMs = 10_000L))
    }

    @Test
    fun keepsSplashWaitingBrieflyWhenOpenAdLoadedNearTimeout() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L, showGraceAfterLoadMs = 1_000L)
        gate.markAdRequestStarted(startElapsedMs = 0L)
        gate.markAdLoaded(loadedElapsedMs = 6_400L)

        assertFalse(gate.shouldNavigateFromProgress(progress = 100, nowElapsedMs = 6_900L))
        assertTrue(gate.shouldNavigateFromProgress(progress = 100, nowElapsedMs = 7_400L))
    }

    @Test
    fun allowsNavigationWhenAdRequestWasSkipped() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L)

        assertTrue(gate.shouldNavigateFromProgress(progress = 100, nowElapsedMs = 0L))
    }

    @Test
    fun capsVisibleProgressWhileOpenAdCanStillLoad() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L, waitingProgressCap = 98)
        gate.markAdRequestStarted(startElapsedMs = 0L)

        assertTrue(gate.shouldHoldForAd(nowElapsedMs = 6_499L))
        assertTrue(gate.shouldShowFullProgress(nowElapsedMs = 6_500L))
        assertTrue(gate.visibleProgress(progress = 100, nowElapsedMs = 6_499L) < 100)
    }

    @Test
    fun showsFullProgressWhenOpenAdWaitIsResolved() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L, waitingProgressCap = 98)
        gate.markAdRequestStarted(startElapsedMs = 0L)
        gate.markAdUnavailable()

        assertTrue(gate.shouldShowFullProgress(nowElapsedMs = 500L))
        assertTrue(gate.visibleProgress(progress = 100, nowElapsedMs = 500L) == 100)
    }

    @Test
    fun keepsProgressHiddenUntilOpenAdWaitIsResolved() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L, waitingProgressCap = 98)
        gate.markAdRequestStarted(startElapsedMs = 0L)

        assertFalse(gate.shouldShowFullProgress(nowElapsedMs = 6_499L))
    }

    @Test
    fun startup_timeout_is_cancelled_only_after_open_ad_is_displayed() {
        val gate = SplashAdGate(waitTimeoutMs = 6_500L)
        gate.markAdRequestStarted(startElapsedMs = 0L)

        assertFalse(gate.shouldCancelStartupTimeout())
        gate.markAdDisplayed()
        assertTrue(gate.shouldCancelStartupTimeout())
    }
}
