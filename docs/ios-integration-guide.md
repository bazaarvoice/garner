# iOS integration guide

Swift package `https://github.com/bazaarvoice/garner.git`, iOS 15 or later, Xcode 16 or later.

## Add the package

In Xcode, **File → Add Package Dependencies…**, enter `https://github.com/bazaarvoice/garner.git`
and pick **Up to Next Major Version** from `1.1.0`. Add the products each target needs:

| Product | Add it to | What it is |
|---|---|---|
| `BVGarner` | The app | The SDK: the `BVPush` facade, notification handling and the review presenter. It links `BVGarnerKit`, the SDK engine as a prebuilt XCFramework. |
| `BVGarnerFirebase` | The app, if it uses Firebase Messaging | `BVPushFirebase`: forwards the APNs and FCM tokens to the SDK. It doesn't link Firebase itself. |
| `BVGarnerNotificationService` | A Notification Service Extension (optional) | `BVNotificationServiceExtension`, a base class for an extension that also handles your own pushes. |

In a `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/bazaarvoice/garner.git", from: "1.1.0"),
],
targets: [
    .target(
        name: "MyApp",
        dependencies: [
            .product(name: "BVGarner", package: "garner"),
            .product(name: "BVGarnerFirebase", package: "garner"),
        ]
    ),
]
```

## Project setup

- **Capabilities:** *Push Notifications*, and *Background Modes* → *Remote notifications*.
- **App Attest:** the SDK attests the device with App Attest when it registers. Its app
  identifier is `<TEAM_ID>.<bundleId>` (your 10-character Apple Team ID, a dot, the bundle id).
  Set it in `Info.plist` as `BVPushAppIdentifier`; without it the SDK works it out from the app's
  Keychain access group. The same identifier must be set up for your app in Bazaarvoice Portal.
- **Firebase:** your app adds Firebase Messaging and its `GoogleService-Info.plist` itself and
  calls `FirebaseApp.configure()`. The code below assumes method swizzling is off
  (`FirebaseAppDelegateProxyEnabled` = `NO` in `Info.plist`), so the app forwards the tokens.
- **App ID:** the 8-digit id that Bazaarvoice Portal shows for your app. An id that isn't 8
  digits leaves the SDK dormant with an error, never a crash.

## Start the SDK and forward tokens

```swift
import BVGarner
import BVGarnerFirebase
import FirebaseCore
import FirebaseMessaging
import UIKit

final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        BVPushUserNotificationCenterProxy.install()
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        BVPush.configure(appId: 10000001)
        BVPush.requestNotificationAuthorization()
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
        BVPushFirebase.didRegisterForRemoteNotifications(deviceToken: deviceToken)
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        Messaging.messaging().appDidReceiveMessage(userInfo)
        // Completes with .noData for pushes that are not from Bazaarvoice.
        BVPushBackground.didReceiveRemoteNotification(userInfo, completion: completionHandler)
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        BVPushFirebase.didReceiveRegistrationToken(fcmToken)
    }
}
```

In a SwiftUI app, attach the delegate with `@UIApplicationDelegateAdaptor(AppDelegate.self)`.

- `BVPushUserNotificationCenterProxy.install()` shows Bazaarvoice pushes in the foreground and
  handles taps. Everything else goes on to the notification-centre delegate that was installed
  before it.
- `BVPush.requestNotificationAuthorization()` asks for permission. If you ask yourself, call
  `BVPush.refreshNotificationPermission()` afterwards.

## Sign-in and sign-out

```swift
try BVPush.linkIdentity(userId: userId, email: email) { reason in
    // nil: signed in or the login ran. Otherwise the step the SDK waits for, for example
    // BVPush.loginWaitNotRegistered or BVPush.loginWaitPushToken; it logs in by itself later.
}
BVPush.unlinkIdentity()
```

The email is the only link key. The SDK sends it to the Garner backend once, keeps it in memory
only until the first login succeeds, and then keeps only the backend's digest in the Keychain.

## Notification Service Extension and App Group (optional)

The SDK works without an extension. If your app has a Notification Service Extension, subclass
`BVNotificationServiceExtension` (product `BVGarnerNotificationService`) or call its
`bvIsOurs(_:)` helper and keep your own logic for other pushes. To share SDK state with the
extension, add an App Group to both targets and pass it to the SDK:

```swift
BVPush.configure(appId: 10000001, options: BVPush.Options(appGroup: "group.com.example.app"))
```

## React Native

There's no React Native package; the app uses this SDK in its `ios/` project:

- Open `ios/<YourApp>.xcworkspace` and add the package to the app target as in
  [Add the package](#add-the-package). Swift packages and CocoaPods work in the same project.
- Put the calls from [Start the SDK and forward tokens](#start-the-sdk-and-forward-tokens) in the
  app's `AppDelegate`. If it is Objective-C, put them in a small Swift file that it calls.
- Call `BVPush.linkIdentity` / `BVPush.unlinkIdentity` from a small native module that your
  JavaScript calls on sign-in and sign-out.
- If a React Native Firebase library handles messaging too, make sure the APNs token, the FCM
  token and remote notifications still reach the `BVPushFirebase` and `BVPushBackground` calls.
- With Expo, use a development build (`npx expo prebuild`); Expo Go can't load native SDKs.

## Privacy manifest

`BVGarner` ships a `PrivacyInfo.xcprivacy` (device ID, user ID and email, linked to the user, for
app functionality, no tracking). See [Privacy](privacy.md).

## Check the integration

- `BVPushVersion.version` is the SDK version.
- `BVPush.diagnostics()` shows whether the device is registered, whether the push token is
  synced, the identity phase and the last error. It holds no secrets.
- `BVPush.observeState { }` and `BVPush.observeEvents { }` report changes as they happen.

The simulator gets no FCM token, so use an iPhone for real pushes. See
[Troubleshooting](troubleshooting.md) when something doesn't arrive, and the
[API reference](api-reference.md) for every call.
