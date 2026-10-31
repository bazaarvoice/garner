package bv.garner.test.app

import com.bazaarvoice.push.events.Diagnostics
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailsReportTest {

    @Test
    fun everyFieldHasAValueInOrder() {
        val values = DetailsReport.values(input(diagnostics()))
        assertEquals(ReportField.entries, values.map { it.first })
        assertEquals("User ID", ReportField.USER.label)
        val byField = values.toMap()
        assertEquals(DEVICE_ID, byField[ReportField.DEVICE_ID])
        assertEquals(TOKEN, byField[ReportField.FCM_TOKEN])
        assertEquals("granted", byField[ReportField.PERMISSION])
        assertEquals(UiText.YES, byField[ReportField.REGISTERED])
        assertEquals(UiText.NO, byField[ReportField.FCM_CONFLICT])
        assertEquals("20000017", byField[ReportField.APP_ID])
        assertEquals(PACKAGE, byField[ReportField.PACKAGE])
        assertEquals(UiText.ENV_QA, byField[ReportField.ENVIRONMENT])
        assertEquals("1.0.0", byField[ReportField.SDK_VERSION])
        assertEquals(USER, byField[ReportField.USER])
        assertEquals("signed-in", byField[ReportField.IDENTITY])
        assertEquals("25 Oct 22:00", byField[ReportField.SESSION_UNTIL])
        assertEquals("false / server false", byField[ReportField.OPTED_OUT])
        assertEquals("off", byField[ReportField.QUIET_HOURS])
        assertEquals(UiText.NONE, byField[ReportField.LAST_ERROR])
        assertEquals(UiText.NONE, byField[ReportField.CORRELATION_ID])
    }

    @Test
    fun beforeTheSdkAnswersValuesAreUnknownOrNone() {
        val byField = DetailsReport.values(
            input(null).copy(deviceId = null, fcmToken = null, userId = "", email = null,
                quiet = QuietStatus(QuietMode.UNKNOWN, null))
        ).toMap()
        assertEquals(UiText.NONE, byField[ReportField.DEVICE_ID])
        assertEquals(UiText.NONE, byField[ReportField.FCM_TOKEN])
        assertEquals(UiText.UNKNOWN, byField[ReportField.PERMISSION])
        assertEquals(UiText.UNKNOWN, byField[ReportField.SESSION])
        assertEquals(UiText.UNKNOWN, byField[ReportField.OPTED_OUT])
        assertEquals(UiText.UNKNOWN, byField[ReportField.QUIET_HOURS])
        assertEquals(UiText.NONE, byField[ReportField.USER])
        assertEquals(UiText.NONE, byField[ReportField.SESSION_UNTIL])
        assertEquals("20000017", byField[ReportField.APP_ID])
    }

    @Test
    fun stateErrorWinsOverTheDiagnosticsError() {
        val byField = DetailsReport.values(
            input(diagnostics(lastError = "old")).copy(stateError = "APP_NOT_ACTIVE")
        ).toMap()
        assertEquals("APP_NOT_ACTIVE", byField[ReportField.LAST_ERROR])
    }

    @Test
    fun jsonListsEveryKeyAsAString() {
        val json = DetailsReport.toJson(
            listOf(ReportField.DEVICE_ID to DEVICE_ID, ReportField.LAST_ERROR to "say \"hi\"\n")
        )
        assertEquals("{\n  \"deviceId\": \"$DEVICE_ID\",\n  \"lastError\": \"say \\\"hi\\\"\\n\"\n}", json)
    }

    @Test
    fun quoteEscapesWhatJsonRequires() {
        assertEquals("\"a\\\\b\\tc\\r\\u0001\"", DetailsReport.quote("a\\b\tc\r\u0001"))
        assertEquals("\"ok – ✓\"", DetailsReport.quote("ok – ✓"))
    }

    private fun diagnostics(lastError: String? = null) = Diagnostics(
        sdkVersion = "1.0.0",
        appId = 20000017,
        registered = true,
        deviceId = DEVICE_ID,
        tokenSynced = true,
        identityPhase = "signed-in",
        hasSession = true,
        sessionExpiry = EXPIRY.toString(),
        desiredOptedOut = false,
        serverOptedOut = false,
        quietWindow = null,
        notificationPermission = "granted",
        fcmConflict = false,
        lastError = lastError,
        lastCorrelationId = null,
    )

    private fun input(diag: Diagnostics?) = ReportInput(
        diag = diag,
        deviceId = DEVICE_ID,
        stateError = null,
        fcmToken = TOKEN,
        appName = "BV Garner",
        packageName = PACKAGE,
        appId = 20000017,
        baseUrl = "https://push.qa.example.com",
        userId = USER,
        email = "tester.one@example.com",
        quiet = QuietStatus(QuietMode.OFF, null),
        zone = TimeZone.getTimeZone("UTC"),
    )

    private companion object {
        const val DEVICE_ID = "3f2a9c1e-7b40-4d2e-9a61"
        const val TOKEN = "dK3x:APA91bH"
        const val PACKAGE = "bv.garner.test.app"
        const val USER = "tester-001"

        /** 2025-10-25 22:00 UTC. */
        const val EXPIRY = 1_761_429_600_000L
    }
}
