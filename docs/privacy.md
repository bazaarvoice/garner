# Privacy

## Data inventory (what leaves the device, and what never does)

| Data | Leaves? | Form | Notes |
|---|---|---|---|
| FCM push token | Yes (Garner backend only) | Plaintext over TLS | Stored encrypted at rest server-side; only its SHA-256 lives on device |
| Email | Yes (Garner backend only) | Plain address over TLS, in the first login call only | The backend normalises and hashes it, stores only the digest, never logs the address and answers the digest (`emailAddressHash`). The device holds the address in memory only and never stores it |
| Email digest | Yes (Garner backend only) | Digest over TLS, in later login calls | Received from the backend at login; stored with the tokens in the secure store until `unlinkIdentity` |
| userId (client's id) | Yes (Garner backend only) | Plaintext over TLS | Needed to link the consumer |
| deviceId, tokens | Yes (Garner backend only) | TLS | deviceApiKey never leaves the Keystore/Keychain except as an HMAC key |
| Review URL | No (stays in the notification) | — | Opened on-device in a Custom Tab / Safari view |
| Analytics | No | — | Lifecycle events are local-only; a future analytics module subscribes explicitly |

## On-device storage

- Secrets (deviceApiKey, auth/refresh tokens, the backend's email digest until
  `unlinkIdentity`, linked identity hashes). The plain email address is never
  stored; it lives in memory until the first login succeeds:
  Android Keystore (AES-256-GCM, `AfterFirstUnlockThisDeviceOnly` class on
  iOS Keychain, no iCloud sync, no access group). Degraded tier (keystore
  failure): app-private software key, flagged in diagnostics.
- Non-secrets (synced token hash, preferences, quiet window, codec state):
  private prefs / App Group suite. Held notifications: encrypted blobs.
- Install marker (no-backup dir / UserDefaults): a restore without the marker
  purges credentials and re-registers — a new deviceId, never identity reuse.

## Consent and withdrawal

`optOut()` stops display locally and replays server-side after link.
`reset()` purges everything on-device. Logging redacts keys, tokens,
emails (plain and hashed), FCM tokens (prefix only) and review-URL
query strings by construction.

## Platform declarations

- iOS: `PrivacyInfo.xcprivacy` (Device ID, User ID, Email — linked,
  app-functionality, no tracking; UserDefaults accessed for the App Group).
- Android: Play Data-safety answers mirror the table above; no background
  location, no advertising ID.
