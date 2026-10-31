package com.bazaarvoice.push.review

/**
 * Review link validation: HTTPS only, optional host allowlist.
 * The default accepts any HTTPS host (custom review domains still open).
 */
public object ReviewLinkValidator {

    public fun isOpenable(url: String?, allowedHosts: Set<String>? = null): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        if (!lower.startsWith("https://")) return false
        if (allowedHosts == null) return true
        val host = lower.removePrefix("https://").substringBefore('/').substringBefore(':')
        return host in allowedHosts
    }

    public enum class TapOutcome { OPEN_REVIEW, OPEN_APP, HOST_HANDLED }

    public fun route(url: String?, hostHandled: Boolean, allowedHosts: Set<String>? = null): TapOutcome {
        if (hostHandled) return TapOutcome.HOST_HANDLED
        return if (isOpenable(url, allowedHosts)) TapOutcome.OPEN_REVIEW else TapOutcome.OPEN_APP
    }
}
