package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseBuildConfigSourceTest {
    @Test
    fun build_exposes_property_driven_advertise_sdk_configuration() {
        val source = File("build.gradle.kts").readText()
        listOf(
            "fun advertiseStringProperty(",
            "fun advertiseBooleanProperty(",
            "fun String.asBuildConfigString()",
            "ADV_REMOTE_CONFIG_ENABLED",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
            "ADV_THINKING_ENABLED",
            "ADV_THINKING_APP_KEY",
            "ADV_THINKING_SERVER_URL",
            "ADV_SINGULAR_ENABLED",
            "ADV_SINGULAR_API_KEY",
            "ADV_SINGULAR_SECRET",
            "ADV_ADMOB_NATIVE_ID",
            "ADV_ADMOB_REWARDED_ID",
            "ADV_FACEBOOK_ENABLED",
            "ADV_TIKTOK_ENABLED",
            "ADV_SAFE_ENABLED",
            "ADV_PLAY_INTEGRITY_ENABLED",
            "manifestPlaceholders[\"advAdmobAppId\"] = advertiseAdmobAppId",
            "buildConfigField(\"String\", \"ADV_DEFAULT_TOPIC\", \"\\\"all\\\"\")",
            "buildConfigField(\"String\", \"ADV_DEFAULT_TOPIC\", \"\\\"debug-all\\\"\")",
            "implementation(\"com.pdffox:advertise:1.0.10\")",
        ).forEach { expected ->
            assertTrue("Missing $expected", source.contains(expected))
        }

        assertTrue(source.contains("ca-app-pub-3643549411810853~3318290879"))
        assertFalse(source.contains("ca-app-pub-3615322193850391~9006916651"))

        val settings = File("../settings.gradle.kts").readText()
        assertEquals(
            2,
            Regex("192\\.168\\.10\\.128:8081/repository/maven-public").findAll(settings).count(),
        )
    }
}
