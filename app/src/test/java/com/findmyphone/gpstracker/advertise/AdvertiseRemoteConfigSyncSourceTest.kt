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
    fun host_install_timing_state_survives_sdk_ownership_migration() {
        val source = File("src/main/java/com/findmyphone/gpstracker/base/MyApplication.kt").readText()
        assertTrue(source.contains("recordInstallTimeIfNeeded()"))
        assertTrue(source.contains("val params = AdvCheckManager.params"))
        assertTrue(source.contains("params.installTime = System.currentTimeMillis()"))
        assertTrue(source.contains("params.isFirstOpen = false"))
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
