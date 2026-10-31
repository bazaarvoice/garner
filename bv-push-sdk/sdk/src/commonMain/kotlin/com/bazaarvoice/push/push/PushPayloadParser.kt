package com.bazaarvoice.push.push

/**
 * Parsed BV push. Never throws: inputs classify as
 * BV / not-BV / invalid. As-built keys first, alias keys accepted, then
 * the nested template value; unknown keys ignored; FCM keys never read.
 */
public sealed interface ParsedPush {
    public data object NotBv : ParsedPush
    public data class Invalid(val reason: String) : ParsedPush
    public data class BvPush(
        val notificationId: String,
        val title: String,
        val body: String,
        val imageUrl: String?,
        val reviewUrl: String?,
        val ctaText: String?,
    ) : ParsedPush
}

public object PushPayloadParser {
    private const val SOURCE = "push_sdk"

    public fun parse(data: Map<String, String>): ParsedPush {
        if (data["bv_source"] != SOURCE) return ParsedPush.NotBv
        val id = data["correspondence_id"] ?: data["bv_notification_id"]
            ?: return ParsedPush.Invalid("missing correspondence_id")
        val nested = parseNestedBody(data["body"])
        // The template's title is the notification title, as the Portal preview
        // shows it; the top-level title (hero product name) is the fallback.
        val title = firstNonBlank(nested?.title, data["title"])
            ?: return ParsedPush.Invalid("missing title")
        // Title-only template (PIE_FOLLOWUP): the product name is the body text.
        // Non-JSON body: shown as plain text.
        val body = if (nested != null) {
            firstNonBlank(nested.body, data["title"], nested.title)
        } else {
            firstNonBlank(data["body"])
        } ?: return ParsedPush.Invalid("missing body")
        val reviewUrl = firstNonBlank(
            data["review_url"], data["action_url"], nested?.reviewUrl)
        return ParsedPush.BvPush(
            notificationId = id,
            title = title,
            body = body,
            imageUrl = data["image"]?.ifBlank { null } ?: data["hero_image_url"]?.ifBlank { null },
            reviewUrl = reviewUrl?.ifBlank { null },
            ctaText = nested?.ctaText,
        )
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private data class NestedBody(val title: String?, val body: String?, val reviewUrl: String?, val ctaText: String?)

    private fun parseNestedBody(raw: String?): NestedBody? {
        if (raw.isNullOrBlank()) return null
        val title = raw.stringValue("title")
        val body = raw.stringValue("body")
        if (title == null && body == null) return null
        return NestedBody(title, body, raw.stringValue("reviewUrl"), raw.stringValue("ctaText"))
    }

    private const val QUOTE = '"'
    private const val NAME_SEPARATOR = ':'

    /** What `\s` matches in a Kotlin regex. */
    private const val WHITESPACE = " \t\n\u000B\u000C\r"

    /** Line terminators: what `.` doesn't match in a Kotlin regex. */
    private const val LINE_TERMINATORS = "\n\r\u0085\u2028\u2029"

    /**
     * The unescaped value of the first `"key": "string"` in this text, at any depth, or null.
     * Finds what the regex `"key"\s*:\s*"((?:[^"\\]|\\.)*)"` found, with a loop instead:
     * Kotlin/Native's regex engine recursed once per character of the value, so a long review
     * link (sign-in token) overflowed the stack and crashed the app on iOS.
     */
    private fun String.stringValue(key: String): String? {
        val name = "$QUOTE$key$QUOTE"
        var at = indexOf(name)
        while (at >= 0) {
            val value = stringAfterName(at + name.length)
            if (value != null) return value.unescape()
            at = indexOf(name, at + 1)
        }
        return null
    }

    /**
     * The raw string value after a member name ending before [start], or null if
     * `: "` doesn't follow or the string isn't closed.
     */
    private fun String.stringAfterName(start: Int): String? {
        var i = skipWhitespace(start)
        if (i >= length || this[i] != NAME_SEPARATOR) return null
        i = skipWhitespace(i + 1)
        if (i >= length || this[i] != QUOTE) return null
        val valueStart = i + 1
        i = valueStart
        while (i < length && this[i] != QUOTE) {
            if (this[i] != BACKSLASH) {
                i++
            } else if (i + 1 < length && this[i + 1] !in LINE_TERMINATORS) {
                i += SIMPLE_ESCAPE_LENGTH
            } else {
                return null
            }
        }
        return if (i < length) substring(valueStart, i) else null
    }

    private fun String.skipWhitespace(start: Int): Int {
        var i = start
        while (i < length && this[i] in WHITESPACE) i++
        return i
    }

    private const val BACKSLASH = '\\'
    private const val UNICODE_ESCAPE = 'u'
    private const val HEX_RADIX = 16
    private const val HEX_DIGITS = 4
    private const val SIMPLE_ESCAPE_LENGTH = 2
    private const val UNICODE_ESCAPE_LENGTH = SIMPLE_ESCAPE_LENGTH + HEX_DIGITS
    private const val FORM_FEED = '\u000C'

    /**
     * JSON string unescape (RFC 8259 section 7) in one left-to-right pass:
     * `\"` `\\` `\/` `\b` `\f` `\n` `\r` `\t` and `\uXXXX`. Kotlin strings are
     * UTF-16, so a surrogate pair (`\uD83D\uDE00`) is rebuilt by appending
     * both halves. A malformed escape is kept as written; never throws.
     */
    private fun String.unescape(): String {
        if (indexOf(BACKSLASH) < 0) return this
        val out = StringBuilder(length)
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c != BACKSLASH || i + 1 >= length) {
                out.append(c)
                i++
                continue
            }
            val next = this[i + 1]
            val simple = when (next) {
                '"' -> '"'
                BACKSLASH -> BACKSLASH
                '/' -> '/'
                'b' -> '\b'
                'f' -> FORM_FEED
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                else -> null
            }
            val code = if (next == UNICODE_ESCAPE) hexCode(i + SIMPLE_ESCAPE_LENGTH) else null
            when {
                simple != null -> {
                    out.append(simple)
                    i += SIMPLE_ESCAPE_LENGTH
                }
                code != null -> {
                    out.append(code.toChar())
                    i += UNICODE_ESCAPE_LENGTH
                }
                else -> {
                    out.append(c).append(next)
                    i += SIMPLE_ESCAPE_LENGTH
                }
            }
        }
        return out.toString()
    }

    /** The 4 hex digits at [start] as a UTF-16 code unit, or null if absent/malformed. */
    private fun String.hexCode(start: Int): Int? {
        if (start + HEX_DIGITS > length) return null
        var code = 0
        for (k in start until start + HEX_DIGITS) {
            val digit = this[k].digitToIntOrNull(HEX_RADIX) ?: return null
            code = code * HEX_RADIX + digit
        }
        return code
    }
}
