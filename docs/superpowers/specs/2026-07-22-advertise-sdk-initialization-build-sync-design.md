# Advertise SDK Initialization And Build Sync Design

## Goal

Synchronize FindMyPhone with LocShare's `com.pdffox:advertise:1.0.10` initialization and Gradle configuration model. The Advertise SDK becomes the single owner of Firebase, Remote Config, Thinking Analytics, Singular, and full-screen ad initialization while FindMyPhone retains its own package name, credentials, ad unit IDs, native-ad implementation, and business configuration keys.

## Scope

The change covers:

- Property-driven `ADV_*` BuildConfig fields and manifest placeholders.
- Release/debug SDK switches and default Firebase topics.
- Full `AdvertiseSdkConfigFactory` configuration for Firebase, Remote Config, Thinking, Singular, AdMob, Facebook, TikTok, and safety switches.
- Application startup ordering and SDK Remote Config status handling.
- Migration of traffic-source checks used by native ads to `AdvertiseSdk.isNature`.
- Removal of the active `SdkInitUtils.initSDKAsync` startup path so shared SDKs are initialized once.

The change does not replace FindMyPhone's native-ad loader, cache, UI, limits, placement keys, navigation, notification service, or scheduled-push implementation.

## Build Configuration

`app/build.gradle.kts` will use the LocShare property precedence for SDK settings:

1. `local.properties`
2. Gradle property
3. Environment variable
4. FindMyPhone-specific default

String values will be escaped before being written to BuildConfig. Remote Config encryption values will use properties or environment variables and default to empty strings; LocShare's key file and credentials will not be copied because they may belong to another Firebase project.

FindMyPhone defaults remain authoritative:

- Existing FindMyPhone AdMob app, interstitial, and App Open IDs.
- Existing FindMyPhone Thinking App ID and server URL.
- Existing FindMyPhone Singular API key and secret.
- Empty native/rewarded SDK ad IDs because native ads remain application-owned.
- Facebook, TikTok, SDK safety, and Play Integrity switches disabled unless explicitly configured.
- Remote Config, Thinking, Singular, Firebase analytics, and Firebase messaging enabled.
- Topic `all` for release and `debug-all` for debug.

The private Maven repository remains available to both plugin and dependency resolution, matching LocShare. The dependency remains pinned to `com.pdffox:advertise:1.0.10`.

## SDK Configuration Factory

`AdvertiseSdkConfigFactory` will mirror LocShare's configuration structure while reading FindMyPhone BuildConfig values. It will:

- Set legal URLs, default topic, server, policy resources, and debug policy override.
- Enable Firebase analytics, Firebase messaging, and default-topic subscription.
- Configure Remote Config, Thinking, Singular, AdMob, Facebook, TikTok, and safety from BuildConfig.
- Keep push components and notification features disabled because FindMyPhone owns those Android components.
- Keep the SDK native ad ID empty so FindMyPhone's `ShowNativeAd` and native pools remain the only native-ad implementation.

## Application Initialization

`MyApplication.onCreate` will establish the singleton, initialize `AdvertiseSdk` synchronously using the same guarded `runBlocking` boundary as LocShare, register the SDK Remote Config listener, and then continue FindMyPhone initialization.

The existing `initSDKAsync(instance)` call will be removed. This prevents duplicate Firebase, Remote Config, Thinking, Singular, and topic initialization. `SdkInitUtils` may remain as dormant legacy code where deleting it would broaden scope, but it will no longer be part of startup.

SDK initialization failures will be logged and will not crash application startup. Remote Config listener failures will preserve existing local preferences.

## Remote Config Data Flow

After `AdvertiseSdk.getRemoteConfigInitStatus` reports success, FindMyPhone will read values through `AdvertiseSdk.getRemoteConfigString`, `getRemoteConfigBoolean`, and `getRemoteConfigLong`, then persist the values consumed by current business code:

- `notification_config`, `notification_config_nature`, `notification_content`
- `ad_policy`, `ad_policy_nature`
- `ad_native_policy`, `ad_native_policy_nature`
- `adload_config`, `adload_config_nature`
- `native_ad_ids`, `nativead_click_to_hideconfig`
- `openreview`
- `skip_setlanguage`, `skip_login`, `skip_guide`
- `show_main_bottom_native_time`, `native_refresh_time`
- `push_scheduled_sending_time` and `scheduled_offset_second`

After persistence, `NativeAdConfigManager.init()` will refresh native policy state. Scheduled pushes will be updated or cancelled using the existing FindMyPhone scheduler.

## Traffic Source

Native policy selection currently depends on `AdvCheckManager.params.fromNature`. SDK ownership means SDK attribution becomes authoritative. Native ad policy, preload configuration, and notification configuration will use `AdvertiseSdk.isNature`, matching LocShare. Legacy fields can remain where unrelated code still references them, but they will no longer decide these ad configuration branches.

## Testing

Source-contract tests will be written first and observed failing before production edits. They will verify:

- Required property-driven BuildConfig fields and release/debug topics.
- Full SDK configuration factory switches and BuildConfig references.
- SDK-first application startup, Remote Config listener registration, and absence of the `initSDKAsync` call.
- Required Remote Config preference mappings.
- `AdvertiseSdk.isNature` usage in native and notification configuration branches.
- Retention of the FindMyPhone native-ad implementation and `1.0.10` dependency.

After implementation, focused tests, the full debug unit-test suite, Debug Kotlin compilation, and Debug APK assembly will run offline. A source audit will check that duplicate startup initialization is gone and native-ad files remain.

## Constraints

- Do not copy LocShare package names, signing settings, version values, ad IDs, Firebase files, or encryption key files.
- Do not reintroduce application-owned App Open or interstitial implementations.
- Do not initialize or replace the empty `.git` directory.
