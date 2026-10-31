package bv.garner.test.app

import com.bazaarvoice.push.events.BVPushEvent

/**
 * The Inbox model: every push the SDK reported, kept on the device.
 * Pure Kotlin (no Android classes), so JVM unit tests cover it.
 */
enum class PushStep(val code: String) {
    RECEIVED("received"),
    SHOWN("shown"),
    HELD("held"),
    HIDDEN("hidden"),
    TAPPED("tapped"),
    REVIEW_OPENED("review"),
    ;

    companion object {
        fun fromCode(code: String): PushStep? = entries.firstOrNull { it.code == code }
    }
}

/** One line of the push history file. */
sealed interface PushLine {
    val id: String
}

/**
 * One SDK event for a push. [detail] is the hidden reason, the held-until epoch
 * millis or the review presenter; empty for the other steps.
 */
data class PushEntry(
    override val id: String,
    val step: PushStep,
    val atMillis: Long,
    val detail: String = "",
) : PushLine

/** Title and body read back from the posted notification. */
data class PushContent(override val id: String, val title: String, val body: String) : PushLine

/** Where a push ended up; the Inbox filters on it. */
enum class PushStatus { RECEIVED, SHOWN, HELD, HIDDEN }

/** All the steps of one notification id, oldest step first. */
data class PushRecord(
    val id: String,
    val firstAtMillis: Long,
    val steps: List<PushEntry>,
    val content: PushContent?,
) {
    /** The latest outcome wins: a push held at night and shown at 08:00 is shown. */
    val status: PushStatus
        get() {
            val outcome = steps.lastOrNull { it.step in OUTCOMES }
            return when (outcome?.step) {
                PushStep.SHOWN -> PushStatus.SHOWN
                PushStep.HELD -> PushStatus.HELD
                PushStep.HIDDEN -> PushStatus.HIDDEN
                else -> if (steps.any { it.step in TAPS }) PushStatus.SHOWN else PushStatus.RECEIVED
            }
        }

    private companion object {
        val OUTCOMES = setOf(PushStep.SHOWN, PushStep.HELD, PushStep.HIDDEN)
        val TAPS = setOf(PushStep.TAPPED, PushStep.REVIEW_OPENED)
    }
}

/** Inbox filter chips. [ALL] also lists pushes that have no outcome yet. */
enum class PushFilter(private val status: PushStatus?) {
    ALL(null),
    SHOWN(PushStatus.SHOWN),
    HELD(PushStatus.HELD),
    HIDDEN(PushStatus.HIDDEN),
    ;

    fun matches(record: PushRecord): Boolean = status == null || record.status == status
}

object PushRecords {
    /** Records kept on the device until the tester taps Clear. */
    const val MAX_RECORDS = 500

    /** One record per notification id, newest first. Content without any event is dropped. */
    fun group(lines: List<PushLine>): List<PushRecord> {
        val steps = LinkedHashMap<String, MutableList<PushEntry>>()
        val content = HashMap<String, PushContent>()
        lines.forEach { line ->
            when (line) {
                is PushEntry -> steps.getOrPut(line.id) { mutableListOf() }.add(line)
                is PushContent -> content[line.id] = line
            }
        }
        return steps.map { (id, entries) ->
            PushRecord(id, entries.minOf { it.atMillis }, entries.sortedBy { it.atMillis }, content[id])
        }.sortedByDescending { it.firstAtMillis }
    }

    /** The lines of the newest [max] records, in their original order. */
    fun limit(lines: List<PushLine>, max: Int = MAX_RECORDS): List<PushLine> {
        val keep = group(lines).take(max).mapTo(HashSet()) { it.id }
        return lines.filter { it.id in keep }
    }

    /** Records first received after the tester last looked at the Inbox. */
    fun unseen(records: List<PushRecord>, seenAtMillis: Long): Int =
        records.count { it.firstAtMillis > seenAtMillis }

    fun count(records: List<PushRecord>, filter: PushFilter): Int = records.count { filter.matches(it) }
}

/** Maps SDK notification events to history entries; other events give null. */
object PushEvents {
    fun toEntry(event: BVPushEvent, nowMillis: Long): PushEntry? = when (event) {
        is BVPushEvent.NotificationReceived -> PushEntry(event.id, PushStep.RECEIVED, nowMillis)
        is BVPushEvent.NotificationDisplayed -> PushEntry(event.id, PushStep.SHOWN, nowMillis)
        is BVPushEvent.NotificationHeld ->
            PushEntry(event.id, PushStep.HELD, nowMillis, event.untilMillis.toString())
        is BVPushEvent.NotificationSuppressed -> PushEntry(event.id, PushStep.HIDDEN, nowMillis, event.reason)
        is BVPushEvent.NotificationOpened -> PushEntry(event.id, PushStep.TAPPED, nowMillis)
        is BVPushEvent.ReviewOpened -> PushEntry(event.id, PushStep.REVIEW_OPENED, nowMillis, event.presenter)
        else -> null
    }
}

/**
 * History file format v1: one line per [PushLine], tab-separated fields,
 * backslash escapes for backslash, tab, newline and carriage return.
 *
 *   v1  E  <id>  <step code>  <epoch millis>  <detail>
 *   v1  C  <id>  <title>  <body>
 *
 * Unknown versions and broken lines decode to null and are skipped.
 */
object PushLogCodec {
    private const val VERSION = "v1"
    private const val KIND_ENTRY = "E"
    private const val KIND_CONTENT = "C"
    private const val SEPARATOR = '\t'
    private const val ESCAPE = '\\'
    private const val ENTRY_FIELDS = 6
    private const val CONTENT_FIELDS = 5

    fun encode(line: PushLine): String = when (line) {
        is PushEntry -> join(VERSION, KIND_ENTRY, line.id, line.step.code, line.atMillis.toString(), line.detail)
        is PushContent -> join(VERSION, KIND_CONTENT, line.id, line.title, line.body)
    }

    fun decode(text: String): PushLine? {
        val fields = split(text)
        if (fields.size < 3 || fields[0] != VERSION || fields[2].isEmpty()) return null
        return when (fields[1]) {
            KIND_ENTRY -> {
                if (fields.size != ENTRY_FIELDS) return null
                val step = PushStep.fromCode(fields[3]) ?: return null
                val at = fields[4].toLongOrNull() ?: return null
                PushEntry(fields[2], step, at, fields[5])
            }
            KIND_CONTENT -> {
                if (fields.size != CONTENT_FIELDS) return null
                PushContent(fields[2], fields[3], fields[4])
            }
            else -> null
        }
    }

    private fun join(vararg fields: String): String =
        fields.joinToString(SEPARATOR.toString()) { escape(it) }

    private fun escape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                ESCAPE -> append(ESCAPE).append(ESCAPE)
                '\t' -> append(ESCAPE).append('t')
                '\n' -> append(ESCAPE).append('n')
                '\r' -> append(ESCAPE).append('r')
                else -> append(c)
            }
        }
    }

    /** Splits on unescaped tabs and unescapes each field. */
    private fun split(text: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == SEPARATOR) {
                fields.add(current.toString())
                current.setLength(0)
            } else if (c == ESCAPE && i + 1 < text.length) {
                i++
                current.append(
                    when (text[i]) {
                        't' -> '\t'
                        'n' -> '\n'
                        'r' -> '\r'
                        else -> text[i]
                    }
                )
            } else {
                current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
