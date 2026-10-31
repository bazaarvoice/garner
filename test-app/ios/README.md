# BV Garner Test App (iOS)

SwiftUI host app for the BV Garner SDK, with the same four tabs as the Android app
(`../android`): **Home** (is this device ready, 5-item checklist), **Account** (sign in,
opt in/out, quiet hours, sign out), **Inbox** (every push the SDK reported, with filters),
**Details** (every value with Copy, Copy all as JSON, Share, recent SDK events, raw
inspector).

| | |
|---|---|
| App name | BV Garner |
| Bundle ID | `bv.garner.test.app` |
| iOS | 16.0 or later (the SDK package supports 15.0) |
| SDK | Swift package `https://github.com/bazaarvoice/garner`, product `BVGarner` (+ `BVGarnerFirebase`), **Up to Next Major** from the current release |
| Push token | Firebase Messaging (`firebase-ios-sdk`, Up to Next Major 12.0.0) |

Real pushes on an iPhone need Apple, Firebase and Garner backend setup first:
see **[NEXT-STEPS.md](NEXT-STEPS.md)**.

## Layout

| Path | Contents |
|---|---|
| `BVGarner.xcodeproj` | App target `BVGarner`, unit tests `BVGarnerTests`, shared scheme `BVGarner`. Takes the SDK as a released package version. |
| `BVGarner.xcworkspace` | The project plus this repository's root package: builds against the checkout (use this one) |
| `BVGarner/` | Swift sources, `Info.plist`, `BVGarner.entitlements` |
| `BVGarner/Assets.xcassets` | App icon: the Android launcher icon (navy b:@ mark on white, `../android/app/src/main/res/mipmap/ic_launcher.xml`) as a 1024 px PNG |
| `BVGarnerTests/` | XCTest unit tests for the screen logic (same rules as the Android JVM tests) |
| `Config/Base.xcconfig` | Every build value (app id, Garner backend URL, team, bundle id) |
| `client.xcconfig.example` | Copy to `client.xcconfig` (git-ignored) for your values |

## How the app gets the SDK

The project adds the SDK like any other dependency, the way the Android app adds a
Maven artifact with a version:

| Android (`build.gradle.kts`) | iOS (`BVGarner.xcodeproj`) |
|---|---|
| `implementation("com.bazaarvoice:bv-garner-sdk-android:1.1.0")` | Package `https://github.com/bazaarvoice/garner`, rule *Up to Next Major Version* from the current release, product `BVGarner` |

The package is the repository root `Package.swift`. Its Swift targets are the sources in
`bv-push-sdk/ios/Sources`; its `BVGarnerKit` binary target is the Kotlin engine
(`bv-push-sdk/sdk`) as an XCFramework. A released version takes BVGarnerKit from the GitHub
release of that version (`url:` and `checksum:`); a build from source takes it from
`bv-push-sdk/sdk/build/XCFrameworks/debug/BVGarnerKit.xcframework` (`path:`).

To get a newer release into the project: **File → Packages → Update to Latest Package Versions**
(if Xcode keeps the old one: **File → Packages → Reset Package Caches**). Check: the top bar chip
and Details → **SDK version** show the new version.

To build against your checkout instead (SDK work), open **`BVGarner.xcworkspace`**. It also
contains the repository root as a local package, and Xcode then uses that instead of the
release: the Swift sources of the checkout, and BVGarnerKit as the root `Package.swift` says.
To run engine (Kotlin) changes too, build the XCFramework (below) and give the `BVGarnerKit`
binary target `path: "bv-push-sdk/sdk/build/XCFrameworks/debug/BVGarnerKit.xcframework"`.
Xcode matches the local package to the remote one by folder name, so the checkout folder must
be called `garner`.

The SDK has no app ids, team ids, hosts or project numbers in it. The app hands them over
from its build settings:

```swift
BVPush.configure(appId: AppConfig.appId, options: BVPush.Options(baseUrlOverride: AppConfig.baseUrl, ...))
```

## Run in the simulator

You need a Mac with Xcode 16.2 or later, and JDK 17 to build the engine from source.

```sh
cd garner/bv-push-sdk
./gradlew :sdk:assembleBVGarnerKitDebugXCFramework      # engine from source: once, and after Kotlin changes
open ../test-app/ios/BVGarner.xcworkspace
```

In Xcode, pick scheme **BVGarner** and an iPhone simulator, then press **⌘R**. By default the
app talks to the production Garner backend (`https://push.prod.bazaarvoice.com`, the SDK's
built-in endpoint); set `BV_BASE_URL` (below) for another one. Until `BV_APP_ID` is the App ID
of your Portal push sender profile, Home shows *Not registered*, which is expected.

The simulator gets no FCM token, so *Push token sent* stays open there. Use an iPhone for
real pushes ([NEXT-STEPS.md](NEXT-STEPS.md)).

If package resolution fails (for example *failed downloading …BVGarnerKit.xcframework.zip…
404*, or binary target `BVGarnerKit` not found):

1. With a `path:` binary target, build the XCFramework (above) first.
2. Open the `.xcworkspace`, not the `.xcodeproj`, from a checkout folder called `garner`.
3. **File → Packages → Reset Package Caches**. From the command line, delete
   `~/Library/Caches/org.swift.swiftpm` and the DerivedData folder instead.

## Configuration

Copy `client.xcconfig.example` to `client.xcconfig` and set what you need. It's git-ignored and
overrides `Config/Base.xcconfig`.

| Setting | Default | Used for |
|---|---|---|
| `BV_APP_ID` | `10000001` | Portal app id passed to `BVPush.configure` (`Info.plist` `BVAppId`) |
| `BV_BASE_URL` | `https://push.prod.bazaarvoice.com` | Garner backend (`BVBaseUrl`). Write `https:/$()/host`: `//` starts a comment in xcconfig. Plain HTTP (a local backend) turns on `allowInsecureLocalhost`. |
| `BV_TEAM_ID` | empty | Signing team; also builds `BVPushAppIdentifier` = `TEAMID.bundleId` for App Attest |
| `BV_BUNDLE_ID` | `bv.garner.test.app` | Bundle ID |
| `BV_APP_NAME` | `BV Garner` | Home screen name |
| `BV_APP_GROUP` | empty | Optional App Group for the SDK's settings |
| `BV_APS_ENVIRONMENT` | `development` | `aps-environment` entitlement (`production` for TestFlight) |
| `BV_APP_ATTEST_ENVIRONMENT` | `production` | App Attest environment the Garner backend verifies |
| `BV_GOOGLE_SERVICE_INFO_PLIST` | `BVGarner/GoogleService-Info.plist` | Firebase config file. It's copied into the app at build time; without it there's no FCM token. |

## Tests

From the repository root:

```sh
(cd bv-push-sdk && ./gradlew :sdk:assembleBVGarnerKitDebugXCFramework)
xcodebuild test -workspace test-app/ios/BVGarner.xcworkspace -scheme BVGarner \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

The tests cover the checklist, quiet hours, sign-in form, app info, inbox records,
history file codec, labels and the Details report. The app doesn't start the SDK while tests
run. Bazaarvoice runs them, and takes a screenshot of every tab on a simulator, for each change.

## Debug launch arguments

Debug builds read launch arguments (Xcode: **Product → Scheme → Edit Scheme → Arguments**, or
`xcrun simctl launch`). Release builds ignore them.

| Argument | Effect |
|---|---|
| `-tab HOME\|ACCOUNT\|INBOX\|DETAILS` | Opens that tab |
| `-screenshots YES` | Asks for notifications provisionally (no prompt). For screenshots. |
| `-cmd INIT [-appId N] [-baseUrl URL]` | Configures (default `http://localhost:8081`, a local backend), logs `BVPushDriver STATE phase=after-INIT …` |
| `-cmd LINK -userId U -email E` | Configures, then links the identity, logs `after-LINK` |
| `-cmd QUIET [-startHour 23 -endHour 8]` | Configures (`:8082`), sets quiet hours, logs `after-QUIET` |
| `-cmd DUMP` | Logs the current state (`manual`) |

Read the lines with `xcrun simctl spawn booted log stream --predicate 'eventMessage CONTAINS "BVPushDriver"'`.
The app also logs `BVPushDriver DEVICEID <id>` once registration succeeds.

## Differences from Android

- **Details → APP** shows *Bundle ID* instead of *Package*. **Details → DEVICE** also shows the
  *APNs token*.
- **Home → Allow** asks for permission the first time. After that it opens the app's
  notification settings.
- iOS has no notification channels, so *Hidden: notification channel off* doesn't occur.
- Data pushes are shown by the SDK as local notifications (`bv-<id>`). Alert pushes are shown
  by iOS, and the SDK records them as shown.
