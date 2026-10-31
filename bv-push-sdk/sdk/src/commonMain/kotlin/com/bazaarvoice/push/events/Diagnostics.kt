package com.bazaarvoice.push.events

/**
 * Diagnostics snapshot for the API, debug inspector and support.
 * Secrets never appear here.
 */
public data class Diagnostics(
    val sdkVersion: String,
    val appId: Int?,
    val registered: Boolean,
    val deviceId: String?,
    val tokenSynced: Boolean,
    val identityPhase: String,
    val hasSession: Boolean,
    val sessionExpiry: String?,
    val desiredOptedOut: Boolean,
    val serverOptedOut: Boolean?,
    val quietWindow: String?,
    val notificationPermission: String,
    val fcmConflict: Boolean,
    val lastError: String?,
    val lastCorrelationId: String?,
)
