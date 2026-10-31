import Foundation

/// What the tester typed and when, in this app's own defaults. The SDK keeps its own state;
/// these values only fill the screens.
final class TesterPrefs {
    private static let suite = "garner_test_app"
    private static let keyUserId = "userId"
    private static let keyEmail = "email"
    private static let keySignedInAt = "signedInAt"
    private static let keyInboxSeenAt = "inboxSeenAt"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = UserDefaults(suiteName: TesterPrefs.suite) ?? .standard) {
        self.defaults = defaults
    }

    var userId: String { defaults.string(forKey: Self.keyUserId) ?? "" }
    var email: String { defaults.string(forKey: Self.keyEmail) ?? "" }
    /// Epoch millis of the last successful sign in on this device; 0 when unknown.
    var signedInAt: Int64 { (defaults.object(forKey: Self.keySignedInAt) as? NSNumber)?.int64Value ?? 0 }
    /// Epoch millis of the newest push the tester has seen in the Inbox.
    var inboxSeenAt: Int64 { (defaults.object(forKey: Self.keyInboxSeenAt) as? NSNumber)?.int64Value ?? 0 }

    func saveIdentity(userId: String, email: String) {
        defaults.set(userId, forKey: Self.keyUserId)
        defaults.set(email, forKey: Self.keyEmail)
    }

    func markSignedIn(atMillis: Int64) {
        defaults.set(NSNumber(value: atMillis), forKey: Self.keySignedInAt)
    }

    /// The email stays, so the form is filled for the next sign in.
    func markSignedOut() {
        defaults.removeObject(forKey: Self.keySignedInAt)
    }

    func markInboxSeen(atMillis: Int64) {
        if atMillis > inboxSeenAt { defaults.set(NSNumber(value: atMillis), forKey: Self.keyInboxSeenAt) }
    }
}
