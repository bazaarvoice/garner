package bv.garner.test.app

import com.bazaarvoice.push.push.QuietWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietTextTest {

    @Test
    fun parsesTheSdkDiagnosticsText() {
        assertEquals(QuietWindow.DEFAULT, QuietText.parse(QuietWindow.DEFAULT.toString()))
        assertEquals(QuietWindow(23, 30, 7, 15), QuietText.parse(QuietWindow(23, 30, 7, 15).toString()))
        assertNull(QuietText.parse(null))
        assertNull(QuietText.parse("QuietWindow(startHour=20)"))
    }

    @Test
    fun statusFollowsTheWindowAndTheClock() {
        val night = QuietWindow.DEFAULT.toString()
        assertEquals(QuietMode.UNKNOWN, QuietText.status(sdkReady = false, raw = night, nowMinutes = 0).mode)
        assertEquals(QuietMode.OFF, QuietText.status(true, null, 0).mode)
        assertEquals(QuietMode.ON_NOW, QuietText.status(true, night, 21 * 60).mode)
        assertEquals(QuietMode.ON_NOW, QuietText.status(true, night, 7 * 60 + 59).mode)
        assertEquals(QuietMode.ON_LATER, QuietText.status(true, night, 8 * 60).mode)
        assertEquals(QuietMode.OFF, QuietText.status(true, QuietWindow(8, 0, 8, 0).toString(), 8 * 60).mode)
        assertEquals(QuietMode.UNKNOWN, QuietText.status(true, "junk", 0).mode)
        assertTrue(QuietText.status(true, night, 0).isDefault)
    }

    @Test
    fun textsForTheAccountAndDetailsScreens() {
        val window = QuietWindow(23, 0, 7, 30)
        assertEquals("23:00–07:30", QuietText.range(window))
        assertEquals("07:30", QuietText.end(window))
        assertEquals(UiText.QUIET_SUMMARY_OFF, QuietText.summary(QuietStatus(QuietMode.OFF, null)))
        assertEquals(UiText.QUIET_SUMMARY_UNKNOWN, QuietText.summary(QuietStatus(QuietMode.UNKNOWN, null)))
        assertEquals(
            "23:00–07:30: pushes in that window are held",
            QuietText.summary(QuietStatus(QuietMode.ON_LATER, window)),
        )
        assertEquals(
            "20:00–08:00, in effect now: pushes are held (default)",
            QuietText.summary(QuietStatus(QuietMode.ON_NOW, QuietWindow.DEFAULT)),
        )
        assertEquals("off", QuietText.value(QuietStatus(QuietMode.OFF, null)))
        assertEquals("23:00–07:30", QuietText.value(QuietStatus(QuietMode.ON_NOW, window)))
        assertEquals(UiText.UNKNOWN, QuietText.value(QuietStatus(QuietMode.UNKNOWN, null)))
    }
}
