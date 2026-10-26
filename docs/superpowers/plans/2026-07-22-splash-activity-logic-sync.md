# SplashActivity Logic Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace FindMyPhone's Splash startup state machine with the LocShare implementation while preserving all FindMyPhone visuals and project-specific destinations.

**Architecture:** Port the LocShare `SplashActivity` behavior into FindMyPhone through an explicit package/type adaptation map. Keep `SplashAdGate` as the isolated timing policy, add LocShare's missing timeout-cancellation behavior, and use source-contract plus unit tests to prove behavior and protected-file boundaries.

**Tech Stack:** Android, Kotlin coroutines, Gradle Kotlin DSL, JUnit 4 source-contract tests, `com.pdffox:advertise:1.0.10`

**Repository constraint:** `D:\work\majia\FindMyPhone\.git` is empty. Do not initialize it; replace commit steps with explicit verification checkpoints.

---

### Task 1: Synchronize SplashAdGate Timeout Behavior

**Files:**
- Create: `app/src/test/java/com/findmyphone/gpstracker/utils/SplashAdGateTest.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/utils/SplashAdGate.kt`

- [ ] **Step 1: Add the LocShare gate tests**

Create the test file with the FindMyPhone package and the complete LocShare behavior matrix:

```kotlin
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
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.utils.SplashAdGateTest" --offline --no-daemon --console=plain
```

Expected: test compilation fails because `shouldCancelStartupTimeout()` does not exist.

- [ ] **Step 3: Add the minimal gate behavior**

Add this method immediately before `visibleProgress`:

```kotlin
fun shouldCancelStartupTimeout(): Boolean = adDisplayed
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command again. Expected: 10 tests pass and `BUILD SUCCESSFUL`.

### Task 2: Lock The Splash Logic And Visual Boundary

**Files:**
- Create: `app/src/test/java/com/findmyphone/gpstracker/ui/SplashActivityLogicSyncSourceTest.kt`
- Inspect: `app/src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt`
- Protect: `app/src/main/res/layout/activity_splash.xml`
- Protect: `app/src/main/res/mipmap-xxxhdpi/splash_bg.png`
- Protect: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Write the failing source-contract test**

```kotlin
package com.findmyphone.gpstracker.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class SplashActivityLogicSyncSourceTest {
    @Test
    fun splash_uses_the_locshare_startup_state_machine() {
        val source = File("src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt").readText()
        listOf(
            "private val consentInitialized = CompletableDeferred<Boolean>()",
            "private var startupJob: Job? = null",
            "private var openAdJob: Job? = null",
            "private var hasStartedConsentInitialization = false",
            "AppPrefsUtils.getBoolean(\"is_First\", true)",
            "my_first_open_time",
            "getDetailCurrentTime(System.currentTimeMillis())",
            "if (startupJob?.isActive == true || isNavigating.get()) return",
            "beginConsentInitialization()",
            "awaitConsentAndPreloadAds()",
            "showSplashConsent()",
            "startMainProgressAndOpenAd()",
            "startProgressJob()",
            "openAdJob = lifecycleScope.launch",
            "splashAdGate.shouldCancelStartupTimeout()",
            "suppressForegroundOpenAdAfterSplashBackground()",
            "LogUtil.log(\"splash_display\", mapOf(\"duration_time\" to elapsedSinceSplashStart()))",
        ).forEach { marker -> assertTrue("Missing $marker", source.contains(marker)) }

        listOf(
            "canShowSplashAd",
            "runConsentFlowWithTimeout()",
            "simulateProgressAndNavigate()",
            "logSplashDuration()",
            "coroutineContext.isActive",
        ).forEach { marker -> assertFalse("Unexpected $marker", source.contains(marker)) }
    }

    @Test
    fun splash_visual_and_manifest_files_are_unchanged() {
        assertHash(
            "src/main/res/layout/activity_splash.xml",
            "2928D99363E20231AC45CC1FBF0E81FEB4DCDC4E88DF3246E2C51E2D88502DB3",
        )
        assertHash(
            "src/main/res/mipmap-xxxhdpi/splash_bg.png",
            "109B7D3A8A9C860D59D7A05483FC1336C8CED94B72D60C96D46BD750D1631ECB",
        )
        assertHash(
            "src/main/AndroidManifest.xml",
            "697384FF2CF46A83B8E4B0BF3043775F81FE1D6B24F29E64219563150268729F",
        )
    }

    private fun assertHash(path: String, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256").digest(File(path).readBytes())
        val actual = digest.joinToString("") { byte -> "%02X".format(byte) }
        assertTrue("Unexpected hash for $path", actual == expected)
    }
}
```

- [ ] **Step 2: Run the focused source-contract test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.ui.SplashActivityLogicSyncSourceTest" --offline --no-daemon --console=plain
```

Expected: the state-machine test fails on missing LocShare markers while the protected-file hash test passes.

### Task 3: Port The LocShare SplashActivity State Machine

**Files:**
- Source: `D:/work/LocShare/app/src/main/java/com/locshare/locate/ui/activity/SplashActivity.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt`

- [ ] **Step 1: Replace the FindMyPhone activity logic with the LocShare source**

Use the complete LocShare file as the behavioral source and apply this exhaustive adaptation map:

```text
package com.locshare.locate.ui.activity
  -> package com.findmyphone.gpstracker.ui

com.locshare.locate.R
  -> com.findmyphone.gpstracker.R
com.locshare.locate.ad.pool.MyAdLoader
  -> com.findmyphone.gpstracker.ad.pool.MyAdLoader
com.locshare.locate.advertise.AdvertisePageMediator
  -> com.findmyphone.gpstracker.advertise.AdvertisePageMediator
com.locshare.locate.base.MyApp
  -> com.findmyphone.gpstracker.base.MyApplication
com.locshare.locate.base.activity.BaseViewActivity
  -> com.findmyphone.gpstracker.base.activity.BaseViewActivity
com.locshare.locate.command.REQUEST_CODE_NOTIFICATION
  -> com.findmyphone.gpstracker.base.command.REQUEST_CODE_NOTIFICATION
com.locshare.locate.command.getDetailCurrentTime
  -> com.findmyphone.gpstracker.base.command.getDetailCurrentTime
com.locshare.locate.command.readyGoThenKill
  -> com.findmyphone.gpstracker.base.command.readyGoThenKill
com.locshare.locate.command.safeClick
  -> com.findmyphone.gpstracker.base.command.safeClick
com.locshare.locate.config.AppConfig
  -> com.findmyphone.gpstracker.config.AppConfig
com.locshare.locate.databinding.ActivitySplashBinding
  -> com.findmyphone.gpstracker.databinding.ActivitySplashBinding
com.locshare.locate.services.PersistentNotificationService
  -> com.findmyphone.gpstracker.services.PersistentNotificationService
com.locshare.locate.util.AppPrefsUtils
  -> com.findmyphone.gpstracker.utils.AppPrefsUtils
com.locshare.locate.util.LogUtil
  -> com.findmyphone.gpstracker.utils.LogUtil
MyApp.instance
  -> MyApplication.instance
```

Add the existing gate import because FindMyPhone keeps the type in `utils`:

```kotlin
import com.findmyphone.gpstracker.utils.SplashAdGate
```

Keep FindMyPhone destination activity simple names unchanged because they live in the same `com.findmyphone.gpstracker.ui` package. Do not edit the layout, images, Manifest, `AdvertisePageMediator`, or native-ad loader.

- [ ] **Step 2: Verify the adapted source differs from LocShare only at project boundaries**

Audit these required FindMyPhone references:

```powershell
rg -n "package com.findmyphone.gpstracker.ui|MyApplication.instance|com.findmyphone.gpstracker.utils.SplashAdGate|getDetailCurrentTime" app/src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt
```

Audit that no LocShare package reference remains:

```powershell
rg -n "com\.locshare\.locate|MyApp\.instance" app/src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt
```

Expected: required FindMyPhone references are present; the second command has no matches.

- [ ] **Step 3: Run the Splash tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.ui.SplashActivityLogicSyncSourceTest" --tests "com.findmyphone.gpstracker.utils.SplashAdGateTest" --offline --no-daemon --console=plain
```

Expected: 12 tests pass and `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run existing advertise Splash contracts**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseInitializationSourceTest" --tests "com.findmyphone.gpstracker.advertise.OpenAdMigrationSourceTest" --offline --no-daemon --console=plain
```

Expected: both existing contracts pass.

### Task 4: Verify The Complete Logic Sync

**Files:**
- Verify all modified source and test files
- Verify protected layout, image, and Manifest files

- [ ] **Step 1: Run all unit tests from a fresh execution**

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --offline --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL` with zero test failures.

- [ ] **Step 2: Compile Debug Kotlin**

```powershell
.\gradlew.bat :app:compileDebugKotlin --offline --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Recompute protected-file hashes**

```powershell
Get-FileHash `
  app\src\main\res\layout\activity_splash.xml, `
  app\src\main\res\mipmap-xxxhdpi\splash_bg.png, `
  app\src\main\AndroidManifest.xml `
  -Algorithm SHA256 | Format-List Path,Hash
```

Expected exact hashes:

```text
activity_splash.xml  2928D99363E20231AC45CC1FBF0E81FEB4DCDC4E88DF3246E2C51E2D88502DB3
splash_bg.png        109B7D3A8A9C860D59D7A05483FC1336C8CED94B72D60C96D46BD750D1631ECB
AndroidManifest.xml  697384FF2CF46A83B8E4B0BF3043775F81FE1D6B24F29E64219563150268729F
```

- [ ] **Step 4: Audit ownership boundaries**

Confirm `SplashActivity.kt` contains LocShare state-machine markers, contains no `com.locshare.locate`, and the only production files changed are `SplashActivity.kt` and `SplashAdGate.kt`. Record that no commit was created because repository metadata is absent.
