# API reference

What a host app calls, on Android (`com.bazaarvoice:bv-garner-sdk-android`, class
`com.bazaarvoice.push.BVPush`) and iOS (Swift package products `BVGarner` and
`BVGarnerFirebase`). Setup steps: [Android guide](android-integration-guide.md),
[iOS guide](ios-integration-guide.md).

## Host app API

There's no "register" call. Once the app starts the SDK and a push token exists, the SDK registers
the device with the Garner backend by itself: attestation (Play Integrity / App Attest),
device registration, token sync and session refresh all run in the background. Android passes the
FCM token on automatically; on iOS the app forwards tokens and pushes.

iOS sources: `bv-push-sdk/ios/Sources/BVGarner/BVPush.swift`,
`bv-push-sdk/ios/Sources/BVGarner/IOSNotificationHandler.swift` and
`bv-push-sdk/ios/Sources/BVGarnerFirebase/BVPushFirebase.swift`. Android sources ship in the
artifact's sources jar.

### Required

| # | Purpose | Android (`BVPush`) | iOS |
|---|---|---|---|
| 1 | **Start the SDK**, once at launch. This triggers device registration. | `BVPush.init(context, appId, options)`, or no code: manifest meta-data `com.bazaarvoice.push.APP_ID` (+ optional `com.bazaarvoice.push.BASE_URL`) and the SDK starts itself | `BVPush.configure(appId:options:)` in `didFinishLaunching` |
| 2 | **Project values** come from the app, never the SDK | Manifest meta-data `com.bazaarvoice.push.CLOUD_PROJECT_NUMBER` (Play Integrity; give it as a string resource) | `Info.plist` `BVPushAppIdentifier` = `TEAMID.bundleId` (App Attest). If it's missing, the SDK works it out from the Keychain. |
| 3 | **Push token** | Automatic: the SDK has its own `FirebaseMessagingService` | `BVPushFirebase.didReceiveRegistrationToken(fcmToken)` in `MessagingDelegate`, and `BVPushFirebase.didRegisterForRemoteNotifications(deviceToken:)` (plus `Messaging.messaging().apnsToken = token`) |
| 4 | **Notification permission** | The app asks for `POST_NOTIFICATIONS` (Android 13+); the SDK only declares it | `BVPush.requestNotificationAuthorization()`, or your own request followed by `BVPush.refreshNotificationPermission()` |
| 5 | **Receive pushes** | Automatic | `BVPushBackground.didReceiveRemoteNotification(userInfo, completion:)` from the AppDelegate, plus `BVPushUserNotificationCenterProxy.install()` for foreground banners and taps |
| 6 | **Login**: link the user to the device | `BVPush.linkIdentity(userId, email)` | `try BVPush.linkIdentity(userId:email:completion:)` |
| 7 | **Logout** | `BVPush.unlinkIdentity(userId)` | `BVPush.unlinkIdentity(userId:completion:)` (user id optional) |

- **Login:** the email is the only link key. The SDK sends it to the backend once and keeps only
  the backend's digest.
- **Order:** the SDK always runs register → push token → login. It never sends a login without a
  device ID, or before the device's push token is sent. You can call `linkIdentity` any time: the
  SDK keeps the user and logs in once both steps are done.
- **Login waiting:** when `linkIdentity` can't log in yet, the SDK tells the app which step it waits
  for: `NOT_REGISTERED` (no device ID yet) or `WAITING_FOR_PUSH_TOKEN` (push token not sent yet).
  Android: `BVPush.events` gets `BVPushEvent.IdentityPending(reason)`. iOS: the `linkIdentity`
  completion gets the reason (`BVPush.loginWaitNotRegistered` / `loginWaitPushToken`; nil = signed in
  or the login ran), and `observeEvents` gets `.identityPending(reason:)`. Nothing to retry: the
  SDK logs in by itself once the step is done.
- **Progress and problems:** after every sync both platforms report what changed, by the same
  rules: `Registered`, `RegistrationBlocked(reason)`, `PushTokenSynced`, `IdentityLinked` +
  `SignedIn`, `SessionRefreshed`, `SignedOut` (+ `IdentityUnlinked` after a logout),
  `PreferenceSynced(optedOut)`, and `Error("RECONCILE_FAILED")` when a sync throws. Android:
  `BVPush.events`; iOS: `observeEvents`. The last error (Android `BVPush.state.lastError`, iOS
  `observeState`, both `diagnostics().lastError`) is the block reason while registration is
  blocked, else the last sync failure, else null. Android's `BVPush.state.deviceId` also updates
  after every sync, not only at start.
- **Logout:** removes the user and the session. The device stays registered, so test pushes still
  arrive, but real pushes for that user stop.
- **Android app with its own `FirebaseMessagingService`:** Android delivers to one service only, so
  forward `BVPush.onNewToken(context, token)` and `BVPush.handleRemoteMessage(context, data)`.
  Otherwise the SDK misses tokens and pushes (`diagnostics().fcmConflict` is `true`).

### Optional

| Purpose | Android | iOS |
|---|---|---|
| Opt out / opt in to pushes | `optOut()` / `optIn()` | `optOut()` / `optIn()` (with completion) |
| Quiet hours (default 20:00–08:00; pushes are held until they end) | `setQuietHours(startHour, startMinute, endHour, endMinute)`, `disableQuietHours()`, `resetQuietHours()` | Same, plus `setQuietHours(QuietWindow)` |
| Handle a tapped push yourself (otherwise the SDK opens the review link in a Custom Tab / Safari) | `setNotificationTapHandler { id -> true }` | `setNotificationTapHandler { id in true }` |
| Watch state and events | `BVPush.state` (StateFlow), `BVPush.events` (SharedFlow) | `observeState { }`, `observeEvents { }` |
| Device ID and support info | `BVPush.deviceId`, `diagnostics()` | `BVPush.deviceId`, `diagnostics()` |

### Smallest integration

```kotlin
// Android: Application.onCreate (or only the manifest APP_ID meta-data)
BVPush.init(this, 10000001)
// login / logout
BVPush.linkIdentity(userId, email)
BVPush.unlinkIdentity(userId)
```

```swift
// iOS: AppDelegate
import BVGarner
import BVGarnerFirebase

BVPush.configure(appId: 10000001)
BVPushUserNotificationCenterProxy.install()
BVPush.requestNotificationAuthorization()
// APNs token:  Messaging.messaging().apnsToken = token
//              BVPushFirebase.didRegisterForRemoteNotifications(deviceToken: token)
// FCM token:   BVPushFirebase.didReceiveRegistrationToken(fcmToken)
// remote push: BVPushBackground.didReceiveRemoteNotification(userInfo, completion: completionHandler)
// login / logout
try BVPush.linkIdentity(userId: userId, email: email)
BVPush.unlinkIdentity()
```

Full setup, including Firebase and App Attest: the [Android guide](android-integration-guide.md)
and the [iOS guide](ios-integration-guide.md).

### Limits on Android today

- `openReview(id)` always returns `false`; it isn't implemented yet. Taps still open the review link
  through the SDK's tap handling.
- `onNewToken` and `setPushToken` make the SDK sync again. It reads the token from Firebase itself
  and uses the one passed in only when Firebase has none.
