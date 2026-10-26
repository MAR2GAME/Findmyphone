package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseInitializationSourceTest {
    @Test
    fun application_initializes_advertise_sdk_and_splash_delegates_consent() {
        val applicationSource = File(
            "src/main/java/com/findmyphone/gpstracker/base/MyApplication.kt"
        ).readText()
        val splashSource = File(
            "src/main/java/com/findmyphone/gpstracker/ui/SplashActivity.kt"
        ).readText()

        assertTrue(applicationSource.contains("AdvertiseSdk.init("))
        assertTrue(
            applicationSource.contains(
                "AdvertiseSdkConfigFactory.create(this@MyApplication)"
            )
        )
        assertFalse(applicationSource.contains("UmpConsentManager"))

        assertTrue(splashSource.contains("AdvertisePageMediator.initSplashConsent("))
        assertTrue(splashSource.contains("AdvertisePageMediator.showSplashConsent("))
        assertTrue(splashSource.contains("formShown.onAwait { true }"))
        assertTrue(splashSource.contains("if (formWasShown == true)"))
        assertFalse(splashSource.contains("UmpConsentManager"))
    }
}
