import BVGarner
import Foundation

/// The four tabs of the bottom bar, in order.
enum TabId: String, CaseIterable, Identifiable {
    case HOME, ACCOUNT, INBOX, DETAILS

    var id: String { rawValue }

    var label: String {
        switch self {
        case .HOME: return UiText.TAB_HOME
        case .ACCOUNT: return UiText.TAB_ACCOUNT
        case .INBOX: return UiText.TAB_INBOX
        case .DETAILS: return UiText.TAB_DETAILS
        }
    }

    /// SF Symbol name.
    var icon: String {
        switch self {
        case .HOME: return UiText.ICON_HOME
        case .ACCOUNT: return UiText.ICON_ACCOUNT
        case .INBOX: return UiText.ICON_INBOX
        case .DETAILS: return UiText.ICON_DETAILS
        }
    }

    /// The tab with this name (saved state, `-tab INBOX` launch argument), or nil.
    static func fromName(_ name: String?) -> TabId? {
        guard let name else { return nil }
        return TabId(rawValue: name)
    }
}

/// One read of everything the screens show; built by AppModel on every refresh.
struct Snapshot {
    let state: BVPush.State
    /// Nil until the SDK answers diagnostics().
    let diag: BVPush.Diagnostics?
    let notificationsAllowed: Bool
    let fcmToken: String?
    let apnsToken: String?
    /// Registration has taken longer than expected since the app opened.
    let slow: Bool
    let nowMillis: Int64
    /// Device wall-clock minutes after midnight, for quiet hours.
    let nowMinutes: Int
    /// The app id from the build settings, shown until the SDK reports one.
    let configuredAppId: Int

    var signedIn: Bool { diag?.hasSession == true }
    var quiet: QuietStatus { QuietText.status(sdkReady: diag != nil, raw: diag?.quietWindow, nowMinutes: nowMinutes) }
    var appId: Int { diag?.appId ?? configuredAppId }

    static let empty = Snapshot(
        state: .notConfigured, diag: nil, notificationsAllowed: false, fcmToken: nil, apnsToken: nil,
        slow: false, nowMillis: 0, nowMinutes: 0, configuredAppId: 0
    )
}
