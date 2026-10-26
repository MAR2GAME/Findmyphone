# FindMyPhone Remote Config Encryption Design

## Goal

Encrypt `C:\Users\weiyu\Downloads\remote_config_findmyphone-0720.json` with the existing `D:\work\AdConfig-master` implementation, make the generated key available to `com.pdffox:advertise:1.0.10`, and retain the generated key metadata and ciphertext under the FindMyPhone project `key` directory.

## Inputs And Ownership

- `D:\work\AdConfig-master` is the only encryption implementation. FindMyPhone does not duplicate or reimplement its cryptography.
- The source is the exact JSON file supplied by the user. It must pass AdConfig's parser and required-parameter validation before encryption.
- AdConfig generates a fresh random 32-byte AES key and key ID for this export.
- The encrypted envelope uses AdConfig's AES-256-GCM protocol for the Firebase parameter `remote_config_json`.

## Generated Artifacts

The project `key` directory contains exactly the generated pair for this run:

- `remote_config_encrypted_<timestamp>.json`: the Firebase-ready wrapper containing the encrypted `remote_config_json` value.
- `remote_config_key_<timestamp>.json`: the AdConfig key metadata containing `keyId` and `encryptionKeyBase64`.

The source JSON is not copied into the project. Secret values are never printed in command output or final reports.

## Gradle Integration

FindMyPhone follows the current LocShare pattern:

1. `app/build.gradle.kts` parses the generated key JSON with `JsonSlurper`.
2. The generated key file supplies `ADV_REMOTE_CONFIG_ENCRYPTION_KEY` and `ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID` when no explicit override is present.
3. A Gradle property has highest priority, followed by the matching environment variable, followed by the local generated key file.
4. A present but malformed key file fails the build with a clear `GradleException`; it never silently falls back to an empty key.
5. `AdvertiseSdkConfigFactory` continues passing the generated BuildConfig fields to the advertise SDK.

The selected key filename is explicit in `app/build.gradle.kts`, matching LocShare's existing build convention and preventing ambiguous selection when old local artifacts exist.

## Security Boundary

- Add `/key/` to `.gitignore` because the key metadata contains the production decryption key.
- Keep `local.properties` ignored as it is today.
- Do not hard-code the key or key ID in tracked Gradle source.
- Do not modify the source JSON or AdConfig implementation.

## Verification

Completion requires all of the following evidence:

1. AdConfig validates the supplied source structure.
2. Decrypting the generated envelope with the generated key reproduces the source `parameters` object exactly.
3. The envelope `kid` equals the generated key metadata `keyId` without revealing either value in logs.
4. Generated Debug BuildConfig contains non-empty encryption key and key ID values sourced from the key file.
5. `:app:compileDebugKotlin` succeeds offline.
6. The project `key` directory contains the generated key and ciphertext files, and `.gitignore` excludes the directory.

## Out Of Scope

- Uploading the encrypted value to Firebase Remote Config.
- Rotating or deleting older production keys outside FindMyPhone.
- Changing Remote Config business values or the advertise SDK encryption protocol.
