package com.bazaarvoice.push.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventsTest {
    // Local event mapping, including the deferred-analytics
    // equivalents. Every event has a stable analytic name; the exhaustive
    // when below fails to compile if a new subtype is added without a name.
    private fun analyticName(event: BVPushEvent): String = when (event) {
        is BVPushEvent.Registered -> "registered"
        is BVPushEvent.RegistrationBlocked -> "registration_blocked"
        is BVPushEvent.PushTokenSynced -> "push_token_synced"
        is BVPushEvent.IdentityLinked -> "identity_linked"
        is BVPushEvent.IdentityPending -> "identity_pending"
        is BVPushEvent.SignedIn -> "signed_in"
        is BVPushEvent.SessionRefreshed -> "session_refreshed"
        is BVPushEvent.SignedOut -> "signed_out"
        is BVPushEvent.IdentityUnlinked -> "identity_unlinked"
        is BVPushEvent.PreferenceSynced -> "preference_synced"
        is BVPushEvent.NotificationReceived -> "notification_received"
        is BVPushEvent.NotificationDisplayed -> "notification_displayed"
        is BVPushEvent.NotificationHeld -> "notification_held"
        is BVPushEvent.NotificationSuppressed -> "notification_suppressed"
        is BVPushEvent.NotificationOpened -> "notification_opened"
        is BVPushEvent.ReviewOpened -> "review_opened"
        is BVPushEvent.Error -> "error"
    }

    @Test
    fun everyEventHasAStableName() {
        val events: List<BVPushEvent> = listOf(
            BVPushEvent.Registered,
            BVPushEvent.RegistrationBlocked("r"),
            BVPushEvent.PushTokenSynced,
            BVPushEvent.IdentityLinked,
            BVPushEvent.IdentityPending(BVPushEvent.IdentityPending.NOT_REGISTERED),
            BVPushEvent.SignedIn,
            BVPushEvent.SessionRefreshed,
            BVPushEvent.SignedOut,
            BVPushEvent.IdentityUnlinked,
            BVPushEvent.PreferenceSynced(true),
            BVPushEvent.NotificationReceived("n"),
            BVPushEvent.NotificationDisplayed("n"),
            BVPushEvent.NotificationHeld("n", 0L),
            BVPushEvent.NotificationSuppressed("n", "quiet"),
            BVPushEvent.NotificationOpened("n"),
            BVPushEvent.ReviewOpened("n", "custom-tabs"),
            BVPushEvent.Error("INVALID_APP_ID", null),
        )
        val names = events.map { analyticName(it) }
        assertEquals(events.size, names.toSet().size)
        assertTrue(names.all { it.isNotBlank() })
    }

    @Test
    fun identityPendingReasonsAreStableCodes() {
        assertEquals("NOT_REGISTERED", BVPushEvent.IdentityPending.NOT_REGISTERED)
        assertEquals("WAITING_FOR_PUSH_TOKEN", BVPushEvent.IdentityPending.WAITING_FOR_PUSH_TOKEN)
    }
}
