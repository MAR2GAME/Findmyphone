package com.findmyphone.gpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import cn.thinkingdata.analytics.TDAnalytics
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.pool.MyAdLoader
import com.findmyphone.gpstracker.advertise.AdvertisePageMediator
import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.base.activity.BaseViewActivity
import com.findmyphone.gpstracker.base.command.REQUEST_CODE_NOTIFICATION
import com.findmyphone.gpstracker.base.command.checkNotificationPermission
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.base.command.safeClick
import com.findmyphone.gpstracker.config.AppConfig
import com.findmyphone.gpstracker.databinding.ActivitySplashBinding
import com.findmyphone.gpstracker.services.PersistentNotificationService
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.utils.SplashAdGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseViewActivity<ActivitySplashBinding>() {
    companion object {
        private const val TOTAL_PROGRESS = 100
        private const val WAITING_PROGRESS_CAP = 80
        private const val WAITING_PROGRESS_INTERVAL_MS = 150L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 80L
        private const val PROGRESS_ANIMATION_FRAME_MS = 16L
        private const val AD_PROGRESS_ANIMATION_MS = 300L
        private const val GLOBAL_TIMEOUT_MS = 20_000L
        private const val CONSENT_TIMEOUT_MS = 6_000L
        private const val AD_WAIT_TIMEOUT_MS = 8_000L
        private const val OPEN_AD_FOREGROUND_WAIT_TIMEOUT_MS = 6_000L
        private const val OPEN_AD_POLL_INTERVAL_MS = 100L
        private const val TAG = "SplashActivity"
    }

    private val isNavigating = AtomicBoolean(false)
    private val splashAdGate = SplashAdGate(waitTimeoutMs = AD_WAIT_TIMEOUT_MS)
    private val consentInitialized = CompletableDeferred<Boolean>()

    private var currentProgress = 0
    private var startElapsedRealtime = 0L
    private var progressJob: Job? = null
    private var waitingJob: Job? = null
    private var startupJob: Job? = null
    private var openAdJob: Job? = null
    private var globalTimeoutJob: Job? = null
    private var hasStartedConsentInitialization = false
    private var hasReturnedFromBackgroundDuringSplash = false
    private var foregroundOpenAdDisabledForSplash = false
    private var foregroundOpenAdRestoreDeferred = false
    private var foregroundOpenAdPreviouslyEnabled = true
    private var isShowingLaunchOpenAd = false
    private var isWaitingForNotificationPermission = false
    private var isShowingSplashConsent = false

    private var trafficSource: String? = null
    private var startTime: Long = 0L

    override fun initializeActivity() {
        super.initializeActivity()
        startTime = System.currentTimeMillis()
        startElapsedRealtime = SystemClock.elapsedRealtime()
        AppPrefsUtils.commitLong(AppConfig.START_TIME, startTime)
        if(AppPrefsUtils.getBoolean("is_First",true)){
            AppPrefsUtils.commitBoolean("is_First",false)
            LogUtil.setUser(
                mapOf("my_first_open_time" to getDetailCurrentTime(System.currentTimeMillis())),
                "user_setOnce"
            )
        }
        setupBackPressHandler()
        setupTermsClickListeners()
        suppressForegroundOpenAdDuringSplash()
        AppPrefsUtils.commitBoolean("in_splash", true)
        checkPermissionAndStart()
    }

    override fun onStop() {
        super.onStop()
        if (
            isShowingLaunchOpenAd ||
            isWaitingForNotificationPermission ||
            isShowingSplashConsent ||
            isNavigating.get() ||
            isFinishing ||
            isDestroyed
        ) {
            return
        }

        hasReturnedFromBackgroundDuringSplash = true
        suppressForegroundOpenAdAfterSplashBackground()
    }

    override fun onDestroy() {
        AppPrefsUtils.commitBoolean("in_splash", false)
        cancelStartupJobs()
        deferForegroundOpenAdRestore()
        isShowingLaunchOpenAd = false
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATION) {
            isWaitingForNotificationPermission = false
            val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val eventName = if (isGranted) {
                "agree_push_permission"
            } else {
                "disagree_push_permission"
            }
            if (isGranted) startPersistentService()
            LogUtil.log(eventName, emptyMap())
            startSplashSequence()
        }
    }

    private fun checkPermissionAndStart() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            startPersistentService()
            startSplashSequence()
            return
        }
        isWaitingForNotificationPermission = true
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATION)
    }

    private fun startPersistentService() {
        try {
            if (PersistentNotificationService.isRunning) return
            val serviceIntent = Intent(this, PersistentNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            LogUtil.log("persistent_service_start_failed", mapOf("error" to (e.message ?: "")))
        }
    }

    private fun startSplashSequence() {
        if (startupJob?.isActive == true || isNavigating.get()) return
        globalTimeoutJob = lifecycleScope.launch {
            delay(GLOBAL_TIMEOUT_MS)
            determineNavigationPath()
        }
        waitingJob = lifecycleScope.launch { simulateWaitingProgress() }
        beginConsentInitialization()

        startupJob = lifecycleScope.launch {
            try {
                awaitConsentAndPreloadAds()
                waitingJob?.cancelAndJoin()
                waitingJob = null
                if (!isFinishing && !isDestroyed && !isNavigating.get()) {
                    startMainProgressAndOpenAd()
                    preloadNativeAds()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Splash startup sequence failed", e)
                splashAdGate.markAdUnavailable()
                completeProgressAndNavigate()
            }
        }
    }

    private fun beginConsentInitialization() {
        if (hasStartedConsentInitialization) return
        hasStartedConsentInitialization = true
        AdvertisePageMediator.initSplashConsent(this) { success ->
            if (!consentInitialized.isCompleted) {
                consentInitialized.complete(success)
            }
        }
    }

    private suspend fun awaitConsentAndPreloadAds() {
        val consentReady = withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            consentInitialized.await()
        } == true
        if (consentReady && !isFinishing && !isDestroyed) {
            showSplashConsent()
        }

        if (!isFinishing && !isDestroyed && !isNavigating.get()) {
            AdvertisePageMediator.preloadLaunchAds(this)
        }
    }

    private suspend fun showSplashConsent() {
        val formShown = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        try {
            AdvertisePageMediator.showSplashConsent(
                activity = this,
                onWillShowForm = {
                    isShowingSplashConsent = true
                    if (!formShown.isCompleted) {
                        formShown.complete(Unit)
                    }
                },
            ) {
                if (!completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
            val formWasShown = withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
                select<Boolean> {
                    formShown.onAwait { true }
                    completed.onAwait { false }
                }
            }
            if (formWasShown == true) {
                completed.await()
            }
        } finally {
            isShowingSplashConsent = false
        }
    }

    private suspend fun simulateWaitingProgress() {
        while (!isFinishing && !isDestroyed && currentProgress < WAITING_PROGRESS_CAP) {
            updateProgress((currentProgress + 1).coerceAtMost(WAITING_PROGRESS_CAP))
            delay(WAITING_PROGRESS_INTERVAL_MS)
        }
    }

    private fun startMainProgressAndOpenAd() {
        startProgressJob()
        requestLaunchOpenAd()
    }

    private fun preloadNativeAds() {
        lifecycleScope.launch {
            MyAdLoader.loadNative(MyApplication.instance)
        }
    }

    private fun setTrafficSource(source: String?) {
        trafficSource = source
        JSONObject().apply { put("traffic_source", trafficSource) }
            .let { json -> TDAnalytics.setSuperProperties(json) }
    }

    private fun startProgressJob() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            for (progress in (currentProgress + 1)..TOTAL_PROGRESS) {
                if (isNavigating.get() || isFinishing || isDestroyed) return@launch
                updateProgress(progress)
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
            if (splashAdGate.shouldNavigateFromProgress(TOTAL_PROGRESS, elapsedSinceSplashStart())) {
                determineNavigationPath()
            }
        }
    }

    private fun requestLaunchOpenAd() {
        if (hasReturnedFromBackgroundDuringSplash || isNavigating.get()) {
            splashAdGate.markAdUnavailable()
            completeProgressAndNavigate()
            return
        }

        splashAdGate.markAdRequestStarted(elapsedSinceSplashStart())
        openAdJob = lifecycleScope.launch {
            val hasCachedOpenAd = waitForLaunchOpenAd()
            if (!hasCachedOpenAd) {
                splashAdGate.markAdUnavailable()
                completeProgressAndNavigate()
                return@launch
            }

            splashAdGate.markAdLoaded(elapsedSinceSplashStart())
            if (hasReturnedFromBackgroundDuringSplash || !waitUntilForegroundForOpenAd()) {
                splashAdGate.markAdUnavailable()
                completeProgressAndNavigate()
                return@launch
            }

            animateProgressTo100()
            if (hasReturnedFromBackgroundDuringSplash || isNavigating.get()) {
                determineNavigationPath()
                return@launch
            }

            isShowingLaunchOpenAd = true
            val showingAd = AdvertisePageMediator.showCachedLaunchOpenAd(
                activity = this@SplashActivity,
                onContinue = {
                    isShowingLaunchOpenAd = false
                    determineNavigationPath()
                },
            )
            if (showingAd) {
                splashAdGate.markAdDisplayed()
                if (splashAdGate.shouldCancelStartupTimeout()) {
                    globalTimeoutJob?.cancel()
                }
            } else {
                isShowingLaunchOpenAd = false
                splashAdGate.markAdUnavailable()
                completeProgressAndNavigate()
            }
        }
    }

    private suspend fun waitForLaunchOpenAd(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + AD_WAIT_TIMEOUT_MS
        var requestedRetryPreload = false
        while (
            !isFinishing &&
            !isDestroyed &&
            !hasReturnedFromBackgroundDuringSplash &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            if (AdvertisePageMediator.hasCachedLaunchOpenAd()) return true
            if (!requestedRetryPreload && !AdvertisePageMediator.isLaunchOpenAdLoading()) {
                requestedRetryPreload = true
                AdvertisePageMediator.preloadLaunchAds(this)
            }
            delay(OPEN_AD_POLL_INTERVAL_MS)
        }
        return AdvertisePageMediator.hasCachedLaunchOpenAd()
    }

    private suspend fun waitUntilForegroundForOpenAd(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + OPEN_AD_FOREGROUND_WAIT_TIMEOUT_MS
        while (!isFinishing && !isDestroyed && SystemClock.elapsedRealtime() < deadline) {
            if (isForegroundForOpenAd()) return true
            delay(OPEN_AD_POLL_INTERVAL_MS)
        }
        return isForegroundForOpenAd()
    }

    private fun isForegroundForOpenAd(): Boolean {
        return lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private suspend fun animateProgressTo100() {
        progressJob?.cancel()
        progressJob = null
        val startProgress = currentProgress
        if (startProgress >= TOTAL_PROGRESS) return

        val animationStartTime = SystemClock.elapsedRealtime()
        while (!isFinishing && !isDestroyed) {
            val elapsed = SystemClock.elapsedRealtime() - animationStartTime
            val fraction = (elapsed.toFloat() / AD_PROGRESS_ANIMATION_MS).coerceIn(0f, 1f)
            val nextProgress = startProgress + ((TOTAL_PROGRESS - startProgress) * fraction).toInt()
            updateProgress(nextProgress, forceFullProgress = true)
            if (fraction >= 1f) break
            delay(PROGRESS_ANIMATION_FRAME_MS)
        }
        if (!isFinishing && !isDestroyed) {
            updateProgress(TOTAL_PROGRESS, forceFullProgress = true)
        }
    }

    private fun completeProgressAndNavigate() {
        lifecycleScope.launch {
            if (!isNavigating.get()) {
                animateProgressTo100()
            }
            determineNavigationPath()
        }
    }

    private fun updateProgress(progress: Int, forceFullProgress: Boolean = false) {
        val safeProgress = progress.coerceIn(0, TOTAL_PROGRESS)
        currentProgress = if (forceFullProgress) {
            safeProgress
        } else {
            splashAdGate.visibleProgress(safeProgress, elapsedSinceSplashStart())
        }
        binding.progressBar.progress = currentProgress
        binding.tvProgress.text = "$currentProgress%"
    }

    private fun elapsedSinceSplashStart(): Long {
        return (SystemClock.elapsedRealtime() - startElapsedRealtime).coerceAtLeast(0L)
    }

    private fun determineNavigationPath() {
        if (!isNavigating.compareAndSet(false, true)) return

        LogUtil.log("splash_display", mapOf("duration_time" to elapsedSinceSplashStart()))
        AppPrefsUtils.commitBoolean("in_splash", false)
        cancelStartupJobs()
        deferForegroundOpenAdRestore()

        val appOpenFrom = intent.getStringExtra("appOpenFrom")
        if (TextUtils.isEmpty(appOpenFrom)) {
            navigateFromNormalLaunch()
        } else {
            setTrafficSource("app_push")
            LogUtil.log("notification_app_clicked", emptyMap())
            if (!AppPrefsUtils.getBoolean(AppConfig.IS_SET_USER)) {
                toNext()
            } else {
                val bundle = Bundle().apply {
                    putString("appOpenFrom", appOpenFrom)
                }
                when (intent.extras?.getString("next").orEmpty()) {
                    "/nearbyPlaces" -> readyGoThenKill(NearbyPlacesActivity::class.java, bundle)
                    "/phoneNumber" -> readyGoThenKill(SearchPhoneActivity::class.java, bundle)
                    "/setupZone" -> readyGoThenKill(ZoneListActivity::class.java, bundle)
                    "/friendList" -> readyGoThenKill(LocationsListActivity::class.java, bundle)
                    "/zoneAlert" -> readyGoThenKill(ZoneAlertActivity::class.java, bundle)
                    else -> readyGoThenKill(MainActivity::class.java, bundle)
                }
            }
        }
    }

    private fun navigateFromNormalLaunch() {
        setTrafficSource(null)
        if (!AppPrefsUtils.getBoolean(AppConfig.IS_SET_USER)) {
            toNext()
        } else {
            readyGoThenKill(MainActivity::class.java)
        }
    }

    fun toNext() {
        val skipLanguage = AppPrefsUtils.getBoolean("skip_setlanguage", false)
        val skipGuide = AppPrefsUtils.getBoolean("skip_guide", false)
        val skipLogin = AppPrefsUtils.getBoolean("skip_login", false)
        val target = when {
            !skipLanguage -> SetLanguageActivity::class.java
            !skipGuide -> GuideActivity::class.java
            !skipLogin -> LoginActivity::class.java
            else -> MainActivity::class.java
        }
        if (target == MainActivity::class.java) {
            AppPrefsUtils.putBoolean(AppConfig.IS_SET_USER, true)
        }
        readyGoThenKill(target)
    }

    private fun setupTermsClickListeners() {
        binding.tvTerms.safeClick {
            LogUtil.log("check_terms", mapOf("referrer_name" to "splash"))
            openTermsOrPrivacyWebView(AppConfig.TERMS_URL)
        }
        binding.tvPrivacy.safeClick {
            LogUtil.log("check_privacy", mapOf("referrer_name" to "splash"))
            openTermsOrPrivacyWebView(AppConfig.PRIVACY_URL)
        }
    }

    private fun openTermsOrPrivacyWebView(url: String) {
        if (!isNavigating.compareAndSet(false, true)) return
        AppPrefsUtils.commitBoolean("in_splash", false)
        cancelStartupJobs()
        restoreForegroundOpenAdIfNeeded()
        readyGoThenKill(WebViewActivity::class.java, Bundle().apply {
            putString(WebViewActivity.EXTRA_URL, url)
            putString("tag", "splash")
        })
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
    }

    private fun suppressForegroundOpenAdDuringSplash() {
        if (foregroundOpenAdDisabledForSplash) return
        foregroundOpenAdPreviouslyEnabled = AdvertisePageMediator.isForegroundOpenAdEnabled()
        foregroundOpenAdDisabledForSplash = true
        AdvertisePageMediator.setForegroundOpenAdEnabled(false)
    }

    private fun suppressForegroundOpenAdAfterSplashBackground() {
        AdvertisePageMediator.suppressNextForegroundOpenAd()
        suppressForegroundOpenAdDuringSplash()
    }

    private fun restoreForegroundOpenAdIfNeeded() {
        if (!foregroundOpenAdDisabledForSplash) return
        foregroundOpenAdDisabledForSplash = false
        AdvertisePageMediator.setForegroundOpenAdEnabled(foregroundOpenAdPreviouslyEnabled)
    }

    private fun deferForegroundOpenAdRestore() {
        if (!foregroundOpenAdDisabledForSplash || foregroundOpenAdRestoreDeferred) return

        foregroundOpenAdDisabledForSplash = false
        foregroundOpenAdRestoreDeferred = true
        val previouslyEnabled = foregroundOpenAdPreviouslyEnabled
        val sourceActivity = this
        val application = application
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity === sourceActivity) return
                application.unregisterActivityLifecycleCallbacks(this)
                Handler(Looper.getMainLooper()).post {
                    AdvertisePageMediator.setForegroundOpenAdEnabled(previouslyEnabled)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    private fun cancelStartupJobs() {
        progressJob?.cancel()
        waitingJob?.cancel()
        startupJob?.cancel()
        openAdJob?.cancel()
        globalTimeoutJob?.cancel()
        progressJob = null
        waitingJob = null
        startupJob = null
        openAdJob = null
        globalTimeoutJob = null
    }
}
