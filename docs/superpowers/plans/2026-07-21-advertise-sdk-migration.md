# Advertise SDK Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove FindMyPhone-owned App Open and interstitial implementations, migrate splash consent and full-screen playback to `com.pdffox:advertise:1.0.10`, and preserve the existing native-ad implementation.

**Architecture:** `MyApplication` initializes the SDK once. A focused `AdvertisePageMediator` adapts the SDK consent, preload, App Open, and interstitial APIs while guarding lifecycle and callback behavior. Existing activity placement calls, navigation continuations, native-ad loading, and native-ad UI remain application-owned.

**Tech Stack:** Android, Kotlin, Gradle Kotlin DSL, JUnit 4, Google Mobile Ads native ads, `com.pdffox:advertise:1.0.10`.

**Repository note:** The SDK dependency, repository, BuildConfig values, manifest placeholder, policy resource, configuration factory, and configuration test already exist. `D:\work\majia\FindMyPhone\.git` is empty, so commit steps must be deferred until Git metadata is restored.

---

### Task 1: Add the SDK playback boundary

**Files:**
- Create: `app/src/main/java/com/findmyphone/gpstracker/advertise/CallbackOnce.kt`
- Create: `app/src/main/java/com/findmyphone/gpstracker/advertise/InterstitialRequestGate.kt`
- Create: `app/src/main/java/com/findmyphone/gpstracker/advertise/AdvertisePageMediator.kt`
- Test: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertisePageMediatorTest.kt`

- [ ] **Step 1: Write the failing pure-behavior tests**

Create `AdvertisePageMediatorTest.kt` with tests for callback-once, blank keys, duplicate completion, presenter exceptions, unavailable activities, request gating, cache expiry, and the policy-aware SDK entry points:

```kotlin
package com.findmyphone.gpstracker.advertise

import com.pdffox.adv.AdvertiseSdk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertisePageMediatorTest {
    @After fun resetSdkState() { AdvertiseSdk.isAppOpenAdEnabled = true }

    @Test fun callback_runs_once() {
        var count = 0
        CallbackOnce { count++ }.also { it.invoke(); it.invoke() }
        assertEquals(1, count)
    }

    @Test fun blank_key_continues_without_presenting() {
        var closed = 0
        var shown = 0
        showInterstitialPlayback(" ", { true }, { _, _ -> shown++ }, { closed++ }, {})
        assertEquals(0, shown)
        assertEquals(1, closed)
    }

    @Test fun duplicate_completion_continues_once() {
        var closed = 0
        showInterstitialPlayback("area", { true }, { _, done -> done(); done() }, { closed++ }, {})
        assertEquals(1, closed)
    }

    @Test fun presenter_exception_continues_once() {
        var closed = 0
        showInterstitialPlayback("area", { true }, { _, _ -> error("SDK") }, { closed++ }, {})
        assertEquals(1, closed)
    }

    @Test fun unavailable_request_does_nothing() {
        var closed = 0
        var shown = 0
        showInterstitialPlayback("area", { false }, { _, _ -> shown++ }, { closed++ }, {})
        assertEquals(0, shown)
        assertEquals(0, closed)
    }

    @Test fun request_gate_rejects_reentry_until_release() {
        val gate = InterstitialRequestGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test fun cached_open_ad_expires_at_the_cache_boundary() {
        assertTrue(AdvertisePageMediator.isCachedOpenAdFresh(1_000L, 1_999L, 1_000L))
        assertFalse(AdvertisePageMediator.isCachedOpenAdFresh(1_000L, 2_000L, 1_000L))
    }

    @Test fun source_uses_policy_aware_sdk_playback() {
        val source = File("src/main/java/com/findmyphone/gpstracker/advertise/AdvertisePageMediator.kt").readText()
        assertTrue(source.contains("ShowOpenAd.showOpenAd("))
        assertFalse(source.contains("showAdmobOpenAdFromCache("))
        assertTrue(source.contains("AdvertiseSdk.showInterstitialAd(activity, areaKey)"))
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertisePageMediatorTest" --no-daemon --console=plain
```

Expected: Kotlin compilation fails because the mediator and helpers do not exist.

- [ ] **Step 3: Implement the callback and request guards**

```kotlin
internal class CallbackOnce(private val callback: () -> Unit) {
    private val invoked = java.util.concurrent.atomic.AtomicBoolean(false)
    fun invoke() { if (invoked.compareAndSet(false, true)) callback() }
}

internal class InterstitialRequestGate {
    private val inProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    fun tryAcquire(): Boolean = inProgress.compareAndSet(false, true)
    fun release() { inProgress.set(false) }
}
```

- [ ] **Step 4: Implement the mediator using PhoneTracker's 1.0.10 APIs**

Implement these exact public operations in `AdvertisePageMediator`: `initSplashConsent`, `showSplashConsent`, `preloadLaunchAds`, `hasCachedLaunchOpenAd`, `isLaunchOpenAdLoading`, `showCachedLaunchOpenAd`, `showInterstitial`, `suppressNextForegroundOpenAd`, `setForegroundOpenAdEnabled`, and `isForegroundOpenAdEnabled`.

Use:

```kotlin
AdvertiseSdk.initConsent(activity) { success -> onComplete(success) }
AdvertiseSdk.showSplashConsent(activity) { onComplete() }
AdvertiseSdk.preloadOpen(context)
AdvertiseSdk.preloadInterstitial(context)
AdvertiseSdk.showInterstitialAd(activity, areaKey) { onComplete() }
ShowOpenAd.showOpenAd(
    activity = activity,
    areaKey = AreaKey.openPageAdv,
    onCloseListener = ShowOpenAd.OpenAdCloseListener { continueOnce() },
    onLoadedListener = ShowOpenAd.OpenAdLoadedListener { },
    onPaidListener = ShowOpenAd.OpenAdPaidListener { _ -> },
)
```

Read SDK cache state through `com.pdffox.adv.adv.AdPool.admobOpenPool`, `AdPool.admobOpenIsLoadingNum`, and `AdConfig.adload_cache_time_open`. Wrap every SDK boundary in `runCatching`, log failures, and call the continuation once on playback failure.

Implement the pure interstitial function exactly as:

```kotlin
internal fun showInterstitialPlayback(
    areaKey: String?,
    isAvailable: () -> Boolean,
    show: (String, () -> Unit) -> Unit,
    onClosed: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    if (!isAvailable()) return
    val closeOnce = CallbackOnce { if (isAvailable()) onClosed() }
    if (areaKey.isNullOrBlank()) { closeOnce.invoke(); return }
    runCatching { show(areaKey) { closeOnce.invoke() } }
        .onFailure { throwable -> onFailure(throwable); closeOnce.invoke() }
}
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: all mediator tests pass.

### Task 2: Initialize the SDK and migrate splash consent

**Files:**
- Modify: `app/src/main/java/com/findmyphone/gpstracker/advertise/AdvertiseSdkConfigFactory.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/base/MyApplication.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt`
- Test: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertiseInitializationSourceTest.kt`

- [ ] **Step 1: Write the failing source-contract test**

Assert that `MyApplication.kt` contains `AdvertiseSdk.init(` and `AdvertiseSdkConfigFactory.create(this@MyApplication)`, and that `SplashActivity.kt` contains `AdvertisePageMediator.initSplashConsent(` plus `AdvertisePageMediator.showSplashConsent(` while containing no `UmpConsentManager`.

- [ ] **Step 2: Run the test and verify RED**

Run the new test class. Expected: assertions fail because the SDK is not initialized and Splash still owns UMP.

- [ ] **Step 3: Add the context factory overload and initialize once**

```kotlin
fun create(context: android.content.Context): AdvertiseSdkConfig =
    create(context.packageName, BuildConfig.DEBUG)
```

Call this before `initSDKAsync(instance)`:

```kotlin
private fun initializeAdvertiseSdk() {
    runCatching {
        kotlinx.coroutines.runBlocking {
            AdvertiseSdk.init(
                context = this@MyApplication,
                isTest = BuildConfig.DEBUG,
                sdkConfig = AdvertiseSdkConfigFactory.create(this@MyApplication),
            )
        }
    }.onFailure { Log.e("MyApplication", "AdvertiseSdk initialization failed", it) }
}
```

- [ ] **Step 4: Replace the Splash UMP flow**

Use a `CompletableDeferred<Boolean>` completed by `initSplashConsent`. Wait at most 6 seconds, call `showSplashConsent` when initialization succeeds, and wait for the form completion before preloading. Preserve notification permission handling, native preload, progress animation, global timeout, and destination selection.

The consent sequence must be:

```kotlin
AdvertisePageMediator.initSplashConsent(this) { success ->
    if (!consentInitialized.isCompleted) consentInitialized.complete(success)
}
val consentReady = withTimeoutOrNull(CONSENT_TIMEOUT_MS) { consentInitialized.await() } == true
if (consentReady && !isFinishing && !isDestroyed) {
    val completed = CompletableDeferred<Unit>()
    AdvertisePageMediator.showSplashConsent(this) {
        if (!completed.isCompleted) completed.complete(Unit)
    }
    withTimeoutOrNull(CONSENT_TIMEOUT_MS) { completed.await() }
}
```

- [ ] **Step 5: Run the focused test and verify GREEN**

Expected: the initialization and consent source contract passes.

### Task 3: Route interstitial placements through the SDK

**Files:**
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ad/AdvActivity.kt`
- Delete: `app/src/main/java/com/findmyphone/gpstracker/ad/InterstitialAdHelper.kt`
- Test: `app/src/test/java/com/findmyphone/gpstracker/advertise/InterstitialMigrationSourceTest.kt`

- [ ] **Step 1: Write the failing migration test**

Assert that `AdvActivity.kt` contains `AdvertisePageMediator.showInterstitial(this, areaKey)`, uses `InterstitialRequestGate`, and contains none of `InterstitialAdHelper`, `MyAdLoader.loadInter`, or `AppConfig.isShowInterstitialAd`. Assert that `InterstitialAdHelper.kt` does not exist.

- [ ] **Step 2: Run the test and verify RED**

Expected: it fails against the current custom helper and preload calls.

- [ ] **Step 3: Replace `showInterstitialAdWithStats` without changing call sites**

```kotlin
private val interstitialRequestGate = InterstitialRequestGate()

fun showInterstitialAdWithStats(areaKey: String, onClosed: () -> Unit) {
    if (isFinishing || isDestroyed || !interstitialRequestGate.tryAcquire()) return
    val closeOnce = CallbackOnce {
        lifecycleScope.launch {
            delay(100)
            try { onClosed() } finally { interstitialRequestGate.release() }
        }
    }
    AdvertisePageMediator.showInterstitial(this, areaKey) { closeOnce.invoke() }
}
```

Remove custom helper registration/unregistration, interstitial preload, display flags, and interstitial-specific progress handling. Keep native idle display and native pool cleanup.

- [ ] **Step 4: Delete the helper and verify GREEN**

Run the focused migration test. Expected: PASS without changing any Activity placement call or `AreaKey`.

### Task 4: Route cold-start and foreground App Open through the SDK

**Files:**
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt`
- Delete: `app/src/main/java/com/findmyphone/gpstracker/ad/ShowOpenAd.kt`
- Delete: `app/src/main/java/com/findmyphone/gpstracker/ad/AppOpenHelper.kt`
- Test: `app/src/test/java/com/findmyphone/gpstracker/advertise/OpenAdMigrationSourceTest.kt`

- [ ] **Step 1: Write the failing App Open contract test**

Assert that Splash calls `preloadLaunchAds`, `hasCachedLaunchOpenAd`, `isLaunchOpenAdLoading`, and `showCachedLaunchOpenAd`; contains no project `ShowOpenAd`, `MyAdLoader.loadOpen`, `SdkInitUtils.awaitAdMobInitialization`, or `UmpConsentManager`; and that both custom App Open files are absent.

- [ ] **Step 2: Run the test and verify RED**

Expected: it reports every current custom App Open dependency.

- [ ] **Step 3: Replace preload, cache wait, and playback**

After consent, call `AdvertisePageMediator.preloadLaunchAds(this)`. Poll cache at 100 ms intervals for at most 8 seconds. Once fresh cache exists and the Activity is resumed, call:

```kotlin
val showing = AdvertisePageMediator.showCachedLaunchOpenAd(this) {
    isShowingLaunchOpenAd = false
    determineNavigationPath()
}
if (!showing) completeProgressAndNavigate()
```

Preserve `SplashAdGate`, the 20-second global timeout, progress-to-100 animation, notification navigation, and `MyAdLoader.loadNative`.

- [ ] **Step 4: Prevent SDK foreground App Open from competing with Splash**

Capture `isForegroundOpenAdEnabled()`, disable foreground open while Splash is active, call `suppressNextForegroundOpenAd()` if Splash backgrounds outside permission/consent/ad display, and restore the captured value exactly once during navigation or destruction.

- [ ] **Step 5: Delete project App Open helpers and verify GREEN**

Run the focused test. Expected: PASS and no application-owned App Open playback remains.

### Task 5: Remove full-screen branches from shared native infrastructure

**Files:**
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ad/pool/AdPool.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ad/pool/MyAdLoader.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ad/pool/AdChecker.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ad/pool/AdConfig.kt`
- Delete: `app/src/main/java/com/findmyphone/gpstracker/ad/AdvIDs.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/config/AppConfig.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/utils/SdkInitUtils.kt`
- Test: `app/src/test/java/com/findmyphone/gpstracker/advertise/FullScreenAdRemovalSourceTest.kt`

- [ ] **Step 1: Write the failing removal test**

Scan `src/main/java` and fail on application imports of Google `AppOpenAd` or `InterstitialAd`, or identifiers `admobOpenPool`, `admobInterPool`, `loadAdmobOpen`, `loadAdmobInter`, `loadOpen(`, `loadInter(`, `isShowOpenAd`, `isShowInterstitialAd`, `AppOpenHelper`, and `InterstitialAdHelper`. Also assert that `ShowNativeAd`, `admobNativePool`, `loadNative`, and `loadNativeAd` still exist.

- [ ] **Step 2: Run the test and verify RED**

Expected: it reports current full-screen classes, pools, loaders, and state flags.

- [ ] **Step 3: Reduce shared classes to their native responsibilities**

In `AdPool`, retain only `admobNativePool`, `putAdmobNative`, `getAdmobNative`, the generic best-ad helper, native paid/click logging, and native destruction in `clearAllPools`.

In `MyAdLoader`, retain native ID parsing/rotation, `isNetworkAvailableQuick`, `loadNative`, `fillAdmobNative`, `loadAdsConcurrently`, `loadNativeAd`, native logging, and `cleanup`. Remove open/inter enums, counters, pools, fill/load methods, and imports.

In `AdChecker`, retain only:

```kotlin
fun checkExpiredNative() {
    AdPool.admobNativePool.entries.removeIf { isExpired(it.value) }
}
private fun isExpired(loadTime: Long): Boolean =
    System.currentTimeMillis() - loadTime > AdConfig.adload_cache_time
```

In `AdConfig`, retain native cache/retry/pool values, `LOAD_TIME_OPEN_APP` and other keys still used by native preload, `adload_trigger_timing_native`, native JSON parsing, and `canLoadNative`. Delete all open/inter values and functions.

- [ ] **Step 4: Remove obsolete initialization and IDs**

Delete `AdvIDs.kt`. From `SdkInitUtils`, remove `AppOpenHelper`, `MobileAds`, `isAdMobInitialized`, `awaitAdMobInitialization`, manual AdMob init methods, and Remote Config reads for `admob_interset`/`admob_open`; retain analytics, FCM, Remote Config, native ID updates, and native config initialization. Remove the two full-screen state booleans from `AppConfig`.

- [ ] **Step 5: Run the removal test and compile Kotlin**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.FullScreenAdRemovalSourceTest" --no-daemon --console=plain
.\gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain
```

Expected: source audit passes and Kotlin compilation succeeds with native code intact.

### Task 6: Verify the complete migration

**Files:**
- Verify all changed files

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 2: Build the Debug APK**

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Audit removed and retained references**

```powershell
rg -n "com\.google\.android\.gms\.ads\.(appopen|interstitial)|InterstitialAdHelper|com\.findmyphone\.gpstracker\.ad\.ShowOpenAd|AppOpenHelper|admob(Open|Inter)Pool|loadAdmob(Open|Inter)" app/src/main app/src/test
rg -n "ShowNativeAd|NativeAdActivity|admobNativePool|loadNativeAd|loadNative" app/src/main/java
```

Expected: the first command has no application-owned full-screen matches; the second reports the retained native implementation.

- [ ] **Step 4: Record the Git limitation**

Do not initialize or replace `.git`. Report that commits could not be made because the existing `.git` directory contains no repository metadata.
