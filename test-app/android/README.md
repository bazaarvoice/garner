# BV Garner Test App (Android)

BV Garner is the Android test app for the BV Garner SDK. It runs the SDK with your
Firebase project and your Portal push sender profile, so you can see a push arrive on a device
or emulator before wiring the SDK into your own app.

The app builds the SDK from source: `settings.gradle.kts` includes `../../bv-push-sdk` as a
Gradle composite build, so the `com.bazaarvoice:bv-garner-sdk-android` dependency is the code in
this repository, and an SDK change shows up in the next run. Your own app takes the same
artifact from Maven Central instead.

## What you need

1. **A Firebase project with an Android app** whose package name you choose (the default is
   `bv.garner.test.app`). Download its `google-services.json`.
2. **A push sender profile in Portal** for the same package name (Android). Upload your
   Firebase service-account key and your Play Integrity service-account key, then note the
   **App ID** Portal shows.
3. **Android Studio or IntelliJ IDEA** with the Android plugin, JDK 17, and an emulator with a
   **Google Play** system image (FCM needs Play services), or a physical Android device.

## Set up

```sh
cd test-app/android
cp client.properties.example client.properties
```

Fill in `client.properties`:

| Key | Value |
|---|---|
| `appName` | the name shown under the app icon (optional, default `BV Garner`) |
| `packageName` | the package name of your Firebase Android app and your sender profile (default `bv.garner.test.app`) |
| `appId` | the App ID from your Portal sender profile |
| `baseUrl` | the Garner backend endpoint: production (the default, `https://push.prod.bazaarvoice.com`), or the QA endpoint your Bazaarvoice contact gives you |
| `googleServicesJson` | path to your `google-services.json` |
| `cloudProjectNumber` | the Google Cloud project number linked to your app in Play Console (*App integrity → Play Integrity API*), digits only. Optional: without it the SDK asks Play Integrity with no project number |

`client.properties` and the copied `app/google-services.json` are git-ignored. The build needs
a `google-services.json` (the Google services Gradle plugin fails without one).

## Run

Open `test-app/android` in Android Studio or IntelliJ and run the `app` configuration, or from
a terminal: `./gradlew :app:installDebug`. Allow notifications when the app asks.
The launcher icon is the Bazaarvoice *b:@* mark (vector resources in `app/src/main/res`). The tab
and checklist icons are Material icons, one family (outlined), as vector drawables:
`app/src/main/res/drawable/ic_tab_*.xml` and `ic_mark_*.xml` (check, exclamation mark, three dots).

The app has four tabs:

| Tab | What it shows |
|---|---|
| **Home** | Is this device ready? A banner (*n of 5 checks pass*) and a checklist: device registered, push token sent, user signed in, notifications allowed, quiet hours. Each problem has a button that fixes it or takes you there. |
| **Account** | **Sign in** with the user id and the consumer email (both required). Once signed in: the user, the session and **Sign out**. Always: **Receive pushes** (opt in / opt out) and **Quiet hours** (Off, Default, Set hours). |
| **Inbox** | Every push the SDK reported on this device, newest first: title and text, time, and what happened (shown, held, hidden and why, tapped). Filters: All, Shown, Held, Hidden. Kept on the device (newest 500) until you tap **Clear**. Tap a push to copy its notification id. |
| **Details** | Every data point an integrator asks for (device id, FCM token, app id, package, endpoint, user ID, session, opt-out, quiet hours, last error), each with **Copy**; **Copy all (JSON)** and **Share**; recent SDK events; the raw inspector text. |

Try a push:

1. **Test push from Portal.** On Home, tap **Copy device ID** and paste it into Portal's
   *Send test push*. The push appears on the device and in Inbox; tapping it opens the review
   page. A test push shows even when no user is signed in.
2. **A real push.** On Account, enter a user id and the consumer email the push is addressed
   to, then tap **Sign in**. When *User signed in* gets a green check on Home, pushes for that email reach
   this device.
3. **Testing at night.** By default pushes are held between 20:00 and 08:00 device time and
   shown when the window ends. On Account, set **Quiet hours** to **Off** to show them at any
   time, or **Set hours** for your own window.

Inbox notes: titles are read from the notification the SDK posted. A push that was hidden (for
example *no user signed in* or *opted out*) is never posted, so Inbox shows only its id.

The device id is also written to logcat: `adb logcat -s BVPushDriver:I | grep DEVICEID`.

## When something goes wrong

**Last error** in Details tells you what Garner backend answered. While registration is blocked it
shows the block reason, and Recent SDK events shows *Registration blocked: <reason>*; otherwise it
shows the last failed sync, or `none`.

| error | Meaning |
|---|---|
| 401 / `APP_NOT_ACTIVE` | `appId` is wrong, or the sender profile is not active |
| `APP_IDENTIFIER_MISMATCH` | `packageName` differs from the package on the sender profile |
| 403 / `ATTESTATION_REJECTED` | Play Integrity rejected the device. A locally built app on an emulator cannot pass it; the endpoint must have attestation turned off for testing, or install the app through a Play Console testing track on a real device |
| 503 | the endpoint is down or its device API is disabled |

Play Integrity problems on the device are in logcat: `adb logcat -s BVPush` shows
`BVPush attestation failed code=<n>`, where `<n>` is Play's `IntegrityErrorCode` (for example
`-16` for a wrong `cloudProjectNumber`).

No device id and no FCM token usually means the emulator image has no Google Play, or
`google-services.json` belongs to a different package.

## Tests

The screen logic (checklist, Inbox history, Details report, sign-in rules, quiet hours text)
is plain Kotlin with JVM unit tests: `./gradlew :app:testDebugUnitTest`. Bazaarvoice runs them,
builds the debug app and takes a screenshot of every tab on an emulator for each change.

Debug builds open a tab from adb: `adb shell am start -n bv.garner.test.app/.MainActivity --es tab INBOX`
(`HOME`, `ACCOUNT`, `INBOX`, `DETAILS`).

Debug builds also take driver commands from adb, each with its own `appId` and `baseUrl`
(`INIT`, `LINK` with `userId` and `email`, `QUIET` with `startHour`, `startMinute`, `endHour`
and `endMinute`, `DUMP`), and log one `BVPushDriver STATE …` line per command:

```sh
adb shell am start -n bv.garner.test.app/.MainActivity \
  --es cmd LINK --ei appId <app-id> --es baseUrl https://push.prod.bazaarvoice.com \
  --es userId tester-001 --es email tester@example.com
adb logcat -s BVPushDriver:I
```
