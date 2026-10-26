# Advertise SDK Initialization And Build Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `com.pdffox:advertise:1.0.10` the single initializer for Firebase, Remote Config, Thinking, Singular, and full-screen ads while preserving FindMyPhone-specific values and native ads.

**Architecture:** Gradle exposes all SDK settings through property-driven BuildConfig fields. `AdvertiseSdkConfigFactory` maps those fields into the SDK, `MyApplication` initializes the SDK before other application work, and a focused synchronizer copies SDK Remote Config values into the preferences consumed by existing FindMyPhone services. Native policy consumers use `AdvertiseSdk.isNature` as the attribution source.

**Tech Stack:** Android, Kotlin, Gradle Kotlin DSL, JUnit 4 source-contract tests, `com.pdffox:advertise:1.0.10`.

**Repository constraint:** `D:\work\majia\FindMyPhone\.git` has no repository metadata. Do not initialize it. Commit steps are replaced with explicit file and verification checkpoints.

---

### Task 1: Synchronize SDK BuildConfig fields

**Files:**
- Create: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertiseBuildConfigSourceTest.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Write the failing build configuration contract**

```kotlin
package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseBuildConfigSourceTest {
    @Test
    fun build_exposes_property_driven_advertise_sdk_configuration() {
        val source = File("build.gradle.kts").readText()
        listOf(
            "fun advertiseStringProperty(",
            "fun advertiseBooleanProperty(",
            "fun String.asBuildConfigString()",
            "ADV_REMOTE_CONFIG_ENABLED",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
            "ADV_THINKING_ENABLED",
            "ADV_THINKING_APP_KEY",
            "ADV_THINKING_SERVER_URL",
            "ADV_SINGULAR_ENABLED",
            "ADV_SINGULAR_API_KEY",
            "ADV_SINGULAR_SECRET",
            "ADV_ADMOB_NATIVE_ID",
            "ADV_ADMOB_REWARDED_ID",
            "ADV_FACEBOOK_ENABLED",
            "ADV_TIKTOK_ENABLED",
            "ADV_SAFE_ENABLED",
            "ADV_PLAY_INTEGRITY_ENABLED",
            "manifestPlaceholders[\"advAdmobAppId\"] = advertiseAdmobAppId",
            "buildConfigField(\"String\", \"ADV_DEFAULT_TOPIC\", \"\\\"all\\\"\")",
            "buildConfigField(\"String\", \"ADV_DEFAULT_TOPIC\", \"\\\"debug-all\\\"\")",
            "implementation(\"com.pdffox:advertise:1.0.10\")",
        ).forEach { expected -> assertTrue("Missing $expected", source.contains(expected)) }

        assertTrue(source.contains("ca-app-pub-3643549411810853~3318290879"))
        assertFalse(source.contains("ca-app-pub-3615322193850391~9006916651"))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseBuildConfigSourceTest" --offline --no-daemon --console=plain
```

Expected: FAIL because the property helpers and most `ADV_*` fields do not exist.

- [ ] **Step 3: Add LocShare-style property helpers before `android {}`**

```kotlin
fun advertiseStringProperty(
    propertyName: String,
    environmentName: String,
    defaultValue: String = "",
): String = localProperties.getProperty(propertyName)
    ?: providers.gradleProperty(propertyName).orNull
    ?: providers.environmentVariable(environmentName).orNull
    ?: defaultValue

fun advertiseBooleanProperty(
    propertyName: String,
    environmentName: String,
    defaultValue: Boolean,
): Boolean = advertiseStringProperty(propertyName, environmentName, defaultValue.toString())
    .equals("true", ignoreCase = true)

fun advertiseLongProperty(
    propertyName: String,
    environmentName: String,
    defaultValue: Long,
): Long = advertiseStringProperty(propertyName, environmentName, defaultValue.toString())
    .toLongOrNull() ?: defaultValue

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val advertiseAdmobAppId = advertiseStringProperty(
    "adv.admob.appId",
    "ADV_ADMOB_APP_ID",
    "ca-app-pub-3643549411810853~3318290879",
)
val advertiseFacebookAppId = advertiseStringProperty("adv.facebook.appId", "ADV_FACEBOOK_APP_ID")
val advertiseFacebookClientToken = advertiseStringProperty(
    "adv.facebook.clientToken",
    "ADV_FACEBOOK_CLIENT_TOKEN",
)
```

- [ ] **Step 4: Replace the hard-coded SDK fields in `defaultConfig`**

Define `advertiseStringField`, `advertiseBooleanField`, and `advertiseLongField` locally in `defaultConfig`, then add these exact values:

```kotlin
fun advertiseStringField(name: String, property: String, environment: String, default: String = "") {
    buildConfigField("String", name, advertiseStringProperty(property, environment, default).asBuildConfigString())
}
fun advertiseBooleanField(name: String, property: String, environment: String, default: Boolean) {
    buildConfigField("boolean", name, advertiseBooleanProperty(property, environment, default).toString())
}
fun advertiseLongField(name: String, property: String, environment: String, default: Long) {
    buildConfigField("long", name, "${advertiseLongProperty(property, environment, default)}L")
}

advertiseBooleanField("ADV_REMOTE_CONFIG_ENABLED", "adv.remoteConfig.enabled", "ADV_REMOTE_CONFIG_ENABLED", true)
advertiseStringField("ADV_REMOTE_CONFIG_ENCRYPTION_KEY", "adv.remoteConfig.encryptionKey", "ADV_REMOTE_CONFIG_ENCRYPTION_KEY")
advertiseStringField("ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID", "adv.remoteConfig.encryptionKeyId", "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID")
advertiseBooleanField("ADV_THINKING_ENABLED", "adv.thinking.enabled", "ADV_THINKING_ENABLED", true)
advertiseStringField("ADV_THINKING_APP_KEY", "adv.thinking.appKey", "ADV_THINKING_APP_KEY", "67056903b4b546baa956431440a054ea")
advertiseStringField("ADV_THINKING_SERVER_URL", "adv.thinking.serverUrl", "ADV_THINKING_SERVER_URL", "https://mar2.top")
advertiseBooleanField("ADV_SINGULAR_ENABLED", "adv.singular.enabled", "ADV_SINGULAR_ENABLED", true)
advertiseStringField("ADV_SINGULAR_API_KEY", "adv.singular.apiKey", "ADV_SINGULAR_API_KEY", "mar2game_f7b9272a")
advertiseStringField("ADV_SINGULAR_SECRET", "adv.singular.secret", "ADV_SINGULAR_SECRET", "72b3df2ee5d0a64a6c404ce01937c3d6")
advertiseStringField("ADV_ADMOB_APP_ID", "adv.admob.appId", "ADV_ADMOB_APP_ID", "ca-app-pub-3643549411810853~3318290879")
advertiseStringField("ADV_ADMOB_INTERSTITIAL_ID", "adv.admob.interstitialId", "ADV_ADMOB_INTERSTITIAL_ID", "ca-app-pub-3643549411810853/7121804517")
advertiseStringField("ADV_ADMOB_NATIVE_ID", "adv.admob.nativeId", "ADV_ADMOB_NATIVE_ID")
advertiseStringField("ADV_ADMOB_OPEN_ID", "adv.admob.openId", "ADV_ADMOB_OPEN_ID", "ca-app-pub-3643549411810853/4615599677")
advertiseStringField("ADV_ADMOB_REWARDED_ID", "adv.admob.rewardedId", "ADV_ADMOB_REWARDED_ID")
advertiseBooleanField("ADV_SAFE_ENABLED", "adv.safe.enabled", "ADV_SAFE_ENABLED", false)
advertiseBooleanField("ADV_PLAY_INTEGRITY_ENABLED", "adv.playIntegrity.enabled", "ADV_PLAY_INTEGRITY_ENABLED", false)
advertiseLongField("ADV_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "adv.playIntegrity.cloudProjectNumber", "ADV_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", 0L)
advertiseBooleanField("ADV_FACEBOOK_ENABLED", "adv.facebook.enabled", "ADV_FACEBOOK_ENABLED", false)
advertiseStringField("ADV_FACEBOOK_APP_ID", "adv.facebook.appId", "ADV_FACEBOOK_APP_ID")
advertiseStringField("ADV_FACEBOOK_CLIENT_TOKEN", "adv.facebook.clientToken", "ADV_FACEBOOK_CLIENT_TOKEN")
advertiseBooleanField("ADV_TIKTOK_ENABLED", "adv.tiktok.enabled", "ADV_TIKTOK_ENABLED", false)
advertiseStringField("ADV_TIKTOK_ACCESS_TOKEN", "adv.tiktok.accessToken", "ADV_TIKTOK_ACCESS_TOKEN")
advertiseStringField("ADV_TIKTOK_TT_APP_ID", "adv.tiktok.ttAppId", "ADV_TIKTOK_TT_APP_ID")
manifestPlaceholders["advAdmobAppId"] = advertiseAdmobAppId
manifestPlaceholders["advFacebookAppId"] = advertiseFacebookAppId
manifestPlaceholders["advFacebookClientToken"] = advertiseFacebookClientToken
```

- [ ] **Step 5: Add variant fields and remove debug ad-ID overrides**

```kotlin
release {
    buildConfigField(
        "boolean",
        "ADV_ADMOB_ENABLED",
        advertiseBooleanProperty("adv.admob.enabled", "ADV_ADMOB_ENABLED", true).toString(),
    )
    buildConfigField("String", "ADV_DEFAULT_TOPIC", "\"all\"")
}
debug {
    buildConfigField(
        "boolean",
        "ADV_ADMOB_ENABLED",
        advertiseBooleanProperty("adv.admob.debugEnabled", "ADV_ADMOB_DEBUG_ENABLED", true).toString(),
    )
    buildConfigField("String", "ADV_DEFAULT_TOPIC", "\"debug-all\"")
}
```

The SDK receives `isTest = BuildConfig.DEBUG` and selects official test IDs internally, so remove the two current debug-only App Open/interstitial BuildConfig overrides.

- [ ] **Step 6: Run the focused test and generate BuildConfig**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseBuildConfigSourceTest" :app:generateDebugBuildConfig --offline --no-daemon --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

### Task 2: Enable the full SDK configuration factory

**Files:**
- Create: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertiseSdkOwnershipSourceTest.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/advertise/AdvertiseSdkConfigFactory.kt`
- Modify: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertiseSdkConfigFactoryTest.kt`

- [ ] **Step 1: Write the failing factory contract**

```kotlin
package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseSdkOwnershipSourceTest {
    @Test
    fun factory_enables_sdk_owned_integrations_from_build_config() {
        val source = File(
            "src/main/java/com/findmyphone/gpstracker/advertise/AdvertiseSdkConfigFactory.kt"
        ).readText()
        listOf(
            "defaultTopic(BuildConfig.ADV_DEFAULT_TOPIC)",
            "firebase(analyticsEnabled = true, messagingEnabled = true, subscribeDefaultTopic = true)",
            "enabled = BuildConfig.ADV_REMOTE_CONFIG_ENABLED",
            "encryptionKeyBase64 = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
            "enabled = BuildConfig.ADV_THINKING_ENABLED",
            "enabled = BuildConfig.ADV_SINGULAR_ENABLED",
            "nativeId = BuildConfig.ADV_ADMOB_NATIVE_ID",
            "enabled = BuildConfig.ADV_FACEBOOK_ENABLED",
            "enabled = BuildConfig.ADV_TIKTOK_ENABLED",
            "enabled = BuildConfig.ADV_SAFE_ENABLED",
            "enabled = BuildConfig.ADV_PLAY_INTEGRITY_ENABLED",
        ).forEach { expected -> assertTrue("Missing $expected", source.contains(expected)) }
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseSdkOwnershipSourceTest" --offline --no-daemon --console=plain
```

Expected: FAIL because Firebase, Remote Config, Thinking, Singular, and social integrations are hard-disabled.

- [ ] **Step 3: Map the BuildConfig values in `AdvertiseSdkConfigFactory`**

Retain legal, server, resources, push, and notifications configuration. Replace the disabled integration blocks with:

```kotlin
defaultTopic(BuildConfig.ADV_DEFAULT_TOPIC)
firebase(analyticsEnabled = true, messagingEnabled = true, subscribeDefaultTopic = true)
remoteConfig(
    enabled = BuildConfig.ADV_REMOTE_CONFIG_ENABLED,
    encryptionKeyBase64 = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY,
    encryptionKeyId = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID,
)
thinking(
    enabled = BuildConfig.ADV_THINKING_ENABLED,
    appKey = BuildConfig.ADV_THINKING_APP_KEY,
    serverUrl = BuildConfig.ADV_THINKING_SERVER_URL,
)
singular(
    enabled = BuildConfig.ADV_SINGULAR_ENABLED,
    apiKey = BuildConfig.ADV_SINGULAR_API_KEY,
    secret = BuildConfig.ADV_SINGULAR_SECRET,
)
adMob(
    enabled = BuildConfig.ADV_ADMOB_ENABLED,
    appId = BuildConfig.ADV_ADMOB_APP_ID,
    bannerId = "",
    interstitialId = BuildConfig.ADV_ADMOB_INTERSTITIAL_ID,
    nativeId = BuildConfig.ADV_ADMOB_NATIVE_ID,
    openId = BuildConfig.ADV_ADMOB_OPEN_ID,
    rewardedId = BuildConfig.ADV_ADMOB_REWARDED_ID,
)
facebook(
    enabled = BuildConfig.ADV_FACEBOOK_ENABLED,
    appId = BuildConfig.ADV_FACEBOOK_APP_ID,
    clientToken = BuildConfig.ADV_FACEBOOK_CLIENT_TOKEN,
)
tiktok(
    enabled = BuildConfig.ADV_TIKTOK_ENABLED,
    accessToken = BuildConfig.ADV_TIKTOK_ACCESS_TOKEN,
    ttAppId = BuildConfig.ADV_TIKTOK_TT_APP_ID,
    appId = packageName,
)
safe(
    enabled = BuildConfig.ADV_SAFE_ENABLED,
    expectedSignatures = "",
    expectedPackageName = packageName,
    rejectDebuggableBuilds = false,
    rejectDebuggerAttached = false,
    killProcessOnFailure = false,
)
playIntegrity(
    enabled = BuildConfig.ADV_PLAY_INTEGRITY_ENABLED,
    cloudProjectNumber = BuildConfig.ADV_PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER,
    runInDebugBuilds = false,
)
```

- [ ] **Step 4: Run the focused factory tests**

Before running the tests, replace the old disabled-integration and debug-ID assertions in `AdvertiseSdkConfigFactoryTest` with:

```kotlin
assertEquals("debug-all", config.defaultTopic)
assertTrue(config.firebase.analyticsEnabled)
assertTrue(config.firebase.messagingEnabled)
assertTrue(config.firebase.subscribeDefaultTopic)
assertEquals(BuildConfig.ADV_REMOTE_CONFIG_ENABLED, config.remoteConfig.enabled)
assertEquals(
    BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY,
    config.remoteConfig.encryptionKeyBase64,
)
assertEquals(BuildConfig.ADV_THINKING_ENABLED, config.thinking.enabled)
assertEquals(BuildConfig.ADV_THINKING_APP_KEY, config.thinking.appKey)
assertEquals(BuildConfig.ADV_SINGULAR_ENABLED, config.singular.enabled)
assertEquals(BuildConfig.ADV_SINGULAR_API_KEY, config.singular.apiKey)
assertEquals("ca-app-pub-3643549411810853/7121804517", BuildConfig.ADV_ADMOB_INTERSTITIAL_ID)
assertEquals("ca-app-pub-3643549411810853/4615599677", BuildConfig.ADV_ADMOB_OPEN_ID)
assertEquals(BuildConfig.ADV_ADMOB_NATIVE_ID, config.adMob.nativeId)
assertEquals(BuildConfig.ADV_ADMOB_REWARDED_ID, config.adMob.rewardedId)
assertFalse(config.facebook.enabled)
assertFalse(config.tiktok.enabled)
assertFalse(config.safe.enabled)
assertFalse(config.playIntegrity.enabled)
assertFalse(config.push.enabled)
assertFalse(config.notifications.enabled)
```

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseSdkOwnershipSourceTest" --tests "com.findmyphone.gpstracker.advertise.AdvertiseSdkConfigFactoryTest" --offline --no-daemon --console=plain
```

Expected: PASS with the factory values matching the generated Debug BuildConfig.

### Task 3: Move startup and Remote Config ownership to the SDK

**Files:**
- Create: `app/src/main/java/com/findmyphone/gpstracker/advertise/AdvertiseRemoteConfigSynchronizer.kt`
- Create: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertiseRemoteConfigSyncSourceTest.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/base/MyApplication.kt`

- [ ] **Step 1: Write the failing startup and synchronization contract**

```kotlin
package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseRemoteConfigSyncSourceTest {
    @Test
    fun application_uses_sdk_as_the_single_initializer() {
        val source = File("src/main/java/com/findmyphone/gpstracker/base/MyApplication.kt").readText()
        assertTrue(source.contains("initializeAdvertiseSdk()"))
        assertTrue(source.contains("AdvertiseRemoteConfigSynchronizer.register(this)"))
        assertTrue(source.indexOf("initializeAdvertiseSdk()") < source.indexOf("initializeApplication()"))
        assertFalse(source.contains("initSDKAsync"))
    }

    @Test
    fun sdk_remote_config_updates_existing_business_preferences() {
        val source = File(
            "src/main/java/com/findmyphone/gpstracker/advertise/AdvertiseRemoteConfigSynchronizer.kt"
        ).readText()
        assertTrue(source.contains("AdvertiseSdk.getRemoteConfigInitStatus"))
        listOf(
            "notification_config", "notification_config_nature", "notification_content",
            "ad_policy", "ad_policy_nature", "ad_native_policy", "ad_native_policy_nature",
            "adload_config", "adload_config_nature", "native_ad_ids",
            "nativead_click_to_hideconfig", "openreview", "skip_setlanguage",
            "skip_login", "skip_guide", "show_main_bottom_native_time",
            "native_refresh_time", "push_scheduled_sending_time",
        ).forEach { key -> assertTrue("Missing $key", source.contains("\"$key\"")) }
        assertTrue(source.contains("NativeAdConfigManager.init()"))
        assertTrue(source.contains("ScheduledPushScheduler.applyRemoteTimes("))
        assertTrue(source.contains("ScheduledPushScheduler.cancelAllWork("))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseRemoteConfigSyncSourceTest" --offline --no-daemon --console=plain
```

Expected: FAIL because the synchronizer does not exist and `initSDKAsync` remains active.

- [ ] **Step 3: Create `AdvertiseRemoteConfigSynchronizer`**

```kotlin
package com.findmyphone.gpstracker.advertise

import android.util.Log
import com.findmyphone.gpstracker.base.MyApplication
import com.findmyphone.gpstracker.bean.ScheduledTimeBean
import com.findmyphone.gpstracker.utils.AppPrefsUtils
import com.findmyphone.gpstracker.utils.NativeAdConfigManager
import com.findmyphone.gpstracker.utils.ScheduledPushScheduler
import com.google.gson.Gson
import com.pdffox.adv.AdvertiseSdk

object AdvertiseRemoteConfigSynchronizer {
    private const val TAG = "AdvertiseRemoteConfig"
    private val gson = Gson()

    fun register(application: MyApplication) {
        AdvertiseSdk.getRemoteConfigInitStatus(
            AdvertiseSdk.RemoteConfigInitStatusListener { isInitialized, isSuccessful, error ->
                if (!isSuccessful) {
                    Log.w(TAG, "Remote Config initialization failed: initialized=$isInitialized", error)
                    return@RemoteConfigInitStatusListener
                }
                runCatching { synchronize(application) }
                    .onFailure { Log.e(TAG, "Remote Config synchronization failed", it) }
            }
        )
    }

    private fun synchronize(application: MyApplication) {
        listOf(
            "notification_config", "notification_config_nature", "notification_content",
            "ad_policy", "ad_policy_nature", "ad_native_policy", "ad_native_policy_nature",
            "adload_config", "adload_config_nature", "native_ad_ids",
            "nativead_click_to_hideconfig",
        ).forEach { key ->
            AppPrefsUtils.commitString(
                key,
                AdvertiseSdk.getRemoteConfigString(key, AppPrefsUtils.getString(key)),
            )
        }
        listOf("openreview", "skip_setlanguage", "skip_login", "skip_guide").forEach { key ->
            AppPrefsUtils.commitBoolean(
                key,
                AdvertiseSdk.getRemoteConfigBoolean(key, AppPrefsUtils.getBoolean(key)),
            )
        }
        listOf("show_main_bottom_native_time", "native_refresh_time").forEach { key ->
            AppPrefsUtils.commitLong(
                key,
                AdvertiseSdk.getRemoteConfigLong(key, AppPrefsUtils.getLong(key)),
            )
        }
        NativeAdConfigManager.init()
        synchronizeScheduledPush(application)
    }

    private fun synchronizeScheduledPush(application: MyApplication) {
        val value = AdvertiseSdk.getRemoteConfigString("push_scheduled_sending_time")
        if (value.isBlank()) {
            ScheduledPushScheduler.cancelAllWork(application)
            return
        }
        runCatching { gson.fromJson(value, ScheduledTimeBean::class.java) }
            .onSuccess { schedule ->
                AppPrefsUtils.commitInt("scheduled_offset_second", schedule.offset_second)
                ScheduledPushScheduler.applyRemoteTimes(application, schedule.hour_minute_of_day)
            }
            .onFailure { Log.e(TAG, "Invalid push schedule", it) }
    }
}
```

- [ ] **Step 4: Make SDK initialization the first application-owned startup step**

Change `MyApplication.onCreate` to:

```kotlin
override fun onCreate() {
    super.onCreate()
    _instance = this
    initializeAdvertiseSdk()
    AdvertiseRemoteConfigSynchronizer.register(this)
    initializeApplication()
}
```

Remove `initSDKAsync(instance)` from `initializeApplication` and remove its import plus now-unused Firebase imports. Retain map initialization, lifecycle callbacks, and security checks.

- [ ] **Step 5: Run the focused startup tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseRemoteConfigSyncSourceTest" --tests "com.findmyphone.gpstracker.advertise.AdvertiseInitializationSourceTest" --offline --no-daemon --console=plain
```

Expected: PASS.

### Task 4: Use SDK attribution for configuration branches

**Files:**
- Create: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertiseTrafficSourceMigrationSourceTest.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ad/AdLimitManager.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/ad/pool/MyAdLoader.kt`
- Modify: `app/src/main/java/com/findmyphone/gpstracker/services/PersistentNotificationService.kt`

- [ ] **Step 1: Write the failing attribution source contract**

```kotlin
package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseTrafficSourceMigrationSourceTest {
    @Test
    fun policy_consumers_use_advertise_sdk_attribution() {
        val paths = listOf(
            "src/main/java/com/findmyphone/gpstracker/ad/AdLimitManager.kt",
            "src/main/java/com/findmyphone/gpstracker/ad/pool/MyAdLoader.kt",
            "src/main/java/com/findmyphone/gpstracker/services/PersistentNotificationService.kt",
        )
        paths.forEach { path ->
            val source = File(path).readText()
            assertTrue("$path must use SDK attribution", source.contains("AdvertiseSdk.isNature"))
        }
        val loader = File(paths[1]).readText()
        val notification = File(paths[2]).readText()
        assertFalse(loader.contains("AdvCheckManager.params.fromNature"))
        assertFalse(notification.contains("AdvCheckManager.params.fromNature"))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseTrafficSourceMigrationSourceTest" --offline --no-daemon --console=plain
```

Expected: FAIL because all three files still branch on `AdvCheckManager.params.fromNature`.

- [ ] **Step 3: Replace policy selection with SDK attribution**

Add `import com.pdffox.adv.AdvertiseSdk` to each file.

In `AdLimitManager`, remove the `fromQuantity/openreview` mutation blocks and use `AdvertiseSdk.isNature` for both remote policy selection and raw-resource fallback in `initConfig` and `initNativeConfig`.

In `MyAdLoader.updateNativeLoadConfig`, use:

```kotlin
val config = if (AdvertiseSdk.isNature) {
    AppPrefsUtils.getString("adload_config_nature")
} else {
    AppPrefsUtils.getString("adload_config")
}
```

In `PersistentNotificationService.ensurePushConfigInitialized`, use:

```kotlin
val currentConfig = if (AdvertiseSdk.isNature) {
    AppPrefsUtils.getString("notification_config_nature")
} else {
    AppPrefsUtils.getString("notification_config")
}
```

- [ ] **Step 4: Run the focused attribution test**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseTrafficSourceMigrationSourceTest" --offline --no-daemon --console=plain
```

Expected: PASS.

### Task 5: Verify the complete SDK ownership migration

**Files:**
- Verify all modified files

- [ ] **Step 1: Run all unit tests without using prior task outputs as proof**

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --offline --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 2: Compile Debug Kotlin**

```powershell
.\gradlew.bat :app:compileDebugKotlin --offline --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build and confirm the Debug APK**

```powershell
.\gradlew.bat :app:assembleDebug --offline --no-daemon --stacktrace --console=plain
Get-Item app\build\outputs\apk\debug\app-debug.apk
```

Expected: `BUILD SUCCESSFUL` and the APK exists.

- [ ] **Step 4: Audit ownership and retained native ads**

```powershell
rg -n "initSDKAsync|Firebase\.remoteConfig|Singular\.init|TDAnalytics\.init" app/src/main/java/com/findmyphone/gpstracker/base app/src/main/java/com/findmyphone/gpstracker/advertise
rg -n "AdvertiseSdk\.init|AdvertiseSdk\.getRemoteConfigInitStatus|AdvertiseSdk\.isNature|ADV_DEFAULT_TOPIC|ADV_REMOTE_CONFIG_ENABLED" app/src/main app/build.gradle.kts
rg -n "ShowNativeAd|NativeAdWidget|admobNativePool|fun loadNative" app/src/main/java
```

Expected: no duplicate startup initializer in `MyApplication`; SDK ownership references are present; native ad implementation remains present.

- [ ] **Step 5: Record the repository limitation**

Do not initialize or replace `.git`. Report that no commit was created because repository metadata is absent.
