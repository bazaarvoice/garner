package com.bazaarvoice.push.sync

import com.bazaarvoice.push.store.LocalState
import kotlin.test.Test
import kotlin.test.assertEquals

class PlannerTest {
    private fun inputs(
        state: LocalState = LocalState(),
        nowMillis: Long = 1_000_000L,
        tokenChanged: Boolean = false,
        hasSession: Boolean = false,
        serverLinkedUser: String? = null,
        authExpired: Boolean = false,
        refreshDue: Boolean = false,
        foregroundOrKeepAlive: Boolean = false,
        hasLoginIdentity: Boolean = true,
    ) = PlanInputs(state, nowMillis, tokenChanged, hasSession, serverLinkedUser,
        authExpired, refreshDue, foregroundOrKeepAlive, hasLoginIdentity)

    private fun registered(extra: LocalState.() -> LocalState = { this }) =
        LocalState(deviceId = "d1", syncedTokenHash = "t1").extra()

    // Planner decision table (rules 1-11).
    @Test
    fun plannerTable() {
        // Rule 1: blocked and not due → idle.
        assertEquals(PlannedCall.IDLE, Planner.plan(inputs(
            LocalState(blockedReason = "x", blockedUntilMillis = 2_000_000L))))
        // Rule 2: no registration → register.
        assertEquals(PlannedCall.REGISTER, Planner.plan(inputs()))
        // Rule 3: token changed → sync token.
        assertEquals(PlannedCall.SYNC_TOKEN, Planner.plan(inputs(
            registered(), tokenChanged = true)))
        // Rule 4: pending logout → logout.
        assertEquals(PlannedCall.LOGOUT, Planner.plan(inputs(
            registered { copy(pendingLogout = true) }, hasSession = true)))
        // Rule 5: session for another user → logout (coordinator moves it first).
        assertEquals(PlannedCall.LOGOUT, Planner.plan(inputs(
            registered { copy(userId = "b") }, hasSession = true, serverLinkedUser = "a")))
        // Rules 7-8: desired identity, no session → login.
        assertEquals(PlannedCall.LOGIN, Planner.plan(inputs(
            registered { copy(userId = "a") })))
        // Order register → push token → login: never a login without a device ID,
        // and never before the token is synced for this registration.
        assertEquals(PlannedCall.REGISTER, Planner.plan(inputs(
            LocalState(userId = "a", syncedTokenHash = "t1"))))
        assertEquals(PlannedCall.SYNC_TOKEN, Planner.plan(inputs(
            registered { copy(userId = "a", syncedTokenHash = null) }, tokenChanged = true)))
        assertEquals(PlannedCall.SYNC_TOKEN, Planner.plan(inputs(
            registered { copy(userId = "a") }, tokenChanged = true)))
        assertEquals(PlannedCall.IDLE, Planner.plan(inputs(
            registered { copy(userId = "a", syncedTokenHash = null) })))
        // Rules 7-8: no email in memory and no stored digest → wait
        // for the next linkIdentity, never plan a login that cannot be sent.
        assertEquals(PlannedCall.IDLE, Planner.plan(inputs(
            registered { copy(userId = "a") }, hasLoginIdentity = false)))
        assertEquals(PlannedCall.IDLE, Planner.plan(inputs(
            registered { copy(userId = "a") }, serverLinkedUser = "a", hasLoginIdentity = false)))
        // Rule 9: refresh on expiry at foreground.
        assertEquals(PlannedCall.REFRESH, Planner.plan(inputs(
            registered { copy(userId = "a", serverOptedOut = false) }, hasSession = true, serverLinkedUser = "a",
            authExpired = true, foregroundOrKeepAlive = true)))
        // Rule 9 without a trigger → idle.
        assertEquals(PlannedCall.IDLE, Planner.plan(inputs(
            registered { copy(userId = "a", serverOptedOut = false) }, hasSession = true, serverLinkedUser = "a",
            authExpired = true, foregroundOrKeepAlive = false)))
        // Rule 10: preference differs → opt call.
        assertEquals(PlannedCall.OPT_OUT, Planner.plan(inputs(
            registered { copy(userId = "a", desiredOptedOut = true, serverOptedOut = false) },
            hasSession = true, serverLinkedUser = "a")))
        assertEquals(PlannedCall.OPT_IN, Planner.plan(inputs(
            registered { copy(userId = "a", desiredOptedOut = false, serverOptedOut = true) },
            hasSession = true, serverLinkedUser = "a")))
        // Rule 11: steady state → idle.
        assertEquals(PlannedCall.IDLE, Planner.plan(inputs(
            registered { copy(userId = "a", serverOptedOut = false) },
            hasSession = true, serverLinkedUser = "a")))
    }
}
