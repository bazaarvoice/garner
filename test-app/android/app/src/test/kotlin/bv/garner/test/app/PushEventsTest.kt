package bv.garner.test.app

import com.bazaarvoice.push.events.BVPushEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushEventsTest {

    @Test
    fun notificationEventsBecomeEntries() {
        assertEquals(PushEntry(ID, PushStep.RECEIVED, NOW), entry(BVPushEvent.NotificationReceived(ID)))
        assertEquals(PushEntry(ID, PushStep.SHOWN, NOW), entry(BVPushEvent.NotificationDisplayed(ID)))
        assertEquals(PushEntry(ID, PushStep.HELD, NOW, UNTIL.toString()), entry(BVPushEvent.NotificationHeld(ID, UNTIL)))
        assertEquals(
            PushEntry(ID, PushStep.HIDDEN, NOW, REASON),
            entry(BVPushEvent.NotificationSuppressed(ID, REASON)),
        )
        assertEquals(PushEntry(ID, PushStep.TAPPED, NOW), entry(BVPushEvent.NotificationOpened(ID)))
        assertEquals(
            PushEntry(ID, PushStep.REVIEW_OPENED, NOW, PRESENTER),
            entry(BVPushEvent.ReviewOpened(ID, PRESENTER)),
        )
    }

    @Test
    fun otherEventsAreNotKept() {
        assertNull(entry(BVPushEvent.Registered))
        assertNull(entry(BVPushEvent.SignedIn))
        assertNull(entry(BVPushEvent.PreferenceSynced(true)))
        assertNull(entry(BVPushEvent.Error("network", "corr-1")))
    }

    private fun entry(event: BVPushEvent) = PushEvents.toEntry(event, NOW)

    private companion object {
        const val ID = "n-1"
        const val NOW = 1_000L
        const val UNTIL = 9_000L
        const val REASON = "opted-out"
        const val PRESENTER = "custom-tabs"
    }
}
