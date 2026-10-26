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
        ).forEach { expected ->
            assertTrue("Missing $expected", source.contains(expected))
        }
    }
}
