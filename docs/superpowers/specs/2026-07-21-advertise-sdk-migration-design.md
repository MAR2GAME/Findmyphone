# Advertise SDK Migration Design

## Goal

Remove all project-owned App Open and interstitial ad loading, caching, throttling, playback, and lifecycle code. Preserve the existing App Open and interstitial placement timing and area keys, and route playback through `com.pdffox:advertise:1.0.10`. Keep the existing native ad implementation and behavior unchanged.

## Scope

The migration covers cold-start App Open ads, foreground App Open ads, interstitial ads triggered by feature navigation, SDK initialization, and the shared ad-pool code that currently contains App Open or interstitial branches.

Native ad loading, native ad pooling, native ad display activities and dialogs, native ad area keys, native ad limits, and native ad analytics remain owned by FindMyPhone. Existing page navigation after an ad closes must remain unchanged.

## Architecture

`MyApplication` initializes `AdvertiseSdk` once with an application-specific `AdvertiseSdkConfig`. The configuration follows PhoneTracker's integration pattern and supplies FindMyPhone package information, legal URLs, server settings, analytics settings, ad unit IDs, and feature switches through BuildConfig values.

A new `AdvertisePageMediator` is the only application-level entry point for SDK App Open and interstitial playback. It protects calls against destroyed activities, converts SDK exceptions or unavailable ads into normal continuation callbacks, and ensures every continuation callback is invoked at most once.

`AdvActivity.showInterstitialAdWithStats(areaKey, onClosed)` keeps its current public contract so feature activities do not change their navigation logic. It delegates to the mediator and retains a small request gate to prevent duplicate taps from starting concurrent interstitial playback.

`SplashActivity` keeps its existing progress, timeout, native-ad preload, and destination-selection flow. Its UMP consent flow is migrated from `UmpConsentManager` to `AdvertiseSdk.initConsent` and `AdvertiseSdk.showSplashConsent`, and its project-owned App Open preload/cache/show code is replaced by SDK preload and playback calls. SDK foreground App Open lifecycle handling replaces `AppOpenHelper`; the splash flow temporarily prevents a competing foreground App Open when required, following the PhoneTracker integration pattern.

## Dependency And Configuration Changes

Add `implementation("com.pdffox:advertise:1.0.10")` and the internal Maven repository used by PhoneTracker. Add the BuildConfig fields and manifest placeholders required by the SDK without embedding environment-only secrets in Kotlin source.

Direct mediation dependencies that are used only by the removed App Open/interstitial implementation may be removed only after Gradle dependency analysis confirms they are not required by native ads or another application feature. Google Mobile Ads remains available because the native implementation directly uses it.

## Code Removal

Delete the dedicated project-owned App Open and interstitial implementations:

- `ShowOpenAd.kt`
- `AppOpenHelper.kt`
- `InterstitialAdHelper.kt`

Remove App Open and interstitial loading, cache maps, cache cleanup, callbacks, counters, ad-type branches, and ad-unit lookup functions from shared advertising files such as `AdPool.kt`, `MyAdLoader.kt`, `AdChecker.kt`, `AdConfig.kt`, and `AdvIDs.kt`. Shared native-ad code and data structures must remain behaviorally unchanged.

Remove manual App Open initialization waiting and lifecycle registration from `SdkInitUtils`. SDK initialization becomes the owner of App Open and interstitial readiness; the existing analytics and native-ad initialization responsibilities remain in place.

## Data Flow

For an interstitial placement, an activity calls the existing `showInterstitialAdWithStats` function with its current area key. The base activity acquires a per-activity request gate and calls `AdvertisePageMediator.showInterstitial`. The mediator calls `AdvertiseSdk.showInterstitialAd`. Close, no-fill, invalid activity, and thrown-exception paths all converge on the same callback-once continuation, after which the original navigation block runs.

For cold start, SplashActivity initializes and displays consent through the SDK, requests SDK preload when consent handling completes, and calls the mediator at the current splash ad decision point. The mediator waits for a fresh SDK App Open cache entry and displays it through `com.pdffox.adv.adv.ShowOpenAd.showOpenAd`, matching PhoneTracker's public 1.0.10 integration. Ad closure, timeout, or playback failure releases the splash flow to its existing progress completion and navigation path.

For foreground return, `AdvertiseSdk` owns lifecycle detection and App Open playback. Existing external-settings flows that must not trigger an App Open ad use the SDK suppression controls when those flows exist in FindMyPhone.

## Error Handling

An unavailable or destroyed Activity prevents the SDK call and does not run UI work on that Activity. A blank interstitial area key skips playback and continues the business action. Consent and playback exceptions are logged and treated as an unavailable ad. Callback guards prevent duplicate SDK callbacks from navigating twice. Splash retains consent, cache-wait, foreground-wait, and global timeouts so a missing SDK callback cannot block startup indefinitely.

## Native Ad Preservation

`ShowNativeAd`, `NativeAdActivity`, native view binding, native pool selection, native refresh timing, native limits, and native analytics are outside the behavioral migration. Mechanical edits to shared pool or loader files are limited to removing App Open/interstitial branches and imports. Native behavior is covered by focused tests or compilation checks before and after the cleanup.

## Testing And Verification

Unit tests cover callback-once behavior, blank area-key fallback, SDK exceptions, unavailable activities, duplicate interstitial requests, SDK consent delegation, App Open cache freshness, and splash foreground-open suppression. Existing native-ad tests, if present, remain unchanged. Verification includes repository-wide searches proving no application-owned App Open/interstitial loader or Google `AppOpenAd`/`InterstitialAd` reference remains, followed by unit tests and `:app:assembleDebug`.

## Acceptance Criteria

- All existing App Open and interstitial placement timing and area-key values remain available.
- App Open and interstitial playback is performed only through `advertise:1.0.10` APIs.
- Splash UMP consent initialization and form display use the advertise SDK APIs.
- Project-owned App Open and interstitial loading, pools, limits, and playback implementations are removed.
- Native ad behavior and application-owned native ad code remain intact.
- Ad failure never blocks the original navigation action or triggers it more than once.
- Unit tests and the Debug build complete successfully.
