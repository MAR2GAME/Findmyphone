# SplashActivity Logic Sync Design

## Goal

Synchronize the runtime logic of `D:\work\LocShare\app\src\main\java\com\locshare\locate\ui\activity\SplashActivity.kt` into FindMyPhone while retaining FindMyPhone's visual layout, package structure, business destinations, and advertise SDK integration boundary.

## Source Of Truth

LocShare is authoritative for the Splash startup state machine and lifecycle behavior. FindMyPhone remains authoritative for package names, application singleton, utility package locations, destination activities, URLs, analytics implementation, native-ad loader, and `AdvertisePageMediator` integration.

The migration adapts type and import names but does not selectively retain a conflicting FindMyPhone startup sequence.

## Startup Flow

`initializeActivity` performs these operations in LocShare order:

1. Record wall-clock and elapsed-realtime startup timestamps.
2. Persist `AppConfig.START_TIME`.
3. On the first launch only, clear `is_First` and send `my_first_open_time` through `LogUtil.setUser(..., "user_setOnce")` using FindMyPhone's existing `getDetailCurrentTime` helper.
4. Install back-press and legal-link handlers.
5. Disable foreground App Open ads while Splash owns the screen.
6. Mark `in_splash` true.
7. Check notification permission and start the Splash sequence.

Notification permission behavior, persistent-service startup, and permission analytics match LocShare. FindMyPhone's service class and constants remain unchanged.

## Consent And Progress State Machine

The synchronized activity uses LocShare's explicit jobs and one-shot state:

- `startupJob` prevents duplicate startup sequences.
- `openAdJob` owns the launch App Open request.
- `globalTimeoutJob` provides the 20-second fallback.
- `waitingJob` advances to the 80-percent waiting cap.
- `progressJob` advances the main progress from its current value to 100.
- `consentInitialized` and `hasStartedConsentInitialization` ensure consent initialization begins once and can be awaited with a six-second timeout.

If consent initialization succeeds, the consent form may be shown. A form that is actually shown is allowed to finish without an arbitrary form-display timeout. Launch ads are preloaded only after the consent stage, and existing FindMyPhone native-ad preloading remains active.

## App Open Ad And Lifecycle Behavior

The migration keeps the advertise SDK behind `AdvertisePageMediator` and adopts LocShare's flow:

- Poll cached launch ads for up to eight seconds and request one retry preload when no load is active.
- Hold visible progress below 100 while the ad can still load.
- Give a recently loaded ad its configured grace interval.
- Show an App Open ad only while Splash is resumed and has not returned from the background.
- Cancel the global startup timeout only after `SplashAdGate` reports that the ad was displayed.
- On backgrounding during Splash, suppress the next foreground App Open ad.
- Restore foreground App Open behavior after the destination activity resumes.
- Cancel every owned coroutine job when navigating or destroying Splash.

FindMyPhone's `SplashAdGate` stays in its current `utils` package and gains LocShare's `shouldCancelStartupTimeout()` behavior.

## Navigation And Analytics

Normal launch, push launch, onboarding skip flags, legal links, and push destination routes retain FindMyPhone's destination activity classes and extras. Their decisions match LocShare.

`splash_display` is recorded at the successful navigation decision using elapsed realtime, matching LocShare's single navigation event. The FindMyPhone-only on-destroy duration logger is removed to avoid a second or differently timed event.

## Preserved UI And Project Boundaries

The following files and behavior are not synchronized from LocShare:

- `app/src/main/res/layout/activity_splash.xml`
- `splash_bg.png`, `ic_logo`, launcher icons, colors, dimensions, and text positioning
- Splash activity Manifest declaration and launch mode
- FindMyPhone package name and application ID
- FindMyPhone page class locations and push route destinations
- FindMyPhone native-ad implementation

## Tests And Verification

The implementation is complete only when:

1. A source-contract test proves the LocShare startup markers exist in FindMyPhone and the layout file is unchanged.
2. LocShare's `SplashAdGateTest` behaviors pass against FindMyPhone's gate, including `shouldCancelStartupTimeout()`.
3. Existing advertise initialization and App Open migration tests continue to pass.
4. The full Debug unit-test suite passes with zero failures.
5. `:app:compileDebugKotlin` succeeds offline.
6. A final file audit confirms no Splash layout, image, or Manifest file was modified by this migration.

## Out Of Scope

- Synchronizing Splash visuals or assets.
- Changing advertise SDK configuration or Remote Config values.
- Refactoring onboarding destination activities.
- Modifying native-ad loading or display behavior outside the existing preload call.
