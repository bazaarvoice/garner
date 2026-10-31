package com.bazaarvoice.push.sync

import com.bazaarvoice.push.events.BVPushEvent
import com.bazaarvoice.push.store.LocalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoginWaitTest {
    private val wanted = LocalState(userId = "u1")
    private val ready = wanted.copy(deviceId = "d1", syncedTokenHash = "t1")

    @Test
    fun nothingWaitsWithoutAUserOrWithASession() {
        assertNull(LoginWait.reason(LocalState(), signedIn = false))
        assertNull(LoginWait.reason(wanted, signedIn = true))
        assertNull(LoginWait.reason(ready, signedIn = true))
    }

    @Test
    fun noDeviceIdWaitsForRegistration() {
        assertEquals(BVPushEvent.IdentityPending.NOT_REGISTERED, LoginWait.reason(wanted, signedIn = false))
        assertEquals(
            BVPushEvent.IdentityPending.NOT_REGISTERED,
            LoginWait.reason(wanted.copy(syncedTokenHash = "t1"), signedIn = false),
        )
    }

    @Test
    fun registeredWithoutTokenWaitsForThePushToken() {
        assertEquals(
            BVPushEvent.IdentityPending.WAITING_FOR_PUSH_TOKEN,
            LoginWait.reason(wanted.copy(deviceId = "d1"), signedIn = false),
        )
    }

    @Test
    fun bothStepsDoneMeansTheLoginRan() {
        assertNull(LoginWait.reason(ready, signedIn = false))
    }
}
