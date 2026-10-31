package bv.garner.test.app

import com.bazaarvoice.push.events.BVPushEvent
import java.util.TimeZone

/** Colour family of a line or chip; the UI maps it to [BvColors]. */
enum class Tone { NORMAL, OK, BAD, WAIT }

data class ToneText(val text: String, val tone: Tone)

/** SDK events in plain words, for Recent SDK events. Pure Kotlin. */
object EventText {
    private const val REASON_LOGGED_OUT = "logged-out"
    private const val REASON_OPTED_OUT = "opted-out"
    private const val REASON_PERMISSION = "permission-denied"
    private const val REASON_CHANNEL = "channel-blocked"

    fun describe(event: BVPushEvent, zone: TimeZone): ToneText = when (event) {
        is BVPushEvent.NotificationReceived -> ToneText(UiText.EVENT_RECEIVED, Tone.NORMAL)
        is BVPushEvent.NotificationDisplayed -> ToneText(UiText.EVENT_SHOWN, Tone.OK)
        is BVPushEvent.NotificationHeld ->
            ToneText(UiText.eventHeld(TimeText.clock(event.untilMillis, zone)), Tone.WAIT)
        is BVPushEvent.NotificationSuppressed ->
            ToneText(UiText.eventHidden(event.reason, hint(event.reason)), Tone.BAD)
        is BVPushEvent.NotificationOpened -> ToneText(UiText.EVENT_TAPPED, Tone.NORMAL)
        is BVPushEvent.ReviewOpened -> ToneText(UiText.eventReview(event.presenter.replace('-', ' ')), Tone.OK)
        is BVPushEvent.Error -> ToneText(UiText.eventError(event.kind), Tone.BAD)
        is BVPushEvent.RegistrationBlocked -> ToneText(UiText.eventBlocked(event.reason), Tone.BAD)
        is BVPushEvent.PreferenceSynced ->
            ToneText(if (event.optedOut) UiText.EVENT_OPTED_OUT else UiText.EVENT_OPTED_IN, Tone.NORMAL)
        BVPushEvent.Registered -> ToneText(UiText.EVENT_REGISTERED, Tone.OK)
        BVPushEvent.PushTokenSynced -> ToneText(UiText.EVENT_TOKEN, Tone.NORMAL)
        BVPushEvent.IdentityLinked -> ToneText(UiText.EVENT_LINKED, Tone.OK)
        is BVPushEvent.IdentityPending -> ToneText(UiText.eventLoginWait(event.reason), Tone.WAIT)
        BVPushEvent.SignedIn -> ToneText(UiText.EVENT_SIGNED_IN, Tone.OK)
        BVPushEvent.SessionRefreshed -> ToneText(UiText.EVENT_REFRESHED, Tone.NORMAL)
        BVPushEvent.SignedOut -> ToneText(UiText.EVENT_SIGNED_OUT, Tone.NORMAL)
        BVPushEvent.IdentityUnlinked -> ToneText(UiText.EVENT_UNLINKED, Tone.NORMAL)
        else -> ToneText(event.toString(), Tone.NORMAL)
    }

    /** Account sign-in line while the SDK waits for a step before it logs in. */
    fun loginWait(reason: String): String = when (reason) {
        BVPushEvent.IdentityPending.NOT_REGISTERED -> UiText.SIGN_IN_WAIT_REGISTER
        BVPushEvent.IdentityPending.WAITING_FOR_PUSH_TOKEN -> UiText.SIGN_IN_WAIT_TOKEN
        else -> UiText.signInWaitOther(reason)
    }

    /** Inbox chip for a hidden push. */
    fun hiddenLabel(reason: String): String = when (reason) {
        REASON_LOGGED_OUT -> UiText.HIDDEN_LOGGED_OUT
        REASON_OPTED_OUT -> UiText.HIDDEN_OPTED_OUT
        REASON_PERMISSION -> UiText.HIDDEN_PERMISSION
        REASON_CHANNEL -> UiText.HIDDEN_CHANNEL
        else -> UiText.hiddenOther(reason)
    }

    private fun hint(reason: String): String = when (reason) {
        REASON_LOGGED_OUT -> UiText.HINT_LOGGED_OUT
        REASON_PERMISSION -> UiText.HINT_PERMISSION
        REASON_CHANNEL -> UiText.HINT_CHANNEL
        REASON_OPTED_OUT -> UiText.HINT_OPTED_OUT
        else -> ""
    }
}

/** Inbox card texts for one [PushRecord]. Pure Kotlin. */
object PushLabels {
    private const val SHORT_ID_MAX = 12
    private const val SHORT_ID_PART = 4
    private const val SHOWN_LATE_MILLIS = 60_000L

    /** "id 7c41…e2a0" for long ids. */
    fun shortId(id: String): String =
        if (id.length <= SHORT_ID_MAX) UiText.ID_PREFIX + id
        else UiText.ID_PREFIX + id.take(SHORT_ID_PART) + UiText.ELLIPSIS + id.takeLast(SHORT_ID_PART)

    fun title(record: PushRecord): String =
        record.content?.title?.takeIf { it.isNotBlank() } ?: shortId(record.id)

    fun body(record: PushRecord): String {
        record.content?.body?.takeIf { it.isNotBlank() }?.let { return it }
        return when (record.status) {
            PushStatus.HIDDEN -> UiText.BODY_HIDDEN
            PushStatus.HELD -> UiText.BODY_HELD
            PushStatus.RECEIVED -> UiText.BODY_RECEIVED
            PushStatus.SHOWN -> UiText.BODY_SHOWN_NO_TEXT
        }
    }

    /** Step chips in time order, without repeats; taps merge into one chip. */
    fun chips(record: PushRecord, zone: TimeZone): List<ToneText> {
        val tapped = record.steps.any { it.step == PushStep.TAPPED }
        val reviewed = record.steps.any { it.step == PushStep.REVIEW_OPENED }
        val chips = mutableListOf<ToneText>()
        record.steps.forEach { entry ->
            val chip = when (entry.step) {
                PushStep.RECEIVED -> null
                PushStep.SHOWN ->
                    if (entry.atMillis - record.firstAtMillis >= SHOWN_LATE_MILLIS) {
                        ToneText(UiText.stepShownAt(TimeText.clock(entry.atMillis, zone)), Tone.OK)
                    } else {
                        ToneText(UiText.STEP_SHOWN, Tone.OK)
                    }
                PushStep.HELD -> ToneText(
                    entry.detail.toLongOrNull()?.let { UiText.stepHeldUntil(TimeText.clock(it, zone)) }
                        ?: UiText.STEP_HELD,
                    Tone.WAIT,
                )
                PushStep.HIDDEN -> ToneText(EventText.hiddenLabel(entry.detail), Tone.BAD)
                PushStep.TAPPED, PushStep.REVIEW_OPENED -> ToneText(
                    when {
                        tapped && reviewed -> UiText.STEP_TAPPED_REVIEW
                        tapped -> UiText.STEP_TAPPED
                        else -> UiText.STEP_REVIEW
                    },
                    Tone.OK,
                )
            }
            if (chip != null && chip !in chips) chips += chip
        }
        if (chips.isEmpty()) chips += ToneText(UiText.STEP_RECEIVED_ONLY, Tone.WAIT)
        return chips
    }
}
