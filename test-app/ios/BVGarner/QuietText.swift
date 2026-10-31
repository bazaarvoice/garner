import BVGarner
import Foundation

/// Quiet hours as the screens show them.
enum QuietMode { case unknown, off, onNow, onLater }

struct QuietStatus: Equatable {
    let mode: QuietMode
    let window: BVPush.QuietWindow?

    var isDefault: Bool { window == BVPush.QuietWindow.default }
}

/// Reads `Diagnostics.quietWindow`, which is the SDK's `QuietWindow(startHour=…)` text or nil when
/// quiet hours are off. Equal start and end never hold a push, so they count as off.
enum QuietText {
    private static let windowParts = 4

    static func parse(_ raw: String?) -> BVPush.QuietWindow? {
        guard let raw else { return nil }
        let numbers = raw.split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
        guard numbers.count == windowParts else { return nil }
        return BVPush.QuietWindow(startHour: numbers[0], startMinute: numbers[1], endHour: numbers[2], endMinute: numbers[3])
    }

    /// The text the SDK reports for `window` (Kotlin `QuietWindow.toString()`).
    static func sdkText(_ window: BVPush.QuietWindow) -> String {
        "QuietWindow(startHour=\(window.startHour), startMinute=\(window.startMinute), " +
            "endHour=\(window.endHour), endMinute=\(window.endMinute))"
    }

    /// `sdkReady`: diagnostics were read; `raw`: their quietWindow.
    static func status(sdkReady: Bool, raw: String?, nowMinutes: Int) -> QuietStatus {
        guard sdkReady else { return QuietStatus(mode: .unknown, window: nil) }
        guard let raw else { return QuietStatus(mode: .off, window: nil) }
        guard let window = parse(raw) else { return QuietStatus(mode: .unknown, window: nil) }
        let start = window.startHour * 60 + window.startMinute
        let end = window.endHour * 60 + window.endMinute
        if start == end { return QuietStatus(mode: .off, window: window) }
        return QuietStatus(mode: isQuiet(start: start, end: end, now: nowMinutes) ? .onNow : .onLater, window: window)
    }

    /// The SDK rule: [start, end) contains now, wrapping past midnight.
    static func isQuiet(start: Int, end: Int, now: Int) -> Bool {
        if start == end { return false }
        return start < end ? (now >= start && now < end) : (now >= start || now < end)
    }

    static func range(_ window: BVPush.QuietWindow) -> String {
        String(format: "%02d:%02d–%02d:%02d", window.startHour, window.startMinute, window.endHour, window.endMinute)
    }

    static func end(_ window: BVPush.QuietWindow) -> String {
        String(format: "%02d:%02d", window.endHour, window.endMinute)
    }

    /// Account screen line, with " (default)" for the SDK default window.
    static func summary(_ status: QuietStatus) -> String {
        let text: String
        switch status.mode {
        case .unknown:
            return UiText.QUIET_SUMMARY_UNKNOWN
        case .off:
            return UiText.QUIET_SUMMARY_OFF
        case .onNow, .onLater:
            guard let window = status.window else { return UiText.QUIET_SUMMARY_OFF }
            text = status.mode == .onNow ? UiText.quietSummaryNow(range(window)) : UiText.quietSummaryLater(range(window))
        }
        return status.isDefault ? text + UiText.QUIET_DEFAULT_SUFFIX : text
    }

    /// Details value: "20:00–08:00" or "off".
    static func value(_ status: QuietStatus) -> String {
        switch status.mode {
        case .unknown: return UiText.UNKNOWN
        case .off: return UiText.QUIET_VALUE_OFF
        case .onNow, .onLater: return status.window.map(range) ?? UiText.UNKNOWN
        }
    }
}
