package com.bazaarvoice.push.sync

import com.bazaarvoice.push.events.BVPushEvent
import com.bazaarvoice.push.store.LocalState

/**
 * What the Android and iOS facades report after every reconcile, by the same rules: the lifecycle
 * events between two reads of the stores, and the last error.
 */
internal object Lifecycle {
    /** Store facts compared before and after a reconcile. */
    data class Facts(
        val deviceId: String?,
        val tokenHash: String?,
        val session: Boolean,
        val userId: String?,
        val serverOptedOut: Boolean?,
        val blockedReason: String?,
        val lastRefresh: String?,
    )

    /**
     * [state] is the stored local state; [deviceId] the stored device ID, [session] whether an
     * auth token is stored and [lastRefresh] the time of the last session refresh.
     */
    fun facts(state: LocalState, deviceId: String?, session: Boolean, lastRefresh: String?): Facts = Facts(
        deviceId = deviceId,
        tokenHash = state.syncedTokenHash,
        session = session,
        userId = state.userId,
        serverOptedOut = state.serverOptedOut,
        blockedReason = state.blockedReason,
        lastRefresh = lastRefresh,
    )

    /** The events from [before] to [after], in the order the steps run. */
    fun events(before: Facts, after: Facts): List<BVPushEvent> {
        val events = mutableListOf<BVPushEvent>()
        if (before.deviceId == null && after.deviceId != null) events += BVPushEvent.Registered
        if (after.blockedReason != null && after.blockedReason != before.blockedReason) {
            events += BVPushEvent.RegistrationBlocked(after.blockedReason)
        }
        if (after.tokenHash != null && after.tokenHash != before.tokenHash) events += BVPushEvent.PushTokenSynced
        if (!before.session && after.session) {
            events += BVPushEvent.IdentityLinked
            events += BVPushEvent.SignedIn
        } else if (before.session && after.session && after.lastRefresh != before.lastRefresh) {
            events += BVPushEvent.SessionRefreshed
        }
        if (before.session && !after.session) {
            events += BVPushEvent.SignedOut
            if (after.userId == null) events += BVPushEvent.IdentityUnlinked
        }
        if (after.serverOptedOut != null && after.serverOptedOut != before.serverOptedOut) {
            events += BVPushEvent.PreferenceSynced(after.serverOptedOut)
        }
        return events
    }

    /**
     * The registration block reason while the device has no ID, else [failure]: the last reconcile
     * failure, null after a reconcile that completed.
     */
    fun lastError(state: LocalState, failure: String?): String? =
        state.blockedReason?.takeIf { state.deviceId == null } ?: failure
}
