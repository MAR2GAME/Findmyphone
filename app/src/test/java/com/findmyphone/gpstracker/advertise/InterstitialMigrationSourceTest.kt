package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InterstitialMigrationSourceTest {
    @Test
    fun base_activity_uses_sdk_interstitial_and_legacy_helper_is_removed() {
        val main = File("src/main")
        val activitySource = File(
            main,
            "java/com/findmyphone/gpstracker/ad/AdvActivity.kt",
        ).readText()

        assertTrue(activitySource.contains("InterstitialRequestGate"))
        assertTrue(
            activitySource.contains(
                "AdvertisePageMediator.showInterstitial(this, areaKey)"
            )
        )
        assertFalse(activitySource.contains("InterstitialAdHelper"))
        assertFalse(activitySource.contains("MyAdLoader.loadInter"))
        assertFalse(activitySource.contains("AppConfig.isShowInterstitialAd"))
        assertFalse(
            File(
                main,
                "java/com/findmyphone/gpstracker/ad/InterstitialAdHelper.kt",
            ).exists()
        )
    }
}
