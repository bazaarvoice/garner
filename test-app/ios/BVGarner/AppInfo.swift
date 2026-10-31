import Foundation

/// App id, endpoint and user facts shown in the top bar and Details.
enum AppInfo {
    private static let https = "https://"
    private static let schemeEnd = "://"
    private static let localHosts: Set<String> = ["localhost", "127.0.0.1", "10.0.2.2"]
    private static let qaMarkers = [".qa.", "-qa.", "-qa-"]
    private static let stagingMarkers = [".stg.", "-stg.", "staging"]
    private static let nameSeparators: Set<Character> = [".", "_", "-", "+"]
    private static let noInitials = "?"

    /// The environment the top bar and Details show (the only place name the app shows):
    /// Local for plain HTTP or a local host, then QA / Staging by host name, else Production.
    static func environment(_ baseUrl: String) -> String {
        let url = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let afterScheme = url.range(of: schemeEnd).map { String(url[$0.upperBound...]) } ?? url
        let hostPort = afterScheme.split(separator: "/", omittingEmptySubsequences: false).first.map(String.init) ?? ""
        let host = hostPort.split(separator: ":", omittingEmptySubsequences: false).first.map(String.init) ?? ""
        if !url.hasPrefix(https) || localHosts.contains(host) { return UiText.ENV_LOCAL }
        if qaMarkers.contains(where: { host.contains($0) }) { return UiText.ENV_QA }
        if stagingMarkers.contains(where: { host.contains($0) }) { return UiText.ENV_STAGING }
        return UiText.ENV_PRODUCTION
    }

    /// "tester.one@example.com" → "TO"; falls back to the user id, then "?".
    static func initials(email: String?, userId: String?) -> String {
        let local = email.map { String($0.split(separator: "@", omittingEmptySubsequences: false).first ?? "") }
        let source: String
        if let local, !local.trimmingCharacters(in: .whitespaces).isEmpty {
            source = local
        } else if let userId, !userId.trimmingCharacters(in: .whitespaces).isEmpty {
            source = userId
        } else {
            return noInitials
        }
        let parts = source.split(whereSeparator: { nameSeparators.contains($0) }).filter { !$0.isEmpty }
        let letters = parts.count >= 2 ? "\(parts[0].first!)\(parts[1].first!)" : String(source.prefix(2))
        return letters.uppercased()
    }
}

/// Time texts in the device zone (passed in, so tests are fixed).
enum TimeText {
    private static let patternClock = "HH:mm"
    private static let patternDateTime = "d MMM HH:mm"
    private static let dayMillis: Int64 = 86_400_000

    static func clock(_ millis: Int64, _ zone: TimeZone) -> String { format(patternClock, millis, zone) }

    static func dateTime(_ millis: Int64, _ zone: TimeZone) -> String { format(patternDateTime, millis, zone) }

    /// Inbox time: "10:48" today, "Yesterday 23:14", else "24 Oct 10:43".
    static func inbox(_ millis: Int64, now nowMillis: Int64, _ zone: TimeZone) -> String {
        switch day(nowMillis, zone) - day(millis, zone) {
        case 0: return clock(millis, zone)
        case 1: return UiText.YESTERDAY + " " + clock(millis, zone)
        default: return dateTime(millis, zone)
        }
    }

    /// The SDK keeps the session expiry as epoch millis text; nil or junk gives nil.
    static func epochMillis(_ raw: String?) -> Int64? {
        guard let raw, let value = Int64(raw.trimmingCharacters(in: .whitespacesAndNewlines)), value > 0 else { return nil }
        return value
    }

    static func nowMillis(_ date: Date = Date()) -> Int64 { Int64((date.timeIntervalSince1970 * 1000).rounded(.down)) }

    /// Device wall-clock minutes after midnight, for quiet hours.
    static func minutesOfDay(_ date: Date = Date(), _ zone: TimeZone = .current) -> Int {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        let parts = calendar.dateComponents([.hour, .minute], from: date)
        return (parts.hour ?? 0) * 60 + (parts.minute ?? 0)
    }

    private static func day(_ millis: Int64, _ zone: TimeZone) -> Int64 {
        let offset = Int64(zone.secondsFromGMT(for: date(millis))) * 1000
        let shifted = millis + offset
        return shifted >= 0 ? shifted / dayMillis : (shifted - dayMillis + 1) / dayMillis
    }

    private static func date(_ millis: Int64) -> Date { Date(timeIntervalSince1970: Double(millis) / 1000) }

    private static func format(_ pattern: String, _ millis: Int64, _ zone: TimeZone) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = zone
        formatter.dateFormat = pattern
        return formatter.string(from: date(millis))
    }
}
