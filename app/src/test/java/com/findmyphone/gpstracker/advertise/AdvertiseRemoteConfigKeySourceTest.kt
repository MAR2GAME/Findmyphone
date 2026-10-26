package com.findmyphone.gpstracker.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdvertiseRemoteConfigKeySourceTest {
    @Test
    fun build_loads_generated_key_metadata_from_the_ignored_key_directory() {
        val buildSource = File("build.gradle.kts").readText()
        val gitIgnore = File("../.gitignore").readText()

        listOf(
            "import groovy.json.JsonSlurper",
            "val remoteConfigKeyFile = rootProject.file(",
            "val remoteConfigKeyPayload: Map<String, Any?> by lazy",
            "fun remoteConfigEncryptionProperty(",
            "providers.gradleProperty(propertyName)",
            "providers.environmentVariable(environmentName)",
            "remoteConfigKeyPayload[keyFileField] as? String",
            "fun remoteConfigEncryptionStringField(",
            "\"encryptionKeyBase64\"",
            "\"keyId\"",
        ).forEach { expected ->
            assertTrue("Missing $expected", buildSource.contains(expected))
        }

        assertTrue(
            Regex("""rootProject\.file\("key/remote_config_key_\d{8}_\d{6}\.json"\)""")
                .containsMatchIn(buildSource),
        )
        assertTrue(gitIgnore.lineSequence().any { it.trim() == "/key/" })
        assertFalse(
            buildSource.contains(
                "advertiseStringField(\"ADV_REMOTE_CONFIG_ENCRYPTION_KEY\"",
            ),
        )
    }
}
