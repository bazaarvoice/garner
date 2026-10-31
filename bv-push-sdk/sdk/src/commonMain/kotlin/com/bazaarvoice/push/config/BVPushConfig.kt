package com.bazaarvoice.push.config

/**
 * Configuration validation. Invalid appId → dormant +
 * error event, never an exception. The only check on an App ID: it is an
 * 8-digit number (10000000–99999999).
 *
 * The SDK knows one host only: the public push gateway. It has no notion of
 * environments. Test builds point elsewhere with [BVPushOptions.baseUrlOverride].
 */
public data class BVPushOptions(
    val baseUrlOverride: String? = null,
    val autoFetchFcmToken: Boolean = true,
    val openReviewAutomatically: Boolean = true,
    val sessionKeepAlive: Boolean = true,
    val sessionRefreshIntervalHours: Long = 24L,
    val allowInsecureLocalhost: Boolean = false,
)

public object BVPushConfig {
    /**
     * The single public push host: the only place the SDK sets it. Android and iOS both get the
     * base URL from [resolveBaseUrl].
     */
    internal const val DEFAULT_BASE_URL = "https://push.prod.bazaarvoice.com"

    private const val MIN_APP_ID = 10_000_000
    private const val MAX_APP_ID = 99_999_999

    /** True for any 8-digit number; nothing else about the App ID is checked. */
    public fun isValidAppId(appId: Int): Boolean = appId in MIN_APP_ID..MAX_APP_ID

    public fun resolveBaseUrl(options: BVPushOptions): String {
        options.baseUrlOverride?.let { return it }
        return DEFAULT_BASE_URL
    }
}
