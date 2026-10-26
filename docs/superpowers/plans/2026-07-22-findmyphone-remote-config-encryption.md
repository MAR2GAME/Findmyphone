# FindMyPhone Remote Config Encryption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Encrypt the supplied FindMyPhone Remote Config with AdConfig, configure the generated key for advertise SDK builds, and retain the protected artifacts under the project `key` directory.

**Architecture:** Use the existing AdConfig server export endpoint as the only generator of the AES-256-GCM envelope and random key. FindMyPhone's Gradle build parses the exact generated key metadata file, with Gradle property and environment-variable overrides, and exposes the values through the existing advertise SDK BuildConfig fields.

**Tech Stack:** Node.js, AdConfig AES-256-GCM, PowerShell, Gradle Kotlin DSL, JUnit 4 source-contract tests

**Repository constraint:** `D:\work\majia\FindMyPhone\.git` is empty. Do not initialize it; replace commit steps with explicit file and verification checkpoints.

---

### Task 1: Lock The Gradle Key-File Contract

**Files:**
- Create: `app/src/test/java/com/findmyphone/gpstracker/advertise/AdvertiseRemoteConfigKeySourceTest.kt`
- Inspect: `app/build.gradle.kts`
- Inspect: `.gitignore`

- [ ] **Step 1: Write the failing source-contract test**

```kotlin
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
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseRemoteConfigKeySourceTest" --offline --no-daemon --console=plain
```

Expected: FAIL because the build does not parse a generated key file and `.gitignore` does not contain `/key/`.

### Task 2: Generate The AdConfig Artifacts

**Files:**
- Read: `C:\Users\weiyu\Downloads\remote_config_findmyphone-0720.json`
- Execute: `D:\work\AdConfig-master\server.js`
- Create: `key/remote_config_encrypted_<AdConfig timestamp>.json`
- Create: `key/remote_config_key_<AdConfig timestamp>.json`

- [ ] **Step 1: Re-run AdConfig's source and crypto checks**

Run from `D:\work\AdConfig-master`:

```powershell
$env:REMOTE_CONFIG_SOURCE = 'C:\Users\weiyu\Downloads\remote_config_findmyphone-0720.json'
node scripts\check.js
```

Expected: exit code 0, 23 parameters validated, and encryption/decryption/import checks reported as available.

- [ ] **Step 2: Export with the AdConfig server and copy the exact generated pair**

Run one guarded PowerShell operation so the local server is always stopped:

```powershell
$toolDir = 'D:\work\AdConfig-master'
$sourcePath = 'C:\Users\weiyu\Downloads\remote_config_findmyphone-0720.json'
$keyDir = 'D:\work\majia\FindMyPhone\key'
$env:REMOTE_CONFIG_SOURCE = $sourcePath
$env:PORT = '44173'
$server = Start-Process -FilePath 'node' -ArgumentList 'server.js' -WorkingDirectory $toolDir -PassThru -WindowStyle Hidden
try {
    $configResponse = $null
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try {
            $configResponse = Invoke-RestMethod 'http://127.0.0.1:44173/api/config'
            break
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    if ($null -eq $configResponse) {
        throw 'AdConfig server did not become ready.'
    }
    $requestBody = @{
        config = $configResponse.config
        encrypted = $true
    } | ConvertTo-Json -Depth 100 -Compress
    $export = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:44173/api/export' -ContentType 'application/json' -Body $requestBody
    New-Item -ItemType Directory -Path $keyDir -Force | Out-Null
    Copy-Item -LiteralPath $export.filePath -Destination (Join-Path $keyDir $export.fileName)
    Copy-Item -LiteralPath $export.keyFile.filePath -Destination (Join-Path $keyDir $export.keyFile.fileName)
    [pscustomobject]@{
        CiphertextFile = $export.fileName
        KeyFile = $export.keyFile.fileName
        CiphertextBytes = $export.bytes
        KeyBytes = $export.keyFile.bytes
    } | Format-List
} finally {
    Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
}
```

Expected: exit code 0 and only the generated filenames and byte counts are printed. Record the exact returned key filename for Task 3.

### Task 3: Configure FindMyPhone To Read The Generated Key

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`

- [ ] **Step 1: Add the key metadata parser and override precedence**

Add `import groovy.json.JsonSlurper`, declare `remoteConfigKeyFile` with the exact filename returned by Task 2, and add:

```kotlin
val remoteConfigKeyPayload: Map<String, Any?> by lazy {
    if (!remoteConfigKeyFile.isFile) {
        return@lazy emptyMap()
    }
    val parsed = try {
        JsonSlurper().parse(remoteConfigKeyFile)
    } catch (cause: Exception) {
        throw GradleException("Unable to parse the local Remote Config key file.", cause)
    }
    if (parsed !is Map<*, *>) {
        throw GradleException("The local Remote Config key file must contain a JSON object.")
    }
    parsed.entries.associate { (key, value) -> key.toString() to value }
}

fun remoteConfigEncryptionProperty(
    propertyName: String,
    environmentName: String,
    keyFileField: String,
): String {
    providers.gradleProperty(propertyName).orNull?.takeIf { it.isNotBlank() }?.let { return it }
    providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }?.let { return it }
    val keyFileValue = remoteConfigKeyPayload[keyFileField] as? String
    if (remoteConfigKeyFile.isFile && keyFileValue.isNullOrBlank()) {
        throw GradleException("The local Remote Config key file is missing a non-empty '$keyFileField' value.")
    }
    return keyFileValue.orEmpty()
}
```

- [ ] **Step 2: Route only the encryption fields through the key-file helper**

Inside `defaultConfig`, add:

```kotlin
fun remoteConfigEncryptionStringField(
    name: String,
    propertyName: String,
    environmentName: String,
    keyFileField: String,
) {
    buildConfigField(
        "String",
        name,
        remoteConfigEncryptionProperty(propertyName, environmentName, keyFileField).asBuildConfigString(),
    )
}
```

Replace the two current `advertiseStringField` calls with:

```kotlin
remoteConfigEncryptionStringField(
    "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
    "adv.remoteConfig.encryptionKey",
    "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
    "encryptionKeyBase64",
)
remoteConfigEncryptionStringField(
    "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
    "adv.remoteConfig.encryptionKeyId",
    "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
    "keyId",
)
```

- [ ] **Step 3: Protect the generated artifacts**

Append this exact line to `.gitignore`:

```gitignore
/key/
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.findmyphone.gpstracker.advertise.AdvertiseRemoteConfigKeySourceTest" --offline --no-daemon --console=plain
```

Expected: PASS and `BUILD SUCCESSFUL`.

### Task 4: Verify Encryption And Android Integration End To End

**Files:**
- Inspect: `key/remote_config_encrypted_<AdConfig timestamp>.json`
- Inspect: `key/remote_config_key_<AdConfig timestamp>.json`
- Generate: `app/build/generated/source/buildConfig/debug/com/findmyphone/gpstracker/BuildConfig.java`

- [ ] **Step 1: Verify decryptability, exact parameters, and matching key ID**

Run a Node verifier using AdConfig's own parser and decryptor. The verifier must parse the one generated ciphertext file and one generated key file under `key`, decrypt the envelope, compare the recovered `parameters` object to the supplied source with `assert.deepStrictEqual`, and compare the envelope `kid` to key metadata `keyId`. It prints only:

```text
OK: encrypted Remote Config decrypts to the exact source parameters
OK: envelope key ID matches generated key metadata
```

- [ ] **Step 2: Generate and inspect Debug BuildConfig without printing secrets**

Run:

```powershell
.\gradlew.bat :app:generateDebugBuildConfig --offline --no-daemon --console=plain
```

Then parse `BuildConfig.java` and assert both `ADV_REMOTE_CONFIG_ENCRYPTION_KEY` and `ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID` are non-empty and exactly equal the generated key metadata values. Print only:

```text
OK: generated BuildConfig contains the selected Remote Config key and key ID
```

- [ ] **Step 3: Run all unit tests from a clean test execution**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --offline --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL` with zero test failures.

- [ ] **Step 4: Compile Debug Kotlin**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin --offline --no-daemon --stacktrace --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Audit artifacts and ignore rules**

Confirm exactly one `remote_config_encrypted_*.json` and one `remote_config_key_*.json` exist in `key`, both are non-empty, `.gitignore` contains `/key/`, and no secret values were written to tracked Gradle source.
