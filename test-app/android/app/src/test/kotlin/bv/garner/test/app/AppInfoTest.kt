package bv.garner.test.app

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppInfoTest {

    @Test
    fun environmentComesFromTheEndpoint() {
        assertEquals(UiText.ENV_LOCAL, AppInfo.environment("http://10.0.2.2:8082"))
        assertEquals(UiText.ENV_LOCAL, AppInfo.environment("https://localhost:8443/api"))
        assertEquals(UiText.ENV_QA, AppInfo.environment("https://push-cert.qa.example.com"))
        assertEquals(UiText.ENV_STAGING, AppInfo.environment("https://push.stg.example.com"))
        assertEquals(UiText.ENV_PRODUCTION, AppInfo.environment(" https://push.prod.bazaarvoice.com "))
    }

    @Test
    fun initialsUseTheEmailNameThenTheUserId() {
        assertEquals("TO", AppInfo.initials("tester.one@example.com", "tester-001"))
        assertEquals("AL", AppInfo.initials("alex@example.com", null))
        assertEquals("TE", AppInfo.initials("", "tester"))
        assertEquals("JD", AppInfo.initials(null, "john_doe"))
        assertEquals("?", AppInfo.initials(" ", ""))
    }

    @Test
    fun timesUseTheGivenZone() {
        val utc = TimeZone.getTimeZone("UTC")
        val india = TimeZone.getTimeZone("Asia/Calcutta")
        assertEquals("22:00", TimeText.clock(AT, utc))
        assertEquals("03:30", TimeText.clock(AT, india))
        assertEquals("24 Oct 22:00", TimeText.dateTime(AT, utc))
    }

    @Test
    fun inboxTimeIsTodayYesterdayOrADate() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("22:00", TimeText.inbox(AT, AT + HOUR, utc))
        assertEquals("Yesterday 22:00", TimeText.inbox(AT, AT + 3 * HOUR, utc))
        assertEquals("24 Oct 22:00", TimeText.inbox(AT, AT + 30 * HOUR, utc))
        // 03:30 on 25 Oct in India: the same instant is "today" there two hours later.
        assertEquals("03:30", TimeText.inbox(AT, AT + 2 * HOUR, TimeZone.getTimeZone("Asia/Calcutta")))
    }

    @Test
    fun sessionExpiryIsEpochMillisText() {
        assertEquals(AT, TimeText.epochMillis(" $AT "))
        assertNull(TimeText.epochMillis(null))
        assertNull(TimeText.epochMillis("tomorrow"))
        assertNull(TimeText.epochMillis("0"))
    }

    private companion object {
        /** 2025-10-24 22:00 UTC. */
        const val AT = 1_761_343_200_000L
        const val HOUR = 3_600_000L
    }
}
