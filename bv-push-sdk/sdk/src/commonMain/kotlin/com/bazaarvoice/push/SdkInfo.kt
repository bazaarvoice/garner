package com.bazaarvoice.push

/**
 * SDK identity shared by every platform facade: the release version and the platform name.
 */
public object SdkInfo {
    /**
     * Release version: the only place it is set. The Gradle build reads it for the Maven artifact
     * and the XCFramework, the release tag is this version, the SDK sends it to the backend at
     * registration, and the Swift package's `BVPushVersion.version` returns it. A pre-release
     * ends in `-rc.n`.
     */
    public const val VERSION: String = "1.1.0"
    public const val PLATFORM: String = "multiplatform"
}
