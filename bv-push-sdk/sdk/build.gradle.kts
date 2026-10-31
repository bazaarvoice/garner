import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kover)
    alias(libs.plugins.api.validator)
    `maven-publish`
    signing
}

// One version for every artifact, set only in SdkInfo.VERSION (the Swift package's
// BVPushVersion.version returns it; the release tag is this version).
val sdkVersion: String = Regex("""const val VERSION: String = "([^"]+)"""")
    .find(file("src/commonMain/kotlin/com/bazaarvoice/push/SdkInfo.kt").readText())
    ?.groupValues?.get(1)
    ?: error("SdkInfo.VERSION not found")

group = providers.gradleProperty("GROUP").getOrElse("com.bazaarvoice")
version = sdkVersion

kotlin {
    // JVM target for commonTest on CI Linux; device targets below.
    jvm()

    androidTarget {
        // Maven Central gets the release variant only (see publishing below).
        publishLibraryVariants("release")
        // The artifactId is set here: the Kotlin plugin names the publication <project>-android
        // after publishing.publications.configureEach has run.
        mavenPublication {
            artifactId = providers.gradleProperty("POM_ARTIFACT_ID").getOrElse("bv-garner-sdk-android")
        }
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    // BVGarnerKit.xcframework for the Swift package (root Package.swift):
    //   ./gradlew :sdk:assembleBVGarnerKitDebugXCFramework   (local builds, CI)
    //   ./gradlew :sdk:assembleBVGarnerKitReleaseXCFramework (releases)
    // Output: sdk/build/XCFrameworks/{debug,release}/BVGarnerKit.xcframework
    val bvGarnerKit = XCFramework("BVGarnerKit")
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "BVGarnerKit"
            binaryOption("bundleId", "com.bazaarvoice.push.BVGarnerKit")
            // CFBundleShortVersionString is numbers only: SdkInfo.VERSION without a -rc.n suffix.
            binaryOption("bundleShortVersionString", sdkVersion.substringBefore('-'))
            bvGarnerKit.add(this)
        }
    }

    // SHA-256 / HMAC come from the Kotlin/Native platform library platform.CoreCrypto
    // (CommonCrypto), so no cinterop of our own.

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.integrity)
            implementation(libs.firebase.messaging)
            implementation(libs.androidx.core)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.work)
            implementation(libs.androidx.lifecycle)
            implementation(libs.androidx.startup)
        }
        getByName("androidInstrumentedTest").dependencies {
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.uiautomator)
        }
        getByName("jvmMain").dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.bazaarvoice.push"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// K/N klib dumps need the Xcode license (cinterop stubs); JVM and
// Android .api baselines are checked here, Swift via the digester post-license.
apiValidation {
    klib {
        enabled = false
    }
}

kover {
    reports {
        filters {
            excludes {
                // Data carriers and port interfaces: no logic to cover.
                classes(
                    "com.bazaarvoice.push.events.*",
                    "com.bazaarvoice.push.net.ModelsKt",
                    "com.bazaarvoice.push.net.Device*",
                    "com.bazaarvoice.push.net.Token*",
                    "com.bazaarvoice.push.net.Login*",
                    "com.bazaarvoice.push.net.Refresh*",
                    "com.bazaarvoice.push.net.*Request*",
                    "com.bazaarvoice.push.net.*Response*",
                    "com.bazaarvoice.push.net.ErrorEnvelope*",
                    "com.bazaarvoice.push.device.AttestationProvider*",
                    "com.bazaarvoice.push.device.PushTokenSource*",
                    "com.bazaarvoice.push.device.ConnectivityMonitor*",
                    "com.bazaarvoice.push.device.DeviceInfoProvider*",
                    "com.bazaarvoice.push.store.SecureStore*",
                    "com.bazaarvoice.push.store.SettingsStore*",
                    "com.bazaarvoice.push.log.LogSink*",
                )
                // Network + hardware paths: covered by integration tests against a
                // real backend and devices, not by JVM unit tests.
                classes(
                    "com.bazaarvoice.push.net.PushApiClient*",
                    "com.bazaarvoice.push.sync.Coordinator*",
                )
                // Android platform glue: covered by device tests and
                // integration tests on an emulator, not by JVM unit tests.
                classes(
                    "com.bazaarvoice.push.Android*",
                    "com.bazaarvoice.push.InstallMarker*",
                    "com.bazaarvoice.push.PlayIntegrityAttestor*",
                    "com.bazaarvoice.push.BVPush*",
                    "com.bazaarvoice.push.BVFirebaseMessagingService*",
                    "com.bazaarvoice.push.FcmServiceConflictDetector*",
                    "com.bazaarvoice.push.CustomTabsReviewPresenter*",
                    "com.bazaarvoice.push.ConnectivityMonitor*",
                    "com.bazaarvoice.push.ForegroundTriggers*",
                    "com.bazaarvoice.push.SyncResumeWorker*",
                    "com.bazaarvoice.push.SessionKeepAliveWorker*",
                    "com.bazaarvoice.push.QuietHoursReleaseWorker*",
                    "com.bazaarvoice.push.PlatformInfo*",
                )
                // iOS platform glue: covered by Xcode tests on simulator
                // (needs the Xcode license), not by JVM unit tests.
                classes(
                    "com.bazaarvoice.push.KeychainSecureStore*",
                    "com.bazaarvoice.push.AppGroupSettingsStore*",
                    "com.bazaarvoice.push.AppAttestAttestor*",
                    "com.bazaarvoice.push.AppIdentifierResolver*",
                    "com.bazaarvoice.push.IosInstallMarker*",
                    "com.bazaarvoice.push.IosFacade*",
                    "com.bazaarvoice.push.IosEvent*",
                    "com.bazaarvoice.push.IosPushResult*",
                )
            }
        }
        // At least 90% line coverage.
        verify {
            rule("commonMain 90 percent") {
                minBound(
                    90,
                    kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE,
                    kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE,
                )
            }
        }
    }
}

// Maven Central: only the Android release variant is published, as
// com.bazaarvoice:bv-garner-sdk-android (artifactId in androidTarget above, POM values in
// gradle.properties). Publishing needs the
// Gradle properties centralUsername, centralPassword, signingKey and signingPassword. Local check without signing:
//   ./gradlew :sdk:publishAndroidReleasePublicationToMavenLocal
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(rootProject.file("../docs/api-reference.md"))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "androidRelease") {
            artifact(javadocJar)
        }
        pom {
            name.set(providers.gradleProperty("POM_NAME"))
            description.set(providers.gradleProperty("POM_DESCRIPTION"))
            url.set(providers.gradleProperty("POM_URL"))
            licenses {
                license {
                    name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                    url.set(providers.gradleProperty("POM_LICENSE_URL"))
                    distribution.set(providers.gradleProperty("POM_LICENSE_DIST"))
                }
            }
            developers {
                developer {
                    id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                    name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                    url.set(providers.gradleProperty("POM_DEVELOPER_URL"))
                }
            }
            scm {
                url.set(providers.gradleProperty("POM_SCM_URL"))
                connection.set(providers.gradleProperty("POM_SCM_CONNECTION"))
                developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION"))
            }
        }
    }
    repositories {
        // Central Publisher Portal, through its OSSRH-compatible staging API. CI then moves the
        // upload to the portal (POST /manual/upload/defaultRepository/<namespace>).
        maven {
            name = "centralStaging"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = providers.gradleProperty("centralUsername").orNull
                password = providers.gradleProperty("centralPassword").orNull
            }
        }
    }
}

// The JVM, iOS and metadata publications exist for the build only: iOS ships as
// BVGarnerKit.xcframework in the Swift package.
tasks.withType<AbstractPublishToMaven>().configureEach {
    onlyIf("only the androidRelease publication is published") {
        (it as AbstractPublishToMaven).publication.name == "androidRelease"
    }
}

signing {
    val signingKey = providers.gradleProperty("signingKey").orNull
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, providers.gradleProperty("signingPassword").orNull)
        publishing.publications.withType<MavenPublication>()
            .matching { it.name == "androidRelease" }
            .configureEach { sign(this) }
    }
}
