package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FullScreenAdRemovalSourceTest {
    @Test
    fun project_ad_infrastructure_keeps_native_and_removes_full_screen_branches() {
        val main = File("src/main")
        val ownedInfrastructure = listOf(
            File(main, "java/com/findmyphone/gpstracker/ad"),
            File(main, "java/com/findmyphone/gpstracker/config/AppConfig.kt"),
            File(main, "java/com/findmyphone/gpstracker/utils/SdkInitUtils.kt"),
        ).flatMap { root ->
            if (root.isDirectory) root.walkTopDown().filter(File::isFile).toList()
            else listOf(root)
        }.filterNot { it.path.replace('\\', '/').endsWith("ad/AdvActivity.kt") }
            .joinToString("\n") { it.readText() }

        listOf(
            "com.google.android.gms.ads.appopen.AppOpenAd",
            "com.google.android.gms.ads.interstitial.InterstitialAd",
            "admobOpenPool",
            "admobInterPool",
            "loadAdmobOpen",
            "loadAdmobInter",
            "loadOpen(",
            "loadInter(",
            "isShowOpenAd",
            "isShowInterstitialAd",
            "AppOpenHelper",
            "InterstitialAdHelper",
        ).forEach { forbidden ->
            assertFalse("Found legacy full-screen identifier: $forbidden", ownedInfrastructure.contains(forbidden))
        }

        val adPoolSource = File(
            main,
            "java/com/findmyphone/gpstracker/ad/pool/AdPool.kt",
        ).readText()
        val loaderSource = File(
            main,
            "java/com/findmyphone/gpstracker/ad/pool/MyAdLoader.kt",
        ).readText()
        assertTrue(File(main, "java/com/findmyphone/gpstracker/ad/ShowNativeAd.kt").isFile)
        assertTrue(adPoolSource.contains("admobNativePool"))
        assertTrue(loaderSource.contains("fun loadNative("))
        assertTrue(loaderSource.contains("fun loadNativeAd("))
        assertFalse(File(main, "java/com/findmyphone/gpstracker/ad/AdvIDs.kt").exists())
    }
}
