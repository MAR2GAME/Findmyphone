package com.findmyphone.gpstracker.advertise

import com.findmyphone.gpstracker.BuildConfig
import com.findmyphone.gpstracker.config.AppConfig
import com.pdffox.adv.AdDebugOverrideMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvertiseSdkConfigFactoryTest {
    @Test
    fun creates_sdk_configuration_for_host_owned_services() {
        val config = AdvertiseSdkConfigFactory.create(AppConfig.packageName, true)

        assertEquals(AppConfig.packageName, config.packageName)
        assertEquals(AppConfig.PRIVACY_URL, config.privacyUrl)
        assertEquals(AppConfig.TERMS_URL, config.termsUrl)
        assertTrue(config.server.enabled)
        assertEquals(AppConfig.ADVHost, config.server.releaseHost)
        assertEquals(AppConfig.ADVHost, config.server.testHost)
        assertEquals("TianWangGaiDiHu", config.server.parseTokenKey)
        assertEquals(AdDebugOverrideMode.POLICY, config.adDebugOverride.mode)
        assertEquals("debug-all", config.defaultTopic)

        assertTrue(config.adMob.enabled)
        assertEquals("ca-app-pub-3643549411810853~3318290879", BuildConfig.ADV_ADMOB_APP_ID)
        assertEquals(BuildConfig.ADV_ADMOB_APP_ID, config.adMob.appId)
        assertEquals("ca-app-pub-3643549411810853/7121804517", BuildConfig.ADV_ADMOB_INTERSTITIAL_ID)
        assertEquals(BuildConfig.ADV_ADMOB_INTERSTITIAL_ID, config.adMob.interstitialId)
        assertEquals("ca-app-pub-3643549411810853/4615599677", BuildConfig.ADV_ADMOB_OPEN_ID)
        assertEquals(BuildConfig.ADV_ADMOB_OPEN_ID, config.adMob.openId)
        assertEquals("", config.adMob.bannerId)
        assertEquals(BuildConfig.ADV_ADMOB_NATIVE_ID, config.adMob.nativeId)
        assertEquals(BuildConfig.ADV_ADMOB_REWARDED_ID, config.adMob.rewardedId)

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
        assertFalse(config.facebook.enabled)
        assertFalse(config.tiktok.enabled)
        assertFalse(config.safe.enabled)
        assertFalse(config.playIntegrity.enabled)
        assertFalse(config.push.enabled)
        assertFalse(config.push.persistentServiceEnabled)
        assertFalse(config.push.firebaseMessagingServiceEnabled)
        assertFalse(config.push.serviceStarterJobEnabled)
        assertFalse(config.push.bootReceiverEnabled)
        assertFalse(config.push.notificationDeletedReceiverEnabled)
        assertFalse(config.push.fileProviderEnabled)
        assertFalse(config.push.deletionObserverEnabled)
        assertFalse(config.notifications.enabled)
    }
}
