import BVGarner
import Foundation

enum ReportSection: CaseIterable {
    case device, app, user

    var title: String {
        switch self {
        case .device: return UiText.SECTION_DEVICE
        case .app: return UiText.SECTION_APP
        case .user: return UiText.SECTION_USER
        }
    }
}

/// One Details row; `key` names it in the JSON copy (same keys as Android, plus `apnsToken`).
enum ReportField: CaseIterable {
    case deviceId, fcmToken, apnsToken, permission, registered, tokenSynced, fcmConflict
    case appName, appId, bundleId, endpoint, environment, sdkVersion
    case user, email, identity, session, sessionUntil, optedOut, quietHours, lastError, correlationId

    var section: ReportSection {
        switch self {
        case .deviceId, .fcmToken, .apnsToken, .permission, .registered, .tokenSynced, .fcmConflict: return .device
        case .appName, .appId, .bundleId, .endpoint, .environment, .sdkVersion: return .app
        default: return .user
        }
    }

    var label: String {
        switch self {
        case .deviceId: return UiText.FIELD_DEVICE_ID
        case .fcmToken: return UiText.FIELD_FCM_TOKEN
        case .apnsToken: return UiText.FIELD_APNS_TOKEN
        case .permission: return UiText.FIELD_PERMISSION
        case .registered: return UiText.FIELD_REGISTERED
        case .tokenSynced: return UiText.FIELD_TOKEN_SYNCED
        case .fcmConflict: return UiText.FIELD_FCM_CONFLICT
        case .appName: return UiText.FIELD_APP_NAME
        case .appId: return UiText.FIELD_APP_ID
        case .bundleId: return UiText.FIELD_PACKAGE
        case .endpoint: return UiText.FIELD_ENDPOINT
        case .environment: return UiText.FIELD_ENVIRONMENT
        case .sdkVersion: return UiText.FIELD_SDK_VERSION
        case .user: return UiText.FIELD_USER
        case .email: return UiText.FIELD_EMAIL
        case .identity: return UiText.FIELD_IDENTITY
        case .session: return UiText.FIELD_SESSION
        case .sessionUntil: return UiText.FIELD_SESSION_UNTIL
        case .optedOut: return UiText.FIELD_OPTED_OUT
        case .quietHours: return UiText.FIELD_QUIET_HOURS
        case .lastError: return UiText.FIELD_LAST_ERROR
        case .correlationId: return UiText.FIELD_CORRELATION_ID
        }
    }

    var key: String {
        switch self {
        case .deviceId: return "deviceId"
        case .fcmToken: return "fcmToken"
        case .apnsToken: return "apnsToken"
        case .permission: return "notificationPermission"
        case .registered: return "registered"
        case .tokenSynced: return "tokenSynced"
        case .fcmConflict: return "fcmConflict"
        case .appName: return "appName"
        case .appId: return "appId"
        case .bundleId: return "bundleId"
        case .endpoint: return "baseUrl"
        case .environment: return "environment"
        case .sdkVersion: return "sdkVersion"
        case .user: return "userId"
        case .email: return "email"
        case .identity: return "identityPhase"
        case .session: return "hasSession"
        case .sessionUntil: return "sessionExpiry"
        case .optedOut: return "optedOut"
        case .quietHours: return "quietHours"
        case .lastError: return "lastError"
        case .correlationId: return "lastCorrelationId"
        }
    }

    var copyable: Bool {
        switch self {
        case .deviceId, .fcmToken, .apnsToken, .appId, .bundleId, .endpoint, .user, .email, .lastError, .correlationId:
            return true
        default:
            return false
        }
    }
}

/// Everything the Details tab shows; `diag` is nil until the SDK answers.
struct ReportInput {
    let diag: BVPush.Diagnostics?
    let deviceId: String?
    let stateError: String?
    let fcmToken: String?
    let apnsToken: String?
    let appName: String
    let bundleId: String
    let appId: Int
    let baseUrl: String
    let userId: String?
    let email: String?
    let quiet: QuietStatus
    let zone: TimeZone
}

struct ReportValue: Equatable {
    let field: ReportField
    let value: String
}

/// Details rows and the "Copy all (JSON)" text.
enum DetailsReport {
    static func values(_ input: ReportInput) -> [ReportValue] {
        ReportField.allCases.map { ReportValue(field: $0, value: value($0, input)) }
    }

    /// Flat JSON object with every field as a string, in field order.
    static func toJson(_ values: [ReportValue]) -> String {
        "{\n" + values.map { "  \(quote($0.field.key)): \(quote($0.value))" }.joined(separator: ",\n") + "\n}"
    }

    /// JSON string literal with the escapes RFC 8259 requires.
    static func quote(_ value: String) -> String {
        var out = "\""
        for scalar in value.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if scalar.value < 0x20 {
                    out += String(format: "\\u%04x", scalar.value)
                } else {
                    out.unicodeScalars.append(scalar)
                }
            }
        }
        return out + "\""
    }

    private static func value(_ field: ReportField, _ input: ReportInput) -> String {
        let diag = input.diag
        switch field {
        case .deviceId: return input.deviceId ?? diag?.deviceId ?? UiText.NONE
        case .fcmToken: return input.fcmToken ?? UiText.NONE
        case .apnsToken: return input.apnsToken ?? UiText.NONE
        case .permission: return diag?.notificationPermission ?? UiText.UNKNOWN
        case .registered: return flag(diag?.registered)
        case .tokenSynced: return flag(diag?.tokenSynced)
        case .fcmConflict: return flag(diag?.fcmConflict)
        case .appName: return input.appName
        case .appId: return String(diag?.appId ?? input.appId)
        case .bundleId: return input.bundleId
        case .endpoint: return input.baseUrl
        case .environment: return AppInfo.environment(input.baseUrl)
        case .sdkVersion: return diag?.sdkVersion ?? UiText.UNKNOWN
        case .user: return nonBlank(input.userId) ?? UiText.NONE
        case .email: return nonBlank(input.email) ?? UiText.NONE
        case .identity: return diag?.identityPhase ?? UiText.UNKNOWN
        case .session: return flag(diag?.hasSession)
        case .sessionUntil:
            return TimeText.epochMillis(diag?.sessionExpiry).map { TimeText.dateTime($0, input.zone) } ?? UiText.NONE
        case .optedOut:
            return diag.map { UiText.optedOutValue($0.desiredOptedOut, $0.serverOptedOut) } ?? UiText.UNKNOWN
        case .quietHours: return QuietText.value(input.quiet)
        case .lastError: return input.stateError ?? diag?.lastError ?? UiText.NONE
        case .correlationId: return diag?.lastCorrelationId ?? UiText.NONE
        }
    }

    private static func nonBlank(_ value: String?) -> String? {
        guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return value
    }

    private static func flag(_ value: Bool?) -> String {
        switch value {
        case true?: return UiText.YES
        case false?: return UiText.NO
        case nil: return UiText.UNKNOWN
        }
    }
}
