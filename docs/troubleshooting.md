# Troubleshooting

## No token

Firebase config missing or wrong package: `google-services.json` must match
the app id exactly (including debug suffixes). The SDK emits
`NO_TOKEN` and `diagnostics()` shows `tokenSynced=false`.

## No pushes after link

1. `diagnostics()`: `registered`? `hasSession`? `lastError`?
2. Email: the SDK sends the address as typed and the Garner backend hashes it
   with the same rules the Bazaarvoice notification platform uses. If pushes
   still miss, the address in the app differs from the one Bazaarvoice holds
   for the consumer; a login with an address the backend cannot read is
   `400 INVALID_REQUEST`. Later logins send the stored `emailAddressHash`; if
   the backend does not know that digest for the user it also answers `400`, the
   SDK forgets the identity and the app's next `linkIdentity` logs in again
   with the address.
3. App ID: the app must register with the App ID of the push sender profile
   that sends the push, or dispatch does not find the device.

## 403s

- `APP_NOT_ACTIVE`: unknown/deleted app id, or identifier mismatch
  (exact package / `TEAMID.bundleId`, as set up in Bazaarvoice Portal).
- `ATTESTATION_REJECTED`: emulator/simulator without Play/App Attest, or a
  wrong Cloud project number. Test real pushes on a real device.
- `APP_IDENTIFIER_IN_USE` (409): the id was deregistered < 30 days ago —
  seed a fresh id, never reuse deleted ones.
- These refusals are retried twice, at least 1 min apart (on the next launch,
  foreground or SDK call). The 3rd refusal in a row blocks calls for 1 h, or
  for the backend's `Retry-After`. `blockedReason` in state holds the code.
  Reinstall the app to retry at once.

## Messaging-service conflicts

Two services for `MESSAGING_EVENT`: the SDK's wins only if declared. Else
forward BV messages from the host service
(`BVFirebaseMessagingService.handleRemoteMessage`) — `diagnostics()`
reports `fcmConflict`.

## Device specifics

- Force-stop / force-quit: FCM redelivers; held payloads survive (encrypted
  store + WorkManager / pending local request).
- OEM battery restrictions (Doze, app standby): keep-alive and release
  workers defer — expected, not lost.
- Quiet hours: device-local wall clock; DST gaps skip, overlaps repeat
  A logged-out device suppresses before quiet evaluation.

## Maven Central: version not found

A new version can take up to an hour to show on Maven Central after its release. Check
`https://repo1.maven.org/maven2/com/bazaarvoice/bv-garner-sdk-android/` and make sure the build
has `mavenCentral()` in its repositories. The artifact is `bv-garner-sdk-android`; versions
before `1.1.0-rc.4` used other names.

## Swift package resolution

- *checksum mismatch* or a failed `BVGarnerKit.xcframework.zip` download: **File → Packages →
  Reset Package Caches**, or delete `~/Library/Caches/org.swift.swiftpm` and the project's
  DerivedData, then resolve again.
- *No such module 'BVPush'* after an update: the module is `BVGarner` (and `BVGarnerFirebase`).
  Change the imports; the type names didn't change.
- *Missing package product 'BVGarner'*: the package version is older than `1.1.0-rc.4`. Update
  the package (**File → Packages → Update to Latest Package Versions**).
