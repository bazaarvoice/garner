import BVGarner
import Foundation

/// Colour family of a line or chip; the UI maps it to `BvColors`.
enum Tone { case normal, ok, bad, wait }

struct ToneText: Equatable, Hashable {
    let text: String
    let tone: Tone
}

/// SDK events in plain words, for Recent SDK events.
enum EventText {
    private static let reasonLoggedOut = "logged-out"
    private static let reasonOptedOut = "opted-out"
    private static let reasonPermission = "permission-denied"
    private static let reasonChannel = "channel-blocked"

    static func describe(_ event: BVPush.Event, _ zone: TimeZone) -> ToneText {
        switch event {
        case .notificationReceived: return ToneText(text: UiText.EVENT_RECEIVED, tone: .normal)
        case .notificationDisplayed: return ToneText(text: UiText.EVENT_SHOWN, tone: .ok)
        case let .notificationHeld(_, untilMillis):
            return ToneText(text: UiText.eventHeld(TimeText.clock(untilMillis, zone)), tone: .wait)
        case let .notificationSuppressed(_, reason):
            return ToneText(text: UiText.eventHidden(reason, hint(reason)), tone: .bad)
        case .notificationOpened: return ToneText(text: UiText.EVENT_TAPPED, tone: .normal)
        case let .reviewOpened(_, presenter):
            return ToneText(text: UiText.eventReview(presenter.replacingOccurrences(of: "-", with: " ")), tone: .ok)
        case let .error(kind): return ToneText(text: UiText.eventError(kind), tone: .bad)
        case let .registrationBlocked(reason): return ToneText(text: UiText.eventBlocked(reason), tone: .bad)
        case let .preferenceSynced(optedOut):
            return ToneText(text: optedOut ? UiText.EVENT_OPTED_OUT : UiText.EVENT_OPTED_IN, tone: .normal)
        case .registered: return ToneText(text: UiText.EVENT_REGISTERED, tone: .ok)
        case .pushTokenSynced: return ToneText(text: UiText.EVENT_TOKEN, tone: .normal)
        case .identityLinked: return ToneText(text: UiText.EVENT_LINKED, tone: .ok)
        case let .identityPending(reason): return ToneText(text: UiText.eventLoginWait(reason), tone: .wait)
        case .signedIn: return ToneText(text: UiText.EVENT_SIGNED_IN, tone: .ok)
        case .sessionRefreshed: return ToneText(text: UiText.EVENT_REFRESHED, tone: .normal)
        case .signedOut: return ToneText(text: UiText.EVENT_SIGNED_OUT, tone: .normal)
        case .identityUnlinked: return ToneText(text: UiText.EVENT_UNLINKED, tone: .normal)
        }
    }

    /// True for a `linkIdentity` completion that means "kept, signs in by itself later".
    static func isLoginWait(_ reason: String) -> Bool {
        reason == BVPush.loginWaitNotRegistered || reason == BVPush.loginWaitPushToken
    }

    /// Account sign-in line while the SDK waits for a step before it logs in.
    static func loginWait(_ reason: String) -> String {
        switch reason {
        case BVPush.loginWaitNotRegistered: return UiText.SIGN_IN_WAIT_REGISTER
        case BVPush.loginWaitPushToken: return UiText.SIGN_IN_WAIT_TOKEN
        default: return UiText.signInWaitOther(reason)
        }
    }

    /// Inbox chip for a hidden push.
    static func hiddenLabel(_ reason: String) -> String {
        switch reason {
        case reasonLoggedOut: return UiText.HIDDEN_LOGGED_OUT
        case reasonOptedOut: return UiText.HIDDEN_OPTED_OUT
        case reasonPermission: return UiText.HIDDEN_PERMISSION
        case reasonChannel: return UiText.HIDDEN_CHANNEL
        default: return UiText.hiddenOther(reason)
        }
    }

    private static func hint(_ reason: String) -> String {
        switch reason {
        case reasonLoggedOut: return UiText.HINT_LOGGED_OUT
        case reasonPermission: return UiText.HINT_PERMISSION
        case reasonChannel: return UiText.HINT_CHANNEL
        case reasonOptedOut: return UiText.HINT_OPTED_OUT
        default: return ""
        }
    }
}

/// Inbox card texts for one `PushRecord`.
enum PushLabels {
    private static let shortIdMax = 12
    private static let shortIdPart = 4
    private static let shownLateMillis: Int64 = 60_000

    /// "id 7c41…e2a0" for long ids.
    static func shortId(_ id: String) -> String {
        id.count <= shortIdMax
            ? UiText.ID_PREFIX + id
            : UiText.ID_PREFIX + String(id.prefix(shortIdPart)) + UiText.ELLIPSIS + String(id.suffix(shortIdPart))
    }

    static func title(_ record: PushRecord) -> String {
        if let title = record.content?.title, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return title }
        return shortId(record.id)
    }

    static func body(_ record: PushRecord) -> String {
        if let body = record.content?.body, !body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return body }
        switch record.status {
        case .hidden: return UiText.BODY_HIDDEN
        case .held: return UiText.BODY_HELD
        case .received: return UiText.BODY_RECEIVED
        case .shown: return UiText.BODY_SHOWN_NO_TEXT
        }
    }

    /// Step chips in time order, without repeats; taps merge into one chip.
    static func chips(_ record: PushRecord, _ zone: TimeZone) -> [ToneText] {
        let tapped = record.steps.contains { $0.step == .tapped }
        let reviewed = record.steps.contains { $0.step == .reviewOpened }
        var chips: [ToneText] = []
        for entry in record.steps {
            let chip: ToneText?
            switch entry.step {
            case .received:
                chip = nil
            case .shown:
                chip = entry.atMillis - record.firstAtMillis >= shownLateMillis
                    ? ToneText(text: UiText.stepShownAt(TimeText.clock(entry.atMillis, zone)), tone: .ok)
                    : ToneText(text: UiText.STEP_SHOWN, tone: .ok)
            case .held:
                let text = Int64(entry.detail).map { UiText.stepHeldUntil(TimeText.clock($0, zone)) } ?? UiText.STEP_HELD
                chip = ToneText(text: text, tone: .wait)
            case .hidden:
                chip = ToneText(text: EventText.hiddenLabel(entry.detail), tone: .bad)
            case .tapped, .reviewOpened:
                let text = tapped && reviewed ? UiText.STEP_TAPPED_REVIEW : (tapped ? UiText.STEP_TAPPED : UiText.STEP_REVIEW)
                chip = ToneText(text: text, tone: .ok)
            }
            if let chip, !chips.contains(chip) { chips.append(chip) }
        }
        if chips.isEmpty { chips.append(ToneText(text: UiText.STEP_RECEIVED_ONLY, tone: .wait)) }
        return chips
    }
}
