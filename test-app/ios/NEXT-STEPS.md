# BV Garner iOS: next steps to a real push on an iPhone

The app builds and its tests run on a simulator. A simulator gets no FCM token, so a real
push needs an iPhone plus one-time Apple, Firebase and Garner backend setup. Do the
steps in order. Each one ends with a check.

Placeholders: `<TEAM_ID>` is your Apple Team ID (developer.apple.com → **Membership**), and
`<firebase-project>` is the Firebase project the Garner backend sender uses (the same one as
the Android app).

| Value | Where it goes |
|---|---|
| Bundle ID `bv.garner.test.app` | Apple App ID, Firebase iOS app (`BV_BUNDLE_ID`) |
| `<TEAM_ID>` | `client.xcconfig` `BV_TEAM_ID` |
| `<TEAM_ID>.bv.garner.test.app` | Garner backend IOS binding. The app sends it as `BVPushAppIdentifier`. |
| Portal app id | `client.xcconfig` `BV_APP_ID` |
| Garner backend URL | `client.xcconfig` `BV_BASE_URL` |

Never commit `GoogleService-Info.plist`, `client.xcconfig`, `.p8` or `.p12` files. All are
git-ignored.

## 0. Get the SDK

Pick one:

- **Released version (like a client app, no Gradle or JDK):** open
  `test-app/ios/BVGarner.xcodeproj`. It takes the Swift package
  `https://github.com/bazaarvoice/garner` (product `BVGarner`) at the version shown under the
  project's **Package Dependencies**. After a newer release is out: **File → Packages → Update
  to Latest Package Versions**. If Xcode keeps the old one, **File → Packages → Reset Package
  Caches**.
- **Checkout (SDK work):** open `test-app/ios/BVGarner.xcworkspace` from a checkout folder
  called `garner`. It builds the Swift sources of the checkout. To run engine (Kotlin) changes
  too, build the XCFramework on a Mac with JDK 17
  (`cd bv-push-sdk && ./gradlew :sdk:assembleBVGarnerKitDebugXCFramework`) and point the
  `BVGarnerKit` binary target in the root `Package.swift` at it ([README.md](README.md)).

Check: **Product → Build** (⌘B) succeeds for scheme **BVGarner**, and in the running app
Details → **SDK version** shows the version you expect.

## 1. Apple Developer

You need a role with **Certificates, Identifiers & Profiles** access. If Xcode says you have no
permission, the team has
[Automatic Signing Controls](https://developer.apple.com/help/account/access/automatic-signing-controls)
on and an Admin has to do 1b–1d.

1. **Development certificate:** **Xcode → Settings → Accounts → (team) → Manage Certificates…
   → + → Apple Development**.
2. **App ID** `bv.garner.test.app` with **Push Notifications** and **App Attest**:
   [Register an App ID](https://developer.apple.com/account/resources/identifiers/add/bundleId).
   Xcode automatic signing creates it in step 5 if you skip this.
3. **APNs auth key** (for Firebase, not for the app):
   [Keys](https://developer.apple.com/account/resources/authkeys/list). Reuse an existing APNs
   key if the team still has its `.p8`. Otherwise **+**, check **Apple Push Notifications
   service (APNs)**, **Configure → Sandbox & Production**, then **Register → Download**. Note
   the **Key ID**. The `.p8` downloads only once: store it in the team's secrets store.
4. **iPhone:** Xcode registers it the first time you run on it (step 5), or add its UDID under
   [Devices](https://developer.apple.com/account/resources/devices/list).

Check: Keychain Access → **My Certificates** shows *Apple Development: (you)* with a private key.

## 2. Firebase

You need Editor or Owner on `<firebase-project>`.

1. **Project settings → General → Add app → Apple**, bundle ID `bv.garner.test.app` →
   **Register app**. Download **GoogleService-Info.plist** and skip the rest of the wizard.
2. Put the file at `test-app/ios/BVGarner/GoogleService-Info.plist` (git-ignored), or set
   `BV_GOOGLE_SERVICE_INFO_PLIST` to its path in `client.xcconfig`. The build copies it into
   the app.
3. **Project settings → Cloud Messaging → Apple app configuration**, pick the iOS app,
   **APNs Authentication Key → Upload**: the `.p8`, its Key ID and `<TEAM_ID>`. Upload it to
   both the development and production slots.

Check: the iOS app shows in Project settings with an APNs key in both slots.

## 3. Garner backend: IOS binding

The backend accepts a device only if the app's iOS binding matches what the app sends:
`<TEAM_ID>.bv.garner.test.app`.

In Portal onboarding for your app id, add the IOS platform with that app identifier, and set up
the sender with the `<firebase-project>` service account.

## 4. App settings

```sh
cp test-app/ios/client.xcconfig.example test-app/ios/client.xcconfig
```

Set `BV_TEAM_ID` and `BV_APP_ID`, and `BV_BASE_URL` for a backend other than production (the
default). Write URLs as `https:/$()/host`, because `//` starts a comment in xcconfig.

- **Production:** the App ID of your Portal push sender profile. `BV_BASE_URL` stays
  `https:/$()/push.prod.bazaarvoice.com` (the default); for QA, set the QA endpoint your
  Bazaarvoice contact gives you.
- **A backend on your Mac:** the iPhone can't reach `localhost`. Get the Mac's name with
  `scutil --get LocalHostName` and set `BV_BASE_URL = http:/$()/<mac-name>.local:8082`. Put the
  iPhone on the same Wi-Fi as the Mac, and allow the backend's process if the macOS firewall
  asks.

Check: `git status` doesn't list `client.xcconfig` (it's git-ignored).

## 5. Run on the iPhone

1. Connect the iPhone over USB and tap **Trust**.
2. Turn on **Settings → Privacy & Security → Developer Mode** and restart. The option appears
   after Xcode has seen the phone once.
3. In Xcode, pick scheme **BVGarner** and your iPhone. **Signing & Capabilities** must show no
   errors: automatic signing with your team registers the device and makes the profile.
4. Press **⌘R**. If the phone says *Untrusted Developer*, go to **Settings → General → VPN &
   Device Management** and trust it, then run again.
5. In the app, tap **Allow** on Home and allow notifications. For a backend on your Mac, also
   allow **Local Network**.

Check: Home shows *Ready: all 5 checks pass*. Details → **FCM token** has a value, and Home → **Copy device
ID** works.

## 6. Check Apple and Firebase (no Garner backend)

1. Details → **FCM token** → **Copy**.
2. Firebase console → **Messaging → New campaign → Notifications**, enter a title and text,
   **Send test message**, paste the token, **Test**.
3. Send it with the app in the background.

Check: a banner shows. Then the APNs key, bundle ID, entitlement and token are all right.

## 7. Push through the Garner backend

1. Home → **Copy device ID**.
2. In Portal, **Send test push** to that device id.

Check: a banner shows, and **Inbox** lists the push as *Shown*.

A push can reach iOS as a data push, which the SDK shows as a local notification, or as an
alert push (`apns-push-type: alert`, `apns-priority: 10`), which iOS shows. The app and SDK
handle both. iOS can delay or drop data pushes when the app is in the background or
force-quit.

## 8. Real pushes for a user

On **Account**, sign in with a user id and the consumer email the review request goes to, and
keep **Receive pushes** on. Review requests for that email then reach this iPhone and show in
**Inbox**.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Signing: no permission, can't register the device or App ID | Automatic Signing Controls are on. Ask an Admin (step 1). |
| Signing: *Missing private key* | The certificate came from another Mac. Make a new one on this Mac (1.1). |
| `No such module 'BVGarner'` or package resolution fails (for example *BVGarnerKit.xcframework.zip … 404*) | Workspace with a `path:` engine: build the XCFramework (step 0), then **File → Packages → Reset Package Caches**. The checkout folder must be called `garner`. Project: check the package URL and version under **Package Dependencies**, then reset the caches. |
| Home: *Push token not sent*, no FCM token | `GoogleService-Info.plist` missing (the Xcode console says so), bundle ID differs from Firebase, or notifications were denied. |
| Firebase test message shows no banner | Wrong APNs key, Key ID or Team ID in Firebase, or the app was in the foreground. |
| Home: *Not registered*, no device ID | Wrong `BV_BASE_URL`, no Local Network permission, or the IOS binding isn't `<TEAM_ID>.bv.garner.test.app`. Details → **Last error** has the code. After a fix, reopen the app to retry. After 3 refusals in a row the SDK waits 1 h; reinstall to retry at once. |
| Details → **SDK version** still shows an older release | **File → Packages → Update to Latest Package Versions**, or **Reset Package Caches** (step 0). |
| Account: sign-in doesn't finish (*Waiting for device registration* or *Waiting for the push token*) | The SDK signs in only after register → push token, and says which step it waits for. Fix *Not registered* or *Push token not sent* on Home first; sign-in then finishes by itself. |
| `401` | Unknown or inactive app id. |
| `403` | Attestation failed: App Attest capability missing, or `BV_APP_ATTEST_ENVIRONMENT` doesn't match the backend. |
| `503` | Garner backend down or its SDK API is off. |
| `429` | Throttled. Wait and retry. |
| `FAILED` `PROVIDER_CONFIG_ERROR` | The APNs key in Firebase is missing or wrong. |
| `FAILED` `UNREGISTERED` | The token belongs to another Firebase project or is stale. Reinstall and resend. |
| `SENT` but no banner | Data push throttled by iOS (see step 7), app force-quit, notifications off, or Focus / Low Power Mode on. Try with the app in the foreground and check **Inbox**. |
