package bv.garner.test.app

import com.bazaarvoice.push.push.QuietWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistTest {

    @Test
    fun allChecksPassWhenTheDeviceIsReady() {
        val checks = Checklist.evaluate(READY)
        assertEquals(CheckKind.entries, checks.map { it.kind })
        assertEquals(5, Checklist.passed(checks))
        assertEquals(CheckSummary.READY, Checklist.summary(checks))
    }

    @Test
    fun beforeInitTheSdkChecksWait() {
        val checks = Checklist.evaluate(
            READY.copy(initialized = false, deviceId = null, tokenSynced = false, hasSession = false,
                quiet = QuietStatus(QuietMode.UNKNOWN, null))
        )
        assertEquals(
            listOf(CheckMark.WAIT, CheckMark.WAIT, CheckMark.WAIT, CheckMark.OK, CheckMark.WAIT),
            checks.map { it.mark },
        )
        assertEquals(CheckSummary.CHECKING, Checklist.summary(checks))
    }

    @Test
    fun noSessionIsTheOneThingToFix() {
        val checks = Checklist.evaluate(READY.copy(hasSession = false))
        assertEquals(CheckMark.BAD, mark(checks, CheckKind.SIGNED_IN))
        assertEquals(4, Checklist.passed(checks))
        assertEquals(CheckSummary.ACTION, Checklist.summary(checks))
    }

    @Test
    fun registrationFailureAlsoFailsTheToken() {
        val checks = Checklist.evaluate(READY.copy(deviceId = null, tokenSynced = false))
        assertEquals(CheckMark.BAD, mark(checks, CheckKind.REGISTERED))
        assertEquals(CheckMark.BAD, mark(checks, CheckKind.PUSH_TOKEN))
    }

    @Test
    fun tokenWaitsUntilItIsSlow() {
        assertEquals(CheckMark.WAIT, mark(Checklist.evaluate(READY.copy(tokenSynced = false)), CheckKind.PUSH_TOKEN))
        assertEquals(
            CheckMark.BAD,
            mark(Checklist.evaluate(READY.copy(tokenSynced = false, slow = true)), CheckKind.PUSH_TOKEN),
        )
    }

    @Test
    fun quietHoursFailOnlyWhileInEffect() {
        val now = READY.copy(quiet = QuietStatus(QuietMode.ON_NOW, QuietWindow.DEFAULT))
        val later = READY.copy(quiet = QuietStatus(QuietMode.ON_LATER, QuietWindow.DEFAULT))
        assertEquals(CheckMark.BAD, mark(Checklist.evaluate(now), CheckKind.QUIET_HOURS))
        assertEquals(CheckMark.OK, mark(Checklist.evaluate(later), CheckKind.QUIET_HOURS))
    }

    @Test
    fun blockedNotificationsFail() {
        val checks = Checklist.evaluate(READY.copy(notificationsAllowed = false))
        assertEquals(CheckMark.BAD, mark(checks, CheckKind.NOTIFICATIONS))
    }

    private fun mark(checks: List<Check>, kind: CheckKind) = checks.single { it.kind == kind }.mark

    private companion object {
        val READY = HealthInput(
            initialized = true,
            deviceId = "3f2a9c1e-7b40-4d2e-9a61",
            tokenSynced = true,
            hasSession = true,
            notificationsAllowed = true,
            quiet = QuietStatus(QuietMode.OFF, null),
            slow = false,
        )
    }
}
