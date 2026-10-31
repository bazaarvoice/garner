package bv.garner.test.app

/** The Home checklist: five things a push needs on this device. Pure Kotlin. */
enum class CheckKind { REGISTERED, PUSH_TOKEN, SIGNED_IN, NOTIFICATIONS, QUIET_HOURS }

enum class CheckMark { OK, BAD, WAIT }

data class Check(val kind: CheckKind, val mark: CheckMark)

/** Banner state above the checklist. */
enum class CheckSummary { READY, CHECKING, ACTION }

data class HealthInput(
    val initialized: Boolean,
    val deviceId: String?,
    val tokenSynced: Boolean,
    val hasSession: Boolean,
    val notificationsAllowed: Boolean,
    val quiet: QuietStatus,
    /** Registration or token sync has taken longer than expected. */
    val slow: Boolean,
)

object Checklist {
    fun evaluate(input: HealthInput): List<Check> = CheckKind.entries.map { Check(it, mark(it, input)) }

    fun passed(checks: List<Check>): Int = checks.count { it.mark == CheckMark.OK }

    fun summary(checks: List<Check>): CheckSummary = when {
        checks.all { it.mark == CheckMark.OK } -> CheckSummary.READY
        checks.any { it.mark == CheckMark.BAD } -> CheckSummary.ACTION
        else -> CheckSummary.CHECKING
    }

    private fun mark(kind: CheckKind, input: HealthInput): CheckMark = when (kind) {
        CheckKind.REGISTERED -> when {
            input.deviceId != null -> CheckMark.OK
            !input.initialized -> CheckMark.WAIT
            else -> CheckMark.BAD
        }
        CheckKind.PUSH_TOKEN -> when {
            input.tokenSynced -> CheckMark.OK
            !input.initialized -> CheckMark.WAIT
            input.deviceId == null || input.slow -> CheckMark.BAD
            else -> CheckMark.WAIT
        }
        CheckKind.SIGNED_IN -> when {
            input.hasSession -> CheckMark.OK
            !input.initialized -> CheckMark.WAIT
            else -> CheckMark.BAD
        }
        CheckKind.NOTIFICATIONS -> if (input.notificationsAllowed) CheckMark.OK else CheckMark.BAD
        CheckKind.QUIET_HOURS -> when (input.quiet.mode) {
            QuietMode.OFF, QuietMode.ON_LATER -> CheckMark.OK
            QuietMode.ON_NOW -> CheckMark.BAD
            QuietMode.UNKNOWN -> CheckMark.WAIT
        }
    }
}
