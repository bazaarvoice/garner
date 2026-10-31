package bv.garner.test.app

import com.bazaarvoice.push.events.Diagnostics
import java.util.TimeZone

enum class ReportSection(val title: String) {
    DEVICE(UiText.SECTION_DEVICE),
    APP(UiText.SECTION_APP),
    USER(UiText.SECTION_USER),
}

/** One Details row; [key] names it in the JSON copy. */
enum class ReportField(val section: ReportSection, val label: String, val key: String, val copyable: Boolean) {
    DEVICE_ID(ReportSection.DEVICE, UiText.FIELD_DEVICE_ID, "deviceId", true),
    FCM_TOKEN(ReportSection.DEVICE, UiText.FIELD_FCM_TOKEN, "fcmToken", true),
    PERMISSION(ReportSection.DEVICE, UiText.FIELD_PERMISSION, "notificationPermission", false),
    REGISTERED(ReportSection.DEVICE, UiText.FIELD_REGISTERED, "registered", false),
    TOKEN_SYNCED(ReportSection.DEVICE, UiText.FIELD_TOKEN_SYNCED, "tokenSynced", false),
    FCM_CONFLICT(ReportSection.DEVICE, UiText.FIELD_FCM_CONFLICT, "fcmConflict", false),
    APP_NAME(ReportSection.APP, UiText.FIELD_APP_NAME, "appName", false),
    APP_ID(ReportSection.APP, UiText.FIELD_APP_ID, "appId", true),
    PACKAGE(ReportSection.APP, UiText.FIELD_PACKAGE, "packageName", true),
    ENDPOINT(ReportSection.APP, UiText.FIELD_ENDPOINT, "baseUrl", true),
    ENVIRONMENT(ReportSection.APP, UiText.FIELD_ENVIRONMENT, "environment", false),
    SDK_VERSION(ReportSection.APP, UiText.FIELD_SDK_VERSION, "sdkVersion", false),
    USER(ReportSection.USER, UiText.FIELD_USER, "userId", true),
    EMAIL(ReportSection.USER, UiText.FIELD_EMAIL, "email", true),
    IDENTITY(ReportSection.USER, UiText.FIELD_IDENTITY, "identityPhase", false),
    SESSION(ReportSection.USER, UiText.FIELD_SESSION, "hasSession", false),
    SESSION_UNTIL(ReportSection.USER, UiText.FIELD_SESSION_UNTIL, "sessionExpiry", false),
    OPTED_OUT(ReportSection.USER, UiText.FIELD_OPTED_OUT, "optedOut", false),
    QUIET_HOURS(ReportSection.USER, UiText.FIELD_QUIET_HOURS, "quietHours", false),
    LAST_ERROR(ReportSection.USER, UiText.FIELD_LAST_ERROR, "lastError", true),
    CORRELATION_ID(ReportSection.USER, UiText.FIELD_CORRELATION_ID, "lastCorrelationId", true),
}

/** Everything the Details tab shows; [diag] is null until the SDK answers. */
data class ReportInput(
    val diag: Diagnostics?,
    val deviceId: String?,
    val stateError: String?,
    val fcmToken: String?,
    val appName: String,
    val packageName: String,
    val appId: Int,
    val baseUrl: String,
    val userId: String?,
    val email: String?,
    val quiet: QuietStatus,
    val zone: TimeZone,
)

/** Details rows and the "Copy all (JSON)" text. Pure Kotlin. */
object DetailsReport {
    fun values(input: ReportInput): List<Pair<ReportField, String>> =
        ReportField.entries.map { it to value(it, input) }

    /** Flat JSON object with every field as a string, in field order. */
    fun toJson(values: List<Pair<ReportField, String>>): String =
        values.joinToString(separator = ",\n", prefix = "{\n", postfix = "\n}") { (field, value) ->
            "  ${quote(field.key)}: ${quote(value)}"
        }

    /** JSON string literal with the escapes RFC 8259 requires. */
    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append(String.format("\\u%04x", c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    private fun value(field: ReportField, input: ReportInput): String {
        val diag = input.diag
        return when (field) {
            ReportField.DEVICE_ID -> input.deviceId ?: diag?.deviceId ?: UiText.NONE
            ReportField.FCM_TOKEN -> input.fcmToken ?: UiText.NONE
            ReportField.PERMISSION -> diag?.notificationPermission ?: UiText.UNKNOWN
            ReportField.REGISTERED -> flag(diag?.registered)
            ReportField.TOKEN_SYNCED -> flag(diag?.tokenSynced)
            ReportField.FCM_CONFLICT -> flag(diag?.fcmConflict)
            ReportField.APP_NAME -> input.appName
            ReportField.APP_ID -> (diag?.appId ?: input.appId).toString()
            ReportField.PACKAGE -> input.packageName
            ReportField.ENDPOINT -> input.baseUrl
            ReportField.ENVIRONMENT -> AppInfo.environment(input.baseUrl)
            ReportField.SDK_VERSION -> diag?.sdkVersion ?: UiText.UNKNOWN
            ReportField.USER -> input.userId?.takeIf { it.isNotBlank() } ?: UiText.NONE
            ReportField.EMAIL -> input.email?.takeIf { it.isNotBlank() } ?: UiText.NONE
            ReportField.IDENTITY -> diag?.identityPhase ?: UiText.UNKNOWN
            ReportField.SESSION -> flag(diag?.hasSession)
            ReportField.SESSION_UNTIL ->
                TimeText.epochMillis(diag?.sessionExpiry)?.let { TimeText.dateTime(it, input.zone) } ?: UiText.NONE
            ReportField.OPTED_OUT ->
                diag?.let { UiText.optedOutValue(it.desiredOptedOut, it.serverOptedOut) } ?: UiText.UNKNOWN
            ReportField.QUIET_HOURS -> QuietText.value(input.quiet)
            ReportField.LAST_ERROR -> input.stateError ?: diag?.lastError ?: UiText.NONE
            ReportField.CORRELATION_ID -> diag?.lastCorrelationId ?: UiText.NONE
        }
    }

    private fun flag(value: Boolean?): String = when (value) {
        true -> UiText.YES
        false -> UiText.NO
        null -> UiText.UNKNOWN
    }
}
