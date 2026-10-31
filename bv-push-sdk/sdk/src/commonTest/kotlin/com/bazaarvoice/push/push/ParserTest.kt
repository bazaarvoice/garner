package com.bazaarvoice.push.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTest {
    private companion object {
        const val BODY_KEY = "body"
        const val PLAIN_BODY = "Tap to review"
        const val REVIEW_URL_KEY = "review_url"
        const val SERVER_ELLIPSIS = "\u2026"
        // PIE template text in Japanese: "How was the product?", "Please write a review".
        const val PIE_TITLE = "\u5546\u54C1\u306F\u3044\u304B\u304C\u3067\u3057\u305F\u304B\uFF1F"
        const val PIE_BODY = "\u30EC\u30D3\u30E5\u30FC\u3092\u66F8\u3044\u3066\u304F\u3060\u3055\u3044"
        const val SIGN_IN_TOKEN = "0a1b2c3d4e5f6789"
        const val SIGN_IN_TOKEN_REPEATS = 40
        const val LONG_VALUE_LENGTH = 100_000
    }

    private fun bv(extra: Map<String, String> = emptyMap()): Map<String, String> =
        mapOf(
            "bv_source" to "push_sdk",
            "correspondence_id" to "corr-1",
            "title" to "First Product",
            "body" to """{"title":"Review time","body":"Tap to review","data":{"reviewUrl":"https://example.com/r/1","ctaText":"Review"}}""",
            "image" to "https://example.com/i.jpg",
            "review_url" to "https://example.com/r/1",
        ) + extra

    // Server-shaped payload parses with nested template values.
    @Test
    fun parsesBuiltPayload() {
        val parsed = PushPayloadParser.parse(bv()) as ParsedPush.BvPush
        assertEquals("corr-1", parsed.notificationId)
        assertEquals("Review time", parsed.title)
        assertEquals("Tap to review", parsed.body)
        assertEquals("https://example.com/i.jpg", parsed.imageUrl)
        assertEquals("https://example.com/r/1", parsed.reviewUrl)
        assertEquals("Review", parsed.ctaText)
    }

    @Test
    fun aliasesAndFallbacks() {
        // Alias keys accepted.
        val aliased = PushPayloadParser.parse(bv(mapOf(
            "image" to "",
            "hero_image_url" to "https://example.com/hero.jpg",
            "review_url" to "",
            "action_url" to "https://example.com/a/2",
        ))) as ParsedPush.BvPush
        assertEquals("https://example.com/hero.jpg", aliased.imageUrl)
        assertEquals("https://example.com/a/2", aliased.reviewUrl)
        // Non-JSON body shown as plain text (Portal test push).
        val plain = PushPayloadParser.parse(bv(mapOf("body" to "plain text"))) as ParsedPush.BvPush
        assertEquals("First Product", plain.title)
        assertEquals("plain text", plain.body)
        // PIE_FOLLOWUP title-only body + escaped characters.
        val followup = PushPayloadParser.parse(bv(mapOf(
            "body" to """{"title":"Reminder"}""",
        ))) as ParsedPush.BvPush
        assertEquals("Reminder", followup.title)
        assertEquals("First Product", followup.body)
        // Raw string: `\n` is the JSON newline escape (Jackson emits it this way).
        // `\\n` would be an escaped backslash + n, see escapedBackslashIsNotANewline.
        val escaped = PushPayloadParser.parse(bv(mapOf(
            "body" to """{"title":"Hi \"there\"","body":"Line1\nLine2"}""",
        ))) as ParsedPush.BvPush
        assertEquals("Hi \"there\"", escaped.title)
        assertEquals("Line1\nLine2", escaped.body)
        // Non-BV and invalid inputs.
        assertTrue(PushPayloadParser.parse(mapOf("from" to "x")) is ParsedPush.NotBv)
        assertTrue(PushPayloadParser.parse(bv() - "correspondence_id") is ParsedPush.Invalid)
        assertTrue(PushPayloadParser.parse(bv(mapOf("title" to "", "body" to ""))) is ParsedPush.Invalid)
    }

    private fun nested(rawTitle: String, rawBody: String = PLAIN_BODY): ParsedPush.BvPush =
        PushPayloadParser.parse(bv(mapOf(
            BODY_KEY to "{\"title\":\"" + rawTitle + "\",\"body\":\"" + rawBody + "\"}",
        ))) as ParsedPush.BvPush

    // Every JSON string escape is decoded, so a
    // producer that escapes HTML or non-ASCII (Gson, Jackson options) shows
    // "Ben & Jerry's", not "Ben \u0026 Jerry's".
    @Test
    fun unescapesUnicodeEscapes() {
        assertEquals("Ben & Jerry's", nested("""Ben \u0026 Jerry\u0027s""").title)
        assertEquals("<b> <i>", nested("""\u003Cb\u003E \u003ci\u003e""").title)
        assertEquals("Caf\u00E9", nested("""Caf\u00e9""").title)
    }

    @Test
    fun unescapesControlAndSimpleEscapes() {
        val parsed = nested("""Tab\there""", """Line1\r\nLine2 \/ \b \f""")
        assertEquals("Tab\there", parsed.title)
        assertEquals("Line1\r\nLine2 / \b \u000C", parsed.body)
    }

    // A surrogate pair decodes to one emoji (UTF-16 halves appended in order).
    @Test
    fun unescapesSurrogatePair() {
        assertEquals("Hi \uD83D\uDE00", nested("""Hi \uD83D\uDE00""").title)
    }

    // Single left-to-right pass: an escaped backslash followed by n is a
    // backslash and the letter n, never a newline.
    @Test
    fun escapedBackslashIsNotANewline() {
        assertEquals("C:\\new", nested("""C:\\new""").title)
    }

    // Malformed or unknown escapes are kept as written; parsing never throws.
    @Test
    fun keepsMalformedEscapes() {
        assertEquals("\\u00ZZ \\x \\u12", nested("""\u00ZZ \x \u12""").title)
    }

    // The server's template renderer cuts a long title to 64 code points plus an
    // ellipsis and serialises the template with Jackson (compact, `&` and
    // non-ASCII literal). The parser returns it unchanged.
    @Test
    fun serverCutTitlePassesThrough() {
        // 64 code points (the emoji is one code point, two UTF-16 units).
        val cut = "Ben & Jerry's Chocolate Fudge Brownie Ice Cream \uD83C\uDF66 Pint 465ml Tub" +
            SERVER_ELLIPSIS
        val body = "{\"title\":\"" + cut + "\",\"body\":\"Tell us what you think\"," +
            "\"data\":{\"reviewUrl\":\"https://example.com/r/1?a=1&b=2\",\"ctaText\":\"Write a review\"}}"
        val parsed = PushPayloadParser.parse(bv(mapOf(BODY_KEY to body, REVIEW_URL_KEY to ""))) as ParsedPush.BvPush
        assertEquals(cut, parsed.title)
        assertEquals("Tell us what you think", parsed.body)
        assertEquals("https://example.com/r/1?a=1&b=2", parsed.reviewUrl)
        assertEquals("Write a review", parsed.ctaText)
    }

    // The PIE template as the server renders it for a push (Jackson,
    // `&` written as \u0026), with a sign-in token in the review link. Host, ids
    // and token are made up.
    @Test
    fun parsesRenderedPieTemplate() {
        val link = "https://reviews.example.com/write?clientId=example&productId=P1&user=" +
            SIGN_IN_TOKEN.repeat(SIGN_IN_TOKEN_REPEATS) + "&source=push"
        val body = "{\"title\": \"" + PIE_TITLE + "\", \"body\": \"" + PIE_BODY + "\", " +
            "\"data\": {\"reviewUrl\": \"" + link.replace("&", "\\u0026") + "\"}}"
        val parsed = PushPayloadParser.parse(bv(mapOf(BODY_KEY to body, REVIEW_URL_KEY to ""))) as ParsedPush.BvPush
        assertEquals(PIE_TITLE, parsed.title)
        assertEquals(PIE_BODY, parsed.body)
        assertEquals(link, parsed.reviewUrl)
        assertNull(parsed.ctaText)
    }

    // The regex this replaced recursed once per character on iOS, so a
    // review link over ~550 characters overflowed the main thread's stack. Any
    // length parses now.
    @Test
    fun parsesVeryLongValues() {
        val title = "T".repeat(LONG_VALUE_LENGTH)
        val link = "https://example.com/r?" + "a=1&".repeat(LONG_VALUE_LENGTH / "a=1&".length)
        val body = "{\"title\":\"" + title + "\",\"body\":\"Tap\",\"data\":{\"reviewUrl\":\"" +
            link.replace("&", "\\u0026") + "\"}}"
        val parsed = PushPayloadParser.parse(bv(mapOf(BODY_KEY to body, REVIEW_URL_KEY to ""))) as ParsedPush.BvPush
        assertEquals(title, parsed.title)
        assertEquals("Tap", parsed.body)
        assertEquals(link, parsed.reviewUrl)
    }

    // A string with no closing quote, or ending in a lone backslash, is absent.
    @Test
    fun unclosedValueIsAbsent() {
        val head = "{\"title\":\"T\",\"body\":\"B\",\"data\":{\"reviewUrl\":\"https://example.com/r?t="
        val unclosed = PushPayloadParser.parse(bv(mapOf(
            BODY_KEY to head + "a".repeat(LONG_VALUE_LENGTH),
            REVIEW_URL_KEY to "",
        ))) as ParsedPush.BvPush
        assertEquals("T", unclosed.title)
        assertEquals("B", unclosed.body)
        assertNull(unclosed.reviewUrl)
        val loneBackslash = PushPayloadParser.parse(bv(mapOf(
            BODY_KEY to head + "a\\",
            REVIEW_URL_KEY to "",
        ))) as ParsedPush.BvPush
        assertNull(loneBackslash.reviewUrl)
    }

    // Each key takes the first `"key": "string"` at any depth. A match without a
    // string value is skipped: the key text as a value, a number, an escape
    // followed by a line break. Whitespace around the colon is allowed.
    @Test
    fun firstStringValueOfEachKey() {
        val body = "{\"body\":\"title\",\"data\":{\"title\":1,\"ctaText\":\"a\\\nb\"}," +
            "\"title\" \t:\r\n \"Real\",\"ctaText\":\"Go\"}"
        val parsed = PushPayloadParser.parse(bv(mapOf(BODY_KEY to body))) as ParsedPush.BvPush
        assertEquals("Real", parsed.title)
        assertEquals("title", parsed.body)
        assertEquals("Go", parsed.ctaText)
    }

    // Display-policy decision table.
    @Test
    fun displayPolicyTable() {
        val parsed = PushPayloadParser.parse(bv())
        assertEquals(DisplayDecision.DISPLAY,
            DisplayPolicy.decide(parsed, optedOut = false, loggedOut = false, isTestPush = false, inQuietWindow = false))
        assertEquals(DisplayDecision.SUPPRESS_OPTED_OUT,
            DisplayPolicy.decide(parsed, optedOut = true, loggedOut = false, isTestPush = false, inQuietWindow = false))
        assertEquals(DisplayDecision.SUPPRESS_LOGGED_OUT,
            DisplayPolicy.decide(parsed, optedOut = false, loggedOut = true, isTestPush = false, inQuietWindow = false))
        assertEquals(DisplayDecision.DISPLAY,
            DisplayPolicy.decide(parsed, optedOut = false, loggedOut = true, isTestPush = true, inQuietWindow = false))
        assertEquals(DisplayDecision.HOLD,
            DisplayPolicy.decide(parsed, optedOut = false, loggedOut = false, isTestPush = false, inQuietWindow = true))
        assertEquals(DisplayDecision.NOT_HANDLED,
            DisplayPolicy.decide(ParsedPush.NotBv, optedOut = false, loggedOut = false, isTestPush = false, inQuietWindow = false))
        assertEquals(DisplayDecision.DROP,
            DisplayPolicy.decide(ParsedPush.Invalid("x"), optedOut = false, loggedOut = false, isTestPush = false, inQuietWindow = false))
    }

    // Quiet hours — midnight crossing, equal bounds, release math.
    @Test
    fun quietHours() {
        val night = QuietWindow(20, 0, 8, 0)
        assertEquals(true, QuietHours.isQuiet(night, 23 * 60))
        assertEquals(true, QuietHours.isQuiet(night, 7 * 60 + 59))
        assertEquals(false, QuietHours.isQuiet(night, 8 * 60))
        assertEquals(false, QuietHours.isQuiet(night, 12 * 60))
        assertEquals(false, QuietHours.isQuiet(QuietWindow(8, 0, 8, 0), 12 * 60))
        assertEquals(false, QuietHours.isQuiet(null, 23 * 60))
        assertEquals(60, QuietHours.releaseInMinutes(night, 7 * 60))
        val day = QuietWindow(9, 0, 17, 0)
        assertEquals(true, QuietHours.isQuiet(day, 12 * 60))
        assertEquals(false, QuietHours.isQuiet(day, 18 * 60))
    }

    // Quiet hours and DST: gap skips, overlap repeats, 30-minute zone, zone change.
    // Uses the real platform timezone database (no mocks).
    @Test
    fun quietHoursDst() {
        val newYork = kotlinx.datetime.TimeZone.of("America/New_York")
        // Spring forward 2026-03-08: 02:00–02:59 EST never happens.
        // 06:59:59Z = 01:59 EST, 07:00Z = 03:00 EDT.
        assertEquals(1 * 60 + 59, QuietHours.wallMinutes(1772953199000L, newYork))
        assertEquals(3 * 60, QuietHours.wallMinutes(1772953200000L, newYork))
        val gapWindow = QuietWindow(1, 0, 3, 0)
        assertEquals(true, QuietHours.isQuiet(gapWindow, QuietHours.wallMinutes(1772953199000L, newYork)))
        assertEquals(false, QuietHours.isQuiet(gapWindow, QuietHours.wallMinutes(1772953200000L, newYork)))
        // Fall back 2026-11-01: 01:00–01:59 happens twice (EDT then EST).
        // 05:30Z = 01:30 EDT, 06:30Z = 01:30 EST — same wall minutes, both quiet.
        val overlapWindow = QuietWindow(1, 0, 2, 0)
        assertEquals(1 * 60 + 30, QuietHours.wallMinutes(1793511000000L, newYork))
        assertEquals(1 * 60 + 30, QuietHours.wallMinutes(1793514600000L, newYork))
        assertEquals(true, QuietHours.isQuiet(overlapWindow, QuietHours.wallMinutes(1793511000000L, newYork)))
        assertEquals(true, QuietHours.isQuiet(overlapWindow, QuietHours.wallMinutes(1793514600000L, newYork)))
        // 30-minute DST zone Australia/Lord_Howe falls back 02:00 -> 01:30 on
        // 2026-04-05: 14:30Z = 01:30 +11:00, 15:00Z = 01:30 +10:30.
        val lordHowe = kotlinx.datetime.TimeZone.of("Australia/Lord_Howe")
        assertEquals(1 * 60 + 30, QuietHours.wallMinutes(1775313000000L, lordHowe))
        assertEquals(1 * 60 + 30, QuietHours.wallMinutes(1775314800000L, lordHowe))
        // Zone change between receipt and release: release math is pure wall
        // minutes; the conversion uses the current zone at each end.
        val night = QuietWindow(20, 0, 8, 0)
        val receiptWall = QuietHours.wallMinutes(1772953200000L, newYork)
        val releaseIn = QuietHours.releaseInMinutes(night, receiptWall)
        assertEquals((8 * 60 - receiptWall + 1440) % 1440, releaseIn)
    }
}
