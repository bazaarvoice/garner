import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Client setup (see ../README.md): client.properties next to settings.gradle.kts
// points the app at your own Firebase app, Portal app id and Garner Backend. Without
// it the app keeps its defaults (package bv.garner.test.app, app id 10000001, and the
// production Garner Backend, the SDK's built-in endpoint). For a backend on your machine,
// set baseUrl=http://10.0.2.2:8082 (the emulator's alias for the host).
val clientFile = rootProject.file("client.properties")
val client = Properties().apply { if (clientFile.exists()) clientFile.inputStream().use(::load) }
fun clientValue(key: String): String? = client.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

val packageName = clientValue("packageName") ?: "bv.garner.test.app"
val bvAppId = clientValue("appId")?.toInt() ?: 10000001
val bvBaseUrl = clientValue("baseUrl") ?: "https://push.prod.bazaarvoice.com"
val appName = clientValue("appName") ?: "BV Garner"
// Google Cloud project linked to the app in Play Console (Play Integrity). Optional: without
// it the SDK sends its Play Integrity request with no project number.
val bvCloudProjectNumber = clientValue("cloudProjectNumber")?.also { value ->
    require(value.all(Char::isDigit)) { "client.properties cloudProjectNumber must be digits only: $value" }
}.orEmpty()

// Play services pins androidx.fragment:fragment:1.1.0 (strict), but the app's
// Activity Result APIs need fragment >= 1.3.0 (lintVitalRelease fails otherwise).
// Fragment is backward compatible, so force the modern line everywhere.
configurations.all {
    resolutionStrategy {
        force("androidx.fragment:fragment:1.8.5")
    }
}

// The google-services plugin reads app/google-services.json. A client file is copied
// over it (replacing the file or the local-stack symlink, never writing through it).
clientValue("googleServicesJson")?.let { path ->
    val source = clientFile.parentFile.resolve(path.replaceFirst("~", System.getProperty("user.home")))
    require(source.isFile) { "client.properties googleServicesJson not found: $source" }
    val target = file("google-services.json")
    if (!target.isFile || target.readText() != source.readText()) {
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

android {
    namespace = "bv.garner.test.app"
    compileSdk = 37
    defaultConfig {
        applicationId = packageName
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "0.5.0"
        buildConfigField("int", "BV_APP_ID", bvAppId.toString())
        buildConfigField("String", "BV_BASE_URL", "\"$bvBaseUrl\"")
        // SDK auto-init reads these; a plain-HTTP local URL stays blank so the
        // auto-init keeps its old behaviour and MainActivity's init owns local runs.
        manifestPlaceholders["bvAppId"] = bvAppId.toString()
        manifestPlaceholders["bvBaseUrl"] = if (bvBaseUrl.startsWith("https://")) bvBaseUrl else ""
        manifestPlaceholders["appName"] = appName
        // A string resource, not a placeholder: a 12-digit manifest value is stored as a float
        // and loses digits. The SDK reads it from meta-data com.bazaarvoice.push.CLOUD_PROJECT_NUMBER.
        resValue("string", "bv_cloud_project_number", bvCloudProjectNumber)
    }
    signingConfigs {
        // The release keystore lives outside the repo. Credentials come
        // from Gradle properties (e.g. ~/.gradle/gradle.properties), never from this file:
        //   bvReleaseStoreFile=/abs/path/to/bv-garner-release.keystore
        //   bvReleaseStorePassword=...
        //   bvReleaseKeyAlias=...
        //   bvReleaseKeyPassword=...
        // Debug builds never touch these; a release build without them fails fast below.
        create("release") {
            val prop = { key: String -> (project.findProperty(key) as String?).orEmpty() }
            val storePath = prop("bvReleaseStoreFile")
            if (storePath.isNotBlank()) {
                storeFile = file(storePath)
                storePassword = prop("bvReleaseStorePassword")
                keyAlias = prop("bvReleaseKeyAlias")
                keyPassword = prop("bvReleaseKeyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            val requestedRelease = gradle.startParameter.taskNames.any {
                it.contains("Release", ignoreCase = true)
            }
            if (requestedRelease && signingConfig?.storeFile == null) {
                throw GradleException(
                    "Release signing is not configured: set bvReleaseStoreFile, "
                            + "bvReleaseStorePassword, bvReleaseKeyAlias and bvReleaseKeyPassword "
                            + "in ~/.gradle/gradle.properties (see app/build.gradle.kts).")
            }
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Local SDK under development via the composite build; release builds
    // use Maven Central com.bazaarvoice:bv-garner-sdk-android.
    implementation("com.bazaarvoice:bv-garner-sdk-android:1.1.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Direct Firebase use (token read for the debug driver); version pinned to
    // the SDK's firebaseMessaging (bv-push-sdk/gradle/libs.versions.toml).
    implementation("com.google.firebase:firebase-messaging:25.1.3")
    // JVM unit tests of the pure screen logic (src/test): real objects only.
    testImplementation("junit:junit:4.13.2")
}
