package bv.garner.test.app

import com.bazaarvoice.push.push.QuietHours
import com.bazaarvoice.push.push.QuietWindow
import java.util.Locale

/** Quiet hours as the screens show them. */
enum class QuietMode { UNKNOWN, OFF, ON_NOW, ON_LATER }

data class QuietStatus(val mode: QuietMode, val window: QuietWindow?) {
    val isDefault: Boolean get() = window == QuietWindow.DEFAULT
}

/**
 * Reads Diagnostics.quietWindow, which is QuietWindow.toString() or null when
 * quiet hours are off. Equal start and end never hold a push, so they count as off.
 */
object QuietText {
    private val NUMBER = Regex("\\d+")
    private const val RANGE_FORMAT = "%02d:%02d–%02d:%02d"
    private const val CLOCK_FORMAT = "%02d:%02d"
    private const val WINDOW_PARTS = 4

    fun parse(raw: String?): QuietWindow? {
        val n = raw?.let { text -> NUMBER.findAll(text).map { it.value.toInt() }.toList() } ?: return null
        return if (n.size == WINDOW_PARTS) QuietWindow(n[0], n[1], n[2], n[3]) else null
    }

    /** [sdkReady]: diagnostics were read; [raw]: their quietWindow. */
    fun status(sdkReady: Boolean, raw: String?, nowMinutes: Int): QuietStatus {
        if (!sdkReady) return QuietStatus(QuietMode.UNKNOWN, null)
        if (raw == null) return QuietStatus(QuietMode.OFF, null)
        val window = parse(raw) ?: return QuietStatus(QuietMode.UNKNOWN, null)
        val start = window.startHour * 60 + window.startMinute
        val end = window.endHour * 60 + window.endMinute
        return when {
            start == end -> QuietStatus(QuietMode.OFF, window)
            QuietHours.isQuiet(window, nowMinutes) -> QuietStatus(QuietMode.ON_NOW, window)
            else -> QuietStatus(QuietMode.ON_LATER, window)
        }
    }

    fun range(window: QuietWindow): String = String.format(
        Locale.US, RANGE_FORMAT, window.startHour, window.startMinute, window.endHour, window.endMinute,
    )

    fun end(window: QuietWindow): String = String.format(Locale.US, CLOCK_FORMAT, window.endHour, window.endMinute)

    /** Account screen line, with " (default)" for the SDK default window. */
    fun summary(status: QuietStatus): String {
        val window = status.window
        val text = when {
            status.mode == QuietMode.UNKNOWN -> return UiText.QUIET_SUMMARY_UNKNOWN
            status.mode == QuietMode.OFF || window == null -> return UiText.QUIET_SUMMARY_OFF
            status.mode == QuietMode.ON_NOW -> UiText.quietSummaryNow(range(window))
            else -> UiText.quietSummaryLater(range(window))
        }
        return if (status.isDefault) text + UiText.QUIET_DEFAULT_SUFFIX else text
    }

    /** Details value: "20:00–08:00" or "off". */
    fun value(status: QuietStatus): String = when (status.mode) {
        QuietMode.UNKNOWN -> UiText.UNKNOWN
        QuietMode.OFF -> UiText.QUIET_VALUE_OFF
        QuietMode.ON_NOW, QuietMode.ON_LATER -> status.window?.let { range(it) } ?: UiText.UNKNOWN
    }
}
