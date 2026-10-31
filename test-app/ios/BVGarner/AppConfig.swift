import BVGarner
import Foundation

/// Build settings the app reads from Info.plist. The values come from `Config/Base.xcconfig`
/// and the git-ignored `client.xcconfig` (see README), so no Portal app id, team or host is
/// compiled into the SDK or this source.
enum AppConfig {
    static let defaultAppId = 10_000_001
    static let defaultBaseUrl = "https://push.prod.bazaarvoice.com"
    private static let https = "https://"

    private static func info(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || trimmed.hasPrefix("$(") ? nil : trimmed
    }

    static var appName: String { info("CFBundleDisplayName") ?? info("CFBundleName") ?? "BV Garner" }
    static var bundleId: String { Bundle.main.bundleIdentifier ?? UiText.UNKNOWN }
    static var appId: Int { info("BVAppId").flatMap { Int($0) } ?? defaultAppId }
    static var baseUrl: String { info("BVBaseUrl") ?? defaultBaseUrl }
    static var appGroup: String? { info("BVAppGroup") }

    /// Firebase is optional: without `GoogleService-Info.plist` the app runs, but gets no FCM token.
    static var hasFirebaseConfig: Bool {
        Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
    }

    /// Unit tests run inside the app; they must not start the SDK against a backend.
    static var isRunningTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }

    /// Plain HTTP (a local backend) needs the explicit test-build opt-in.
    static func options(baseUrl: String = baseUrl) -> BVPush.Options {
        BVPush.Options(
            baseUrlOverride: baseUrl,
            allowInsecureLocalhost: !baseUrl.lowercased().hasPrefix(https),
            appGroup: appGroup
        )
    }

    static func startSDK() {
        BVPush.configure(appId: appId, options: options())
    }
}
