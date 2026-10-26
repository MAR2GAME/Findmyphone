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
