# Changelog

All notable changes to the BV Garner SDK (iOS & Android) and the BV Garner Test Apps. Android and
iOS share one version number. Versions follow [Semantic Versioning](https://semver.org).

## 1.1.0

First public release.

### Added

- **Android SDK:** `com.bazaarvoice:bv-garner-sdk-android` on Maven Central (`minSdk 24`). Kotlin
  package `com.bazaarvoice.push`, API `BVPush`.
- **iOS SDK:** the Swift package `https://github.com/bazaarvoice/garner.git` (iOS 15) with the
  products `BVGarner`, `BVGarnerFirebase` and `BVGarnerNotificationService`. Its engine,
  `BVGarnerKit.xcframework`, is attached to the GitHub release of the same version.
- What the SDK does: registers the device (Play Integrity on Android, App Attest on iOS), keeps
  the push token in sync, links the signed-in consumer, shows review-request pushes with opt-out
  and quiet hours, and opens the review page on tap. It starts with your 8-digit App ID.
- **BV Garner Test Apps** for Android and iOS: see a push arrive with your own Firebase project
  and Portal push sender profile.
- Docs in `docs/`: Android and iOS integration guides (also for React Native apps), API
  reference, privacy, troubleshooting.
