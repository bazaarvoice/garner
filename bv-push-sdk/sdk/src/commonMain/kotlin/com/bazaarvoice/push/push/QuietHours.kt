package com.bazaarvoice.push.push

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Quiet-hours evaluation on device-local wall-clock time.
 * Start inclusive, end exclusive; may cross midnight; equal bounds mean off.
 * Pure functions with an explicit now.
 */
public data class QuietWindow(val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int) {
    public companion object {
        public val DEFAULT: QuietWindow = QuietWindow(20, 0, 8, 0)
    }
}

public object QuietHours {

    /** Minutes after midnight 0..1439; true when [start, end) contains now. */
    public fun isQuiet(window: QuietWindow?, nowMinutes: Int): Boolean {
        if (window == null) return false
        val start = window.startHour * 60 + window.startMinute
        val end = window.endHour * 60 + window.endMinute
        if (start == end) return false
        return if (start < end) nowMinutes in start until end
        else nowMinutes >= start || nowMinutes < end
    }

    /** Minutes after midnight of the next end boundary from now. */
    public fun releaseInMinutes(window: QuietWindow, nowMinutes: Int): Int {
        val end = window.endHour * 60 + window.endMinute
        return (end - nowMinutes + 1440) % 1440
    }

    /**
     * Device-local wall-clock minutes for an instant in [zone], using the
     * platform timezone database. DST gaps skip wall
     * times (they are never observed); overlaps repeat them.
     */
    public fun wallMinutes(epochMillis: Long, zone: TimeZone): Int {
        val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
        return local.hour * 60 + local.minute
    }
}
