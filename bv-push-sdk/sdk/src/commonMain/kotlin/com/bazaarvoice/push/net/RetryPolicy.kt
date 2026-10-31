package com.bazaarvoice.push.net

import kotlin.random.Random

/**
 * Delay schedule with jitter; Retry-After in seconds or HTTP-date.
 * Pure functions with explicit randomness source.
 */
public object RetryPolicy {
    private val delaysMillis = longArrayOf(1000L, 2000L, 4000L)

    /** Attempt is zero-based; after the table, the caller parks on triggers. */
    public fun delayForAttempt(attempt: Int, random: Random = Random): Long {
        val base = delaysMillis[attempt.coerceAtMost(delaysMillis.size - 1)]
        val jitter = (random.nextDouble() - 0.5) * base / 2
        return (base + jitter).toLong().coerceIn(0L, 30_000L)
    }

    /** Parses Retry-After (delay-seconds or HTTP-date) against now; at least 1 s. */
    public fun retryAfterMillis(header: String?, nowMillis: Long): Long {
        val seconds = header?.toLongOrNull()
        if (seconds != null) return maxOf(1000L, seconds * 1000L)
        val dateMillis = header?.let { parseHttpDate(it) }
        if (dateMillis != null) return maxOf(1000L, dateMillis - nowMillis)
        return 1000L
    }

    private val months = mapOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
        "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12,
    )

    /** IMF-fixdate IMF ("Sun, 06 Nov 1994 08:49:37 GMT"); null when unparseable. */
    public fun parseHttpDate(header: String): Long? {
        return try {
            val parts = header.split(' ')
            val fields = if (parts.size == 6 && parts[0].endsWith(",")) parts.drop(1) else parts
            if (fields.size != 5) return null
            val day = fields[0].toInt()
            val month = months[fields[1]] ?: return null
            val year = fields[2].toInt()
            val time = fields[3].split(':')
            if (time.size != 3) return null
            daysSinceEpoch(year, month, day) * 86_400_000L +
                ((time[0].toInt() * 60 + time[1].toInt()) * 60 + time[2].toInt()) * 1000L
        } catch (_: Exception) {
            null
        }
    }

    private fun isLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

    private fun daysSinceEpoch(year: Int, month: Int, day: Int): Long {
        val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var days = (year - 1970) * 365L
        var leapDays = 0L
        var y = 1970
        while (y < year) {
            if (isLeap(y)) leapDays++
            y++
        }
        days += leapDays + monthDays[month - 1] + (day - 1)
        if (month > 2 && isLeap(year)) days++
        return days
    }
}
