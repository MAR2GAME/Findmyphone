package com.findmyphone.gpstracker.advertise

import android.content.Context
import com.findmyphone.gpstracker.BuildConfig
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.config.AppConfig
import com.pdffox.adv.AdvertiseSdkConfig
import com.pdffox.adv.AdvertiseSdkConfigs
import com.pdffox.adv.NotificationFeatureConfig

object AdvertiseSdkConfigFactory {
    fun create(context: Context): AdvertiseSdkConfig =
        create(context.packageName, BuildConfig.DEBUG)

    fun create(packageName: String, isDebug: Boolean): AdvertiseSdkConfig {
        return AdvertiseSdkConfigs.create(packageName, isDebug) {
            legal(
                privacyUrl = AppConfig.PRIVACY_URL,
                termsUrl = AppConfig.TERMS_URL,
            )
            defaultTopic(BuildConfig.ADV_DEFAULT_TOPIC)
            adDebugOverride("POLICY")
            server(
                enabled = true,
                releaseHost = AppConfig.ADVHost,
                testHost = AppConfig.ADVHost,
                parseTokenKey = "TianWangGaiDiHu",
            )
            resources(
                adPolicyRawResId = R.raw.ad_policy,
                adLoadConfigRawResId = R.raw.adload_config,
                cloudCidrsRawResId = com.pdffox.adv.R.raw.cloud,
                googleCidrsRawResId = com.pdffox.adv.R.raw.google,
            )
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
            push(
                enabled = false,
                persistentServiceEnabled = false,
                firebaseMessagingServiceEnabled = false,
                serviceStarterJobEnabled = false,
                bootReceiverEnabled = false,
                notificationDeletedReceiverEnabled = false,
                fileProviderEnabled = false,
                deletionObserverEnabled = false,
            )
            notifications(NotificationFeatureConfig(enabled = false))
        }
    }
}
