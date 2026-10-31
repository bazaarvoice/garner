package com.bazaarvoice.push.sync

import com.bazaarvoice.push.events.BVPushEvent
import com.bazaarvoice.push.store.LocalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LifecycleTest {
    private val none = emptyList<BVPushEvent>()
    private val fresh = Lifecycle.facts(LocalState(), deviceId = null, session = false, lastRefresh = null)
    private val registered = fresh.copy(deviceId = "d1")
    private val synced = registered.copy(tokenHash = "t1")
    private val signedIn = synced.copy(session = true, userId = "u1", lastRefresh = "r1")

    @Test
    fun factsComeFromTheStores() {
        val state = LocalState(
            deviceId = "d1",
            syncedTokenHash = "t1",
            userId = "u1",
            serverOptedOut = true,
            blockedReason = "APP_NOT_ACTIVE",
        )
        assertEquals(
            Lifecycle.Facts("d1", "t1", true, "u1", true, "APP_NOT_ACTIVE", "r1"),
            Lifecycle.facts(state, deviceId = "d1", session = true, lastRefresh = "r1"),
        )
    }

    @Test
    fun nothingChangedMeansNoEvents() {
        assertEquals(none, Lifecycle.events(fresh, fresh))
        assertEquals(none, Lifecycle.events(signedIn, signedIn))
    }

    @Test
    fun theFirstDeviceIdIsRegistered() {
        assertEquals(listOf(BVPushEvent.Registered), Lifecycle.events(fresh, registered))
    }

    @Test
    fun aNewBlockReasonIsReportedOnce() {
        val blocked = fresh.copy(blockedReason = "ATTESTATION_REJECTED")
        assertEquals(listOf(BVPushEvent.RegistrationBlocked("ATTESTATION_REJECTED")), Lifecycle.events(fresh, blocked))
        assertEquals(none, Lifecycle.events(blocked, blocked))
        assertEquals(
            listOf(BVPushEvent.RegistrationBlocked("APP_NOT_ACTIVE")),
            Lifecycle.events(blocked, blocked.copy(blockedReason = "APP_NOT_ACTIVE")),
        )
        assertEquals(none, Lifecycle.events(blocked, fresh))
    }

    @Test
    fun aNewTokenHashIsSynced() {
        assertEquals(listOf(BVPushEvent.PushTokenSynced), Lifecycle.events(registered, synced))
        assertEquals(listOf(BVPushEvent.PushTokenSynced), Lifecycle.events(synced, synced.copy(tokenHash = "t2")))
    }

    @Test
    fun stepsInOneReconcileComeInOrder() {
        assertEquals(listOf(BVPushEvent.Registered, BVPushEvent.PushTokenSynced), Lifecycle.events(fresh, synced))
    }

    @Test
    fun aNewSessionLinksAndSignsIn() {
        assertEquals(
            listOf(BVPushEvent.IdentityLinked, BVPushEvent.SignedIn),
            Lifecycle.events(synced.copy(userId = "u1"), signedIn),
        )
    }

    @Test
    fun aNewRefreshTimeRefreshesTheSession() {
        val refreshed = signedIn.copy(lastRefresh = "r2")
        assertEquals(listOf(BVPushEvent.SessionRefreshed), Lifecycle.events(signedIn, refreshed))
    }

    @Test
    fun aLostSessionSignsOutAndUnlinksOnlyWithoutAUser() {
        assertEquals(listOf(BVPushEvent.SignedOut, BVPushEvent.IdentityUnlinked), Lifecycle.events(signedIn, synced))
        assertEquals(listOf(BVPushEvent.SignedOut), Lifecycle.events(signedIn, signedIn.copy(session = false)))
    }

    @Test
    fun aNewServerPreferenceIsSynced() {
        val optedOut = signedIn.copy(serverOptedOut = true)
        assertEquals(listOf(BVPushEvent.PreferenceSynced(true)), Lifecycle.events(signedIn, optedOut))
        assertEquals(none, Lifecycle.events(optedOut, signedIn))
    }

    @Test
    fun lastErrorIsTheBlockReasonOnlyWithoutADeviceId() {
        val blocked = LocalState(blockedReason = "ATTESTATION_REJECTED")
        assertEquals("ATTESTATION_REJECTED", Lifecycle.lastError(blocked, failure = null))
        assertEquals("ATTESTATION_REJECTED", Lifecycle.lastError(blocked, failure = "timeout"))
        assertEquals("timeout", Lifecycle.lastError(blocked.copy(deviceId = "d1"), failure = "timeout"))
        assertNull(Lifecycle.lastError(blocked.copy(deviceId = "d1"), failure = null))
        assertNull(Lifecycle.lastError(LocalState(), failure = null))
    }
}
