package com.bazaarvoice.push.sync

import com.bazaarvoice.push.events.BVPushEvent
import com.bazaarvoice.push.store.LocalState

/**
 * Why a wanted login hasn't run yet. The SDK logs in only after register and push token sync
 * (planner rules 7–8), so a user that `linkIdentity` kept waits for the missing step.
 */
internal object LoginWait {
    /**
     * Null when no user is wanted or [signedIn] is true; else
     * [BVPushEvent.IdentityPending.NOT_REGISTERED] (no device ID yet) or
     * [BVPushEvent.IdentityPending.WAITING_FOR_PUSH_TOKEN] (push token not sent yet). Also null
     * once both steps are done: the login itself ran (a failed one parks for a retry).
     */
    fun reason(state: LocalState, signedIn: Boolean): String? = when {
        state.userId == null || signedIn -> null
        state.deviceId == null -> BVPushEvent.IdentityPending.NOT_REGISTERED
        state.syncedTokenHash == null -> BVPushEvent.IdentityPending.WAITING_FOR_PUSH_TOKEN
        else -> null
    }
}
