package com.bazaarvoice.push.sync

import com.bazaarvoice.push.store.LocalState
import kotlin.test.Test
import kotlin.test.assertEquals

class RejectionPolicyTest {

    private val code = "ATTESTATION_REJECTED"

    private fun plan(state: LocalState, now: Long) = Planner.plan(
        PlanInputs(
            state = state,
            nowMillis = now,
            tokenChanged = false,
            hasSession = false,
            serverLinkedUser = null,
            authExpired = false,
            refreshDue = false,
            foregroundOrKeepAlive = false,
            hasLoginIdentity = false,
        )
    )

    @Test
    fun firstTwoRejectionsOnlyParkForAMinute() {
        val first = RejectionPolicy.afterRejection(LocalState(), 1_000L, code, null)
        assertEquals(code, first.blockedReason)
        assertEquals(1, first.rejectedAttempts)
        assertEquals(61_000L, first.parkUntilMillis)
        assertEquals(PlannedCall.REGISTER, plan(first, 61_000L))

        val second = RejectionPolicy.afterRejection(first, 61_000L, code, null)
        assertEquals(2, second.rejectedAttempts)
        assertEquals(121_000L, second.parkUntilMillis)
        assertEquals(PlannedCall.REGISTER, plan(second, 121_000L))
    }

    @Test
    fun thirdRejectionBlocksForAnHourAndResetsTheCount() {
        var state = LocalState()
        repeat(3) { state = RejectionPolicy.afterRejection(state, 0L, code, null) }
        assertEquals(code, state.blockedReason)
        assertEquals(0, state.rejectedAttempts)
        assertEquals(3_600_000L, state.blockedUntilMillis)
        assertEquals(PlannedCall.IDLE, plan(state, 3_599_999L))
        assertEquals(PlannedCall.REGISTER, plan(state, 3_600_000L))

        val next = RejectionPolicy.afterRejection(state, 3_600_000L, code, null)
        assertEquals(1, next.rejectedAttempts)
        assertEquals(3_660_000L, next.parkUntilMillis)
    }

    @Test
    fun retryAfterWinsOverDefaults() {
        val parked = RejectionPolicy.afterRejection(LocalState(), 0L, code, 5_000L)
        assertEquals(5_000L, parked.parkUntilMillis)

        val blocked = RejectionPolicy.afterRejection(
            LocalState(rejectedAttempts = 2), 0L, code, 7_200_000L)
        assertEquals(7_200_000L, blocked.blockedUntilMillis)
    }

    @Test
    fun stateFromOlderBuildsStartsWithNoRejections() {
        val old = """{"schemaVersion":1,"blockedReason":"APP_NOT_ACTIVE","blockedUntilMillis":5}"""
        val state = LocalState.decode(old)
        assertEquals(0, state.rejectedAttempts)
        assertEquals("APP_NOT_ACTIVE", state.blockedReason)
    }
}
