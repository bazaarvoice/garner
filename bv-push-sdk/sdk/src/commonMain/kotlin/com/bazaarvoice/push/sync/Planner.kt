package com.bazaarvoice.push.sync

import com.bazaarvoice.push.store.LocalState

/**
 * Desired-state planner — pure function, first applicable rule wins.
 * After every result the plan is recomputed by the
 * coordinator. Intermediate desired changes collapse in the state before
 * planning (A → none → B yields logout of A's session, then link B).
 */
public enum class PlannedCall {
    IDLE,
    REGISTER,
    SYNC_TOKEN,
    LOGOUT,
    LOGIN,
    REFRESH,
    OPT_OUT,
    OPT_IN,
}

public data class PlanInputs(
    val state: LocalState,
    val nowMillis: Long,
    val tokenChanged: Boolean,
    val hasSession: Boolean,
    val serverLinkedUser: String?,
    val authExpired: Boolean,
    val refreshDue: Boolean,
    val foregroundOrKeepAlive: Boolean,
    /**
     * Whether a login could name the consumer: the email the host passed to
     * `linkIdentity` is still in memory, or a digest an earlier login answered is in the secure
     * store. Without either the SDK waits for the next `linkIdentity` instead of planning a
     * login it cannot send.
     */
    val hasLoginIdentity: Boolean,
)

public object Planner {

    public fun plan(inputs: PlanInputs): PlannedCall {
        val s = inputs.state
        // Rule 1: blocked and not yet due → park.
        if (s.blockedReason != null && inputs.nowMillis < s.blockedUntilMillis) return PlannedCall.IDLE
        // Rule 2: no registration → register.
        if (s.deviceId == null) return PlannedCall.REGISTER
        // Rule 3: token changed → sync token (debounce lives in the coordinator).
        if (inputs.tokenChanged) return PlannedCall.SYNC_TOKEN
        // Rule 4: pending logout → logout.
        if (s.pendingLogout) return PlannedCall.LOGOUT
        // Rule 5: session exists but desired identity is none/another user →
        // the coordinator moves it to pending logout first; plan the logout.
        val desiredUser = s.userId
        if (inputs.hasSession && desiredUser != inputs.serverLinkedUser) return PlannedCall.LOGOUT
        // Rules 7–8: desired identity differs from the server link, or a
        // desired identity exists with no session → login, when the consumer
        // can be named (email in memory or stored digest); otherwise wait for
        // the next linkIdentity. Login always comes last: rule 2 registered the
        // device and a push token must be synced for that registration
        // (register → push token → login); until then wait.
        if (desiredUser != null && (desiredUser != inputs.serverLinkedUser || !inputs.hasSession)) {
            return if (inputs.hasLoginIdentity && s.syncedTokenHash != null) PlannedCall.LOGIN else PlannedCall.IDLE
        }
        // Rule 9: refresh on expiry/schedule at foreground or keep-alive.
        if (inputs.hasSession
            && (inputs.authExpired || inputs.refreshDue)
            && inputs.foregroundOrKeepAlive
        ) {
            return PlannedCall.REFRESH
        }
        // Rule 10: preference differs (unknown counts as differs) → opt call.
        if (desiredUser != null && s.serverOptedOut != s.desiredOptedOut) {
            return if (s.desiredOptedOut) PlannedCall.OPT_OUT else PlannedCall.OPT_IN
        }
        // Rule 11: otherwise idle.
        return PlannedCall.IDLE
    }
}
