# Android integration guide

SDK `com.bazaarvoice:bv-garner-sdk-android:1.1.0` on Maven Central, `minSdk 24`, Kotlin 2.4.

## Dependency

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.bazaarvoice:bv-garner-sdk-android:1.1.0")
}
```

Transitives (OkHttp, Play Integrity, Firebase Messaging, AndroidX Core /
Browser / Work / Lifecycle / Startup) come with the artifact (size budget:
release-APK growth ≤ 1.5 MB with R8; unshrunk AAR ~270 KB).

## Init

One line, usually in `Application.onCreate` (main-thread cost is a validation
plus an enqueue; everything else runs off-thread):

```kotlin
BVPush.init(application, appId, BVPushOptions())
```

`appId` is the 8-digit id Bazaarvoice Portal shows. An id that isn't 8 digits
leaves the SDK dormant and emits an error event — never an exception. Without
an `Application` subclass, the App Startup initializer auto-inits from the
manifest meta-data (no-code mode):

```xml
<meta-data
    android:name="com.bazaarvoice.push.APP_ID"
    android:value="10000001" />
```

The library manifest already declares the messaging service
(`exported=false`), the tap trampoline, permissions (`INTERNET`,
`ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`) and
the Custom Tabs / browser queries.

## Play Integrity

Device registration carries a Play Integrity token. Give the SDK the Google Cloud project
number linked to your app in Play Console (*Test and release → App integrity → Play Integrity
API*) as a **string resource**: a plain 12-digit `android:value` is stored as a float and
loses digits.

```xml
<!-- res/values/strings.xml: <string name="bv_cloud_project_number">123456789012</string> -->
<meta-data
    android:name="com.bazaarvoice.push.CLOUD_PROJECT_NUMBER"
    android:value="@string/bv_cloud_project_number" />
```

Without it the SDK sends the request with no project number. A value that is not digits is
ignored with a warning. Failures are written to logcat with Play's error code:
`adb logcat -s BVPush` shows `BVPush attestation failed code=<n>`.

## Firebase

Add the client's `google-services.json`. The SDK fetches the FCM token
itself (`autoFetchFcmToken`, default true). If the host owns Firebase
Messaging, keep its service and forward BV messages:

```kotlin
override fun onMessageReceived(message: RemoteMessage) {
    if (BVFirebaseMessagingService.handleRemoteMessage(this, message.data)) return
    // ... host handling for non-BV messages
}
```

If the SDK's service wins the `MESSAGING_EVENT` race, `diagnostics()`
reports `fcmConflict`; non-BV traffic is then left alone by the SDK.

## Identity

```kotlin
BVPush.linkIdentity(userId, email)          // the email is the only link key
BVPush.unlinkIdentity(userId)
```

The email is sent as typed, over TLS, to the Garner backend only. The backend
normalises and hashes it and stores only the digest. The SDK holds the
address in memory only, until the first login succeeds. The backend's login
answer carries `emailAddressHash`; the SDK keeps that digest with the tokens in
the encrypted secure store (Keystore) and sends it, not the address, when it
must log in again (both tokens expired). `unlinkIdentity` deletes it. The
plain address is never written to disk. The email
is the only key that links a consumer to a device. The first login links the consumer and opens the session in one
call; repeating the same user costs zero network calls.

## Preference, quiet hours, taps

```kotlin
BVPush.optOut() / BVPush.optIn()
BVPush.setQuietHours(22, 0, 7, 0) / BVPush.disableQuietHours() / BVPush.resetQuietHours() // 20:00–08:00 default
BVPush.setNotificationTapHandler { notificationId -> /* true = consumed */ }
BVPush.openReview(notificationId)
```

Taps open the review URL in a Custom Tab (system browser fallback); closing
returns to the app. HTTP(S) review URLs outside the allowlist open the app.

## Permissions and backup

Request `POST_NOTIFICATIONS` at runtime (Android 13+). When revoked or the
channel is blocked, pushes are suppressed locally and reported — nothing
posts. Exclude the SDK stores from Auto Backup (`allowBackup` rules); a
restore without the install marker purges credentials and re-registers.

## React Native

There's no React Native package; the app uses this SDK in its `android/` project:

- Add `implementation("com.bazaarvoice:bv-garner-sdk-android:1.1.0")` to the `dependencies` of
  `android/app/build.gradle`.
- Call `BVPush.init(this, appId)` in `MainApplication.onCreate`, or use the manifest meta-data
  from [Init](#init).
- Call `BVPush.linkIdentity` / `BVPush.unlinkIdentity` (and `optOut` / `optIn` if you offer them)
  from a small native module that your JavaScript calls on sign-in and sign-out.
- If a React Native Firebase library handles messaging too, forward Bazaarvoice messages from its
  messaging service as in [Firebase](#firebase).
- With Expo, use a development build (`npx expo prebuild`); Expo Go can't load native SDKs.

## Observing and debugging

```kotlin
BVPush.state        // StateFlow: initialized, deviceId, lastError
BVPush.events       // SharedFlow<BVPushEvent> (local only, never uploaded)
BVPush.diagnostics() // no secrets; safe to attach to support tickets
BVPush.reset()       // purge everything (consent withdrawal)
Logger.level = Level.DEBUG // redacted by construction
```

HTTPS only. Plain HTTP works only in debuggable builds with
`allowInsecureLocalhost = true` (a local test backend).
