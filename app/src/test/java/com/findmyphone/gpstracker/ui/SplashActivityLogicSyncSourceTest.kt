package com.findmyphone.gpstracker.ui

import org.junit.Assert.assertEquals
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
        ).forEach { marker ->
            assertTrue("SplashActivity.kt is missing required marker: $marker", source.contains(marker))
        }

        listOf(
            "canShowSplashAd",
            "runConsentFlowWithTimeout()",
            "simulateProgressAndNavigate()",
            "logSplashDuration()",
            "coroutineContext.isActive",
        ).forEach { marker ->
            assertFalse("SplashActivity.kt still contains forbidden marker: $marker", source.contains(marker))
        }
    }

    @Test
    fun splash_visual_and_manifest_files_are_unchanged() {
        assertSha256(
            "src/main/res/layout/activity_splash.xml",
            "2928D99363E20231AC45CC1FBF0E81FEB4DCDC4E88DF3246E2C51E2D88502DB3",
        )
        assertSha256(
            "src/main/res/mipmap-xxxhdpi/splash_bg.png",
            "109B7D3A8A9C860D59D7A05483FC1336C8CED94B72D60C96D46BD750D1631ECB",
        )
        assertSha256(
            "src/main/AndroidManifest.xml",
            "697384FF2CF46A83B8E4B0BF3043775F81FE1D6B24F29E64219563150268729F",
        )
    }

    private fun assertSha256(path: String, expected: String) {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(File(path).readBytes())
            .joinToString("") { byte -> "%02X".format(byte) }

        assertEquals("SHA-256 changed for $path", expected, actual)
    }
}
