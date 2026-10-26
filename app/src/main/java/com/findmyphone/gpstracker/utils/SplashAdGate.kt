package com.findmyphone.gpstracker.utils

import kotlin.let
import kotlin.ranges.coerceAtMost
import kotlin.ranges.coerceIn

class SplashAdGate(
    private val waitTimeoutMs: Long,
    private val showGraceAfterLoadMs: Long = 800L,
    private val waitingProgressCap: Int = 98
) {
    private var adRequestStartElapsedMs: Long? = null
    private var adLoadedElapsedMs: Long? = null
    private var adUnavailable = false
    private var adDisplayed = false

    fun markAdRequestStarted(startElapsedMs: Long) {
        adRequestStartElapsedMs = startElapsedMs
        adLoadedElapsedMs = null
        adUnavailable = false
        adDisplayed = false
    }

    fun markAdLoaded(loadedElapsedMs: Long) {
        if (adRequestStartElapsedMs != null) {
            adLoadedElapsedMs = loadedElapsedMs
        }
    }

    fun markAdDisplayed() {
        adDisplayed = true
    }

    fun markAdUnavailable() {
        adUnavailable = true
    }

    fun shouldNavigateFromProgress(progress: Int, nowElapsedMs: Long): Boolean {
        if (progress < 100) return false
        if (adDisplayed) return false
        return !shouldHoldForAd(nowElapsedMs)
    }

    fun shouldHoldForAd(nowElapsedMs: Long): Boolean {
        val requestStart = adRequestStartElapsedMs ?: return false
        if (adUnavailable || adDisplayed) return false

        val waitUntil = adLoadedElapsedMs?.let { loadedAt ->
            kotlin.comparisons.maxOf(requestStart + waitTimeoutMs, loadedAt + showGraceAfterLoadMs)
        } ?: (requestStart + waitTimeoutMs)

        return nowElapsedMs < waitUntil
    }

    fun shouldShowFullProgress(nowElapsedMs: Long): Boolean {
        return !shouldHoldForAd(nowElapsedMs)
    }

    fun shouldCancelStartupTimeout(): Boolean = adDisplayed

    fun visibleProgress(progress: Int, nowElapsedMs: Long): Int {
        if (!shouldHoldForAd(nowElapsedMs)) return progress
        return progress.coerceAtMost(waitingProgressCap.coerceIn(0, 99))
    }
}
