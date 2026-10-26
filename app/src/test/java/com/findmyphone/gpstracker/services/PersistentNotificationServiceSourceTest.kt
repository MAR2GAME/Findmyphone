package com.findmyphone.gpstracker.services

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PersistentNotificationServiceSourceTest {

    private val source = File(
        "src/main/java/com/findmyphone/gpstracker/services/PersistentNotificationService.kt"
    ).readText()

    @Test
    fun serviceRegistersAndUnregistersVolumeObserver() {
        assertTrue(source.contains("import android.database.ContentObserver"))
        assertTrue(source.contains("import android.media.AudioManager"))
        assertTrue(source.contains("import android.provider.Settings"))
        assertTrue(source.contains("contentResolver.registerContentObserver("))
        assertTrue(source.contains("Settings.System.CONTENT_URI"))
        assertTrue(source.contains("initVolumeKeyListener()"))
        assertTrue(source.contains("contentResolver.unregisterContentObserver(it)"))
        assertTrue(source.contains("volumeObserver = null"))
    }

    @Test
    fun volumeChangeTriggersInstalledAppNotificationScene() {
        assertTrue(source.contains("private fun handleVolumeChanged()"))
        assertTrue(source.contains("now - lastVolumeCallbackTime < 250L"))
        assertTrue(source.contains("handleNotificationEvent("))
        assertTrue(source.contains("action = Intent.ACTION_PACKAGE_ADDED"))
        assertTrue(source.contains("scene = \"app_installed\""))
    }
}
