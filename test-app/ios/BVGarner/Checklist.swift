import Foundation

/// The Home checklist: five things a push needs on this device.
enum CheckKind: CaseIterable { case registered, pushToken, signedIn, notifications, quietHours }

enum CheckMark { case ok, bad, wait }

struct Check: Equatable {
    let kind: CheckKind
    let mark: CheckMark
}

/// Banner state above the checklist.
enum CheckSummary { case ready, checking, action }

struct HealthInput {
    let initialized: Bool
    let deviceId: String?
    let tokenSynced: Bool
    let hasSession: Bool
    let notificationsAllowed: Bool
    let quiet: QuietStatus
    /// Registration or token sync has taken longer than expected.
    let slow: Bool
}

enum Checklist {
    static func evaluate(_ input: HealthInput) -> [Check] {
        CheckKind.allCases.map { Check(kind: $0, mark: mark($0, input)) }
    }

    static func passed(_ checks: [Check]) -> Int { checks.filter { $0.mark == .ok }.count }

    static func summary(_ checks: [Check]) -> CheckSummary {
        if checks.allSatisfy({ $0.mark == .ok }) { return .ready }
        if checks.contains(where: { $0.mark == .bad }) { return .action }
        return .checking
    }

    private static func mark(_ kind: CheckKind, _ input: HealthInput) -> CheckMark {
        switch kind {
        case .registered:
            if input.deviceId != nil { return .ok }
            return input.initialized ? .bad : .wait
        case .pushToken:
            if input.tokenSynced { return .ok }
            if !input.initialized { return .wait }
            return input.deviceId == nil || input.slow ? .bad : .wait
        case .signedIn:
            if input.hasSession { return .ok }
            return input.initialized ? .bad : .wait
        case .notifications:
            return input.notificationsAllowed ? .ok : .bad
        case .quietHours:
            switch input.quiet.mode {
            case .off, .onLater: return .ok
            case .onNow: return .bad
            case .unknown: return .wait
            }
        }
    }
}
