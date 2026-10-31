package com.bazaarvoice.push.device

/**
 * Platform ports the coordinator drives, implemented once per platform
 * (Android and iOS). Tests run the core against the production adapters.
 */
public interface AttestationProvider {
    public sealed interface Evidence {
        public data class Token(val token: String) : Evidence
        public data object Unsupported : Evidence
    }

    /** Always attempts real attestation; never a bypass. */
    public suspend fun attest(nonce: String): Evidence
}

public interface PushTokenSource {
    /** Current FCM token, or null when Firebase is unavailable. */
    public suspend fun currentToken(): String?
}

public interface ConnectivityMonitor {
    public val connected: Boolean
}

/** Device facts for registration. */
public interface DeviceInfoProvider {
    /** "ANDROID" or "IOS" (the OpenAPI Platform). */
    public val platform: String
    /** Android package name or TEAMID.bundleId. */
    public val appIdentifier: String
    public val model: String?
    public val osVersion: String?
    public val appVersion: String?
}
