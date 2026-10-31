package bv.garner.test.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** App id, endpoint and user facts shown in the top bar and Details. Pure Kotlin. */
object AppInfo {
    private const val HTTPS = "https://"
    private const val SCHEME_END = "://"
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
    private val QA_MARKERS = listOf(".qa.", "-qa.", "-qa-")
    private val STAGING_MARKERS = listOf(".stg.", "-stg.", "staging")
    private val NAME_SEPARATORS = charArrayOf('.', '_', '-', '+')
    private const val NO_INITIALS = "?"

    /**
     * The environment the top bar and Details show (the only place name the app shows):
     * Local for plain HTTP or the emulator host, then QA / Staging by host name, else Production.
     */
    fun environment(baseUrl: String): String {
        val url = baseUrl.trim().lowercase(Locale.ROOT)
        val host = url.substringAfter(SCHEME_END).substringBefore('/').substringBefore(':')
        return when {
            !url.startsWith(HTTPS) || host in LOCAL_HOSTS -> UiText.ENV_LOCAL
            QA_MARKERS.any { host.contains(it) } -> UiText.ENV_QA
            STAGING_MARKERS.any { host.contains(it) } -> UiText.ENV_STAGING
            else -> UiText.ENV_PRODUCTION
        }
    }

    /** "tester.one@example.com" → "TO"; falls back to the user id, then "?". */
    fun initials(email: String?, userId: String?): String {
        val source = email?.substringBefore('@')?.takeIf { it.isNotBlank() } ?: userId?.takeIf { it.isNotBlank() }
            ?: return NO_INITIALS
        val parts = source.split(*NAME_SEPARATORS).filter { it.isNotEmpty() }
        val letters = if (parts.size >= 2) "${parts[0][0]}${parts[1][0]}" else source.take(2)
        return letters.uppercase(Locale.ROOT)
    }
}

/** Time texts in the device zone (passed in, so tests are fixed). */
object TimeText {
    private const val PATTERN_CLOCK = "HH:mm"
    private const val PATTERN_DATE_TIME = "d MMM HH:mm"
    private const val DAY_MILLIS = 86_400_000L

    fun clock(millis: Long, zone: TimeZone): String = format(PATTERN_CLOCK, millis, zone)

    fun dateTime(millis: Long, zone: TimeZone): String = format(PATTERN_DATE_TIME, millis, zone)

    /** Inbox time: "10:48" today, "Yesterday 23:14", else "24 Oct 10:43". */
    fun inbox(millis: Long, nowMillis: Long, zone: TimeZone): String =
        when (day(nowMillis, zone) - day(millis, zone)) {
            0L -> clock(millis, zone)
            1L -> UiText.YESTERDAY + " " + clock(millis, zone)
            else -> dateTime(millis, zone)
        }

    /** The SDK keeps the session expiry as epoch millis text; null or junk gives null. */
    fun epochMillis(raw: String?): Long? = raw?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    private fun day(millis: Long, zone: TimeZone): Long = Math.floorDiv(millis + zone.getOffset(millis), DAY_MILLIS)

    private fun format(pattern: String, millis: Long, zone: TimeZone): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = zone }.format(Date(millis))
}
