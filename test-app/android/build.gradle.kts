plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    // Firebase config for the BV Garner test app (app/google-services.json is
    // git-ignored, never committed; see README.md).
    id("com.google.gms.google-services") version "4.5.0" apply false
}
