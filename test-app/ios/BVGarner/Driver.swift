#if DEBUG
import BVGarner
import Foundation

/// Debug builds only: launch arguments that drive the real SDK against a local Garner
/// Backend, like the Android `adb shell am start --es cmd …` driver. Every command logs a
/// `BVPushDriver` line a harness can assert on (`log stream --predicate 'eventMessage CONTAINS "BVPushDriver"'`).
///
///     xcrun simctl launch booted bv.garner.test.app -cmd INIT -appId 10000001 -baseUrl http://localhost:8081
///     xcrun simctl launch booted bv.garner.test.app -cmd LINK -userId tester-001 -email tester@example.com
///     xcrun simctl launch booted bv.garner.test.app -cmd QUIET -startHour 23 -endHour 8
///     xcrun simctl launch booted bv.garner.test.app -tab INBOX -screenshots YES
enum Driver {
    static let tag = "BVPushDriver"
    private static let driverAppId = 10_000_001
    private static let driverBaseUrl = "http://localhost:8081"
    private static let driverQuietBaseUrl = "http://localhost:8082"

    private static var args: UserDefaults { .standard }

    /// The tab to open (`-tab HOME|ACCOUNT|INBOX|DETAILS`).
    static var tab: TabId? { TabId.fromName(args.string(forKey: "tab")) }

    /// CI screenshots: no permission prompt.
    static var screenshots: Bool { args.bool(forKey: "screenshots") }

    /// Runs a `-cmd` and returns true; it brings its own configure, so the app must not start the SDK.
    static func start() -> Bool {
        guard let cmd = args.string(forKey: "cmd") else { return false }
        let appId = args.object(forKey: "appId") != nil ? args.integer(forKey: "appId") : driverAppId
        let baseUrl = args.string(forKey: "baseUrl")
        switch cmd {
        case "INIT":
            initialize(appId, baseUrl ?? driverBaseUrl)
            after(8) { dump("after-INIT") }
        case "LINK":
            initialize(appId, baseUrl ?? driverBaseUrl)
            guard let userId = args.string(forKey: "userId"), let email = args.string(forKey: "email") else {
                emit("LINK-ERROR userId and email are required")
                return true
            }
            after(4) {
                do {
                    try BVPush.linkIdentity(userId: userId, email: email)
                    TesterPrefs().saveIdentity(userId: userId, email: email)
                } catch {
                    emit("LINK-ERROR \(error.localizedDescription)")
                }
                after(8) { dump("after-LINK") }
            }
        case "QUIET":
            initialize(appId, baseUrl ?? driverQuietBaseUrl)
            after(4) {
                let value = { (key: String, fallback: Int) in args.object(forKey: key) != nil ? args.integer(forKey: key) : fallback }
                try? BVPush.setQuietHours(
                    startHour: value("startHour", 23), startMinute: value("startMinute", 0),
                    endHour: value("endHour", 8), endMinute: value("endMinute", 0)
                )
                after(1) { dump("after-QUIET") }
            }
        case "DUMP":
            AppConfig.startSDK()
            after(2) { dump("manual") }
        default:
            emit("UNKNOWN-CMD \(cmd)")
            return false
        }
        return true
    }

    static func emit(_ line: String) {
        NSLog("%@ %@", tag, line)
    }

    private static func initialize(_ appId: Int, _ baseUrl: String) {
        BVPush.configure(appId: appId, options: AppConfig.options(baseUrl: baseUrl))
    }

    private static func after(_ seconds: Double, _ work: @escaping () -> Void) {
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds, execute: work)
    }

    /// The harness reads these lines; keep the format (same as Android).
    private static func dump(_ phase: String) {
        let state = BVPush.state
        let diag = BVPush.diagnostics().map { String(describing: $0) } ?? "nil"
        emit("STATE phase=\(phase) initialized=\(state.initialized) deviceId=\(state.deviceId ?? "null") " +
            "error=\(state.lastError ?? "null") diag=\(diag)")
        if let token = BVPush.fcmToken { emit("FCMTOKEN \(token)") }
    }
}
#endif
