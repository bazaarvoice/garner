package bv.garner.test.app

import com.bazaarvoice.push.events.BVPushEvent
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class PushLabelsTest {

    @Test
    fun shortIdKeepsShortIdsAndShortensLongOnes() {
        assertEquals("id abc-123", PushLabels.shortId("abc-123"))
        assertEquals("id 7c41…e2a0", PushLabels.shortId(LONG_ID))
    }

    @Test
    fun titleAndBodyComeFromTheNotificationWhenKnown() {
        val record = record(listOf(PushEntry(LONG_ID, PushStep.SHOWN, AT)), PushContent(LONG_ID, TITLE, BODY))
        assertEquals(TITLE, PushLabels.title(record))
        assertEquals(BODY, PushLabels.body(record))
    }

    @Test
    fun withoutContentTheIdAndAStatusLineShow() {
        val hidden = record(listOf(PushEntry(LONG_ID, PushStep.HIDDEN, AT, "logged-out")))
        assertEquals("id 7c41…e2a0", PushLabels.title(hidden))
        assertEquals(UiText.BODY_HIDDEN, PushLabels.body(hidden))
        assertEquals(UiText.BODY_HELD, PushLabels.body(record(listOf(PushEntry(LONG_ID, PushStep.HELD, AT)))))
        assertEquals(UiText.BODY_RECEIVED, PushLabels.body(record(listOf(PushEntry(LONG_ID, PushStep.RECEIVED, AT)))))
        assertEquals(UiText.BODY_SHOWN_NO_TEXT, PushLabels.body(record(listOf(PushEntry(LONG_ID, PushStep.SHOWN, AT)))))
        assertEquals("id 7c41…e2a0", PushLabels.title(record(listOf(PushEntry(LONG_ID, PushStep.SHOWN, AT)),
            PushContent(LONG_ID, " ", ""))))
    }

    @Test
    fun chipsFollowTheStepsWithoutRepeats() {
        val heldUntil = AT + 10 * HOUR
        val record = record(
            listOf(
                PushEntry(LONG_ID, PushStep.RECEIVED, AT),
                PushEntry(LONG_ID, PushStep.HELD, AT, heldUntil.toString()),
                PushEntry(LONG_ID, PushStep.SHOWN, heldUntil),
                PushEntry(LONG_ID, PushStep.TAPPED, heldUntil + 1),
                PushEntry(LONG_ID, PushStep.REVIEW_OPENED, heldUntil + 2, "custom-tabs"),
            )
        )
        assertEquals(
            listOf(
                ToneText("Held until 08:00", Tone.WAIT),
                ToneText("Shown 08:00", Tone.OK),
                ToneText(UiText.STEP_TAPPED_REVIEW, Tone.OK),
            ),
            PushLabels.chips(record, UTC),
        )
    }

    @Test
    fun quickShowHiddenReasonsAndNoResultChips() {
        val shown = record(listOf(PushEntry(LONG_ID, PushStep.RECEIVED, AT), PushEntry(LONG_ID, PushStep.SHOWN, AT + 5)))
        assertEquals(listOf(ToneText(UiText.STEP_SHOWN, Tone.OK)), PushLabels.chips(shown, UTC))
        assertEquals(
            listOf(ToneText(UiText.STEP_RECEIVED_ONLY, Tone.WAIT)),
            PushLabels.chips(record(listOf(PushEntry(LONG_ID, PushStep.RECEIVED, AT))), UTC),
        )
        mapOf(
            "logged-out" to UiText.HIDDEN_LOGGED_OUT,
            "opted-out" to UiText.HIDDEN_OPTED_OUT,
            "permission-denied" to UiText.HIDDEN_PERMISSION,
            "channel-blocked" to UiText.HIDDEN_CHANNEL,
            "new-reason" to "Hidden: new-reason",
        ).forEach { (reason, label) ->
            val hidden = record(listOf(PushEntry(LONG_ID, PushStep.HIDDEN, AT, reason)))
            assertEquals(listOf(ToneText(label, Tone.BAD)), PushLabels.chips(hidden, UTC))
        }
    }

    @Test
    fun eventsReadInPlainWords() {
        assertEquals(ToneText(UiText.EVENT_SHOWN, Tone.OK), EventText.describe(BVPushEvent.NotificationDisplayed(LONG_ID), UTC))
        assertEquals(
            ToneText("Held for quiet hours (shows at 08:00)", Tone.WAIT),
            EventText.describe(BVPushEvent.NotificationHeld(LONG_ID, AT + 10 * HOUR), UTC),
        )
        assertEquals(
            ToneText("Hidden: logged-out (no user signed in: see Account)", Tone.BAD),
            EventText.describe(BVPushEvent.NotificationSuppressed(LONG_ID, "logged-out"), UTC),
        )
        assertEquals(
            ToneText("Review page opened (custom tabs)", Tone.OK),
            EventText.describe(BVPushEvent.ReviewOpened(LONG_ID, "custom-tabs"), UTC),
        )
        assertEquals(ToneText(UiText.EVENT_OPTED_OUT, Tone.NORMAL), EventText.describe(BVPushEvent.PreferenceSynced(true), UTC))
        assertEquals(ToneText(UiText.EVENT_REGISTERED, Tone.OK), EventText.describe(BVPushEvent.Registered, UTC))
    }

    private fun record(entries: List<PushEntry>, content: PushContent? = null): PushRecord =
        PushRecords.group(entries + listOfNotNull(content)).single()

    private companion object {
        const val LONG_ID = "7c41aa00-1111-2222-3333-44445555e2a0"
        const val TITLE = "How was your order?"
        const val BODY = "Tell other shoppers what you think."
        const val HOUR = 3_600_000L

        /** 2025-10-24 22:00 UTC. */
        const val AT = 1_761_343_200_000L
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }
}
